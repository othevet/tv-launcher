package fr.olivierthevet.tvlauncher

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class Weather(
    val temperature: Int,
    val min: Int,
    val max: Int,
    val label: String,
    val symbol: String,
)

// Ville réglée dans local.properties (voir app/build.gradle.kts)
private const val URL_OPEN_METEO =
    "https://api.open-meteo.com/v1/forecast?latitude=${BuildConfig.WEATHER_LATITUDE}" +
        "&longitude=${BuildConfig.WEATHER_LONGITUDE}" +
        "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min" +
        "&timezone=Europe%2FParis&forecast_days=1"

suspend fun fetchWeather(): Weather? = withContext(Dispatchers.IO) {
    runCatching {
        val connection = URL(URL_OPEN_METEO).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 10_000
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val json = JSONObject(body)
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        val (label, symbol) = describe(current.getInt("weather_code"))
        Weather(
            temperature = current.getDouble("temperature_2m").roundToInt(),
            min = daily.getJSONArray("temperature_2m_min").getDouble(0).roundToInt(),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0).roundToInt(),
            label = label,
            symbol = symbol,
        )
    }.getOrNull()
}

// Codes WMO utilisés par Open-Meteo
private fun describe(code: Int): Pair<String, String> = when (code) {
    0 -> "Ciel dégagé" to "☀️"
    1, 2 -> "Éclaircies" to "🌤️"
    3 -> "Couvert" to "☁️"
    45, 48 -> "Brouillard" to "🌫️"
    in 51..57 -> "Bruine" to "🌦️"
    in 61..67, in 80..82 -> "Pluie" to "🌧️"
    in 71..77, 85, 86 -> "Neige" to "🌨️"
    in 95..99 -> "Orage" to "⛈️"
    else -> "—" to "🌡️"
}
