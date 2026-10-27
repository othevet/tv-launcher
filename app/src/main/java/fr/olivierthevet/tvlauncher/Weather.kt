package fr.olivierthevet.tvlauncher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

data class Weather(
    /** Code WMO du temps actuel (voir describe) */
    val code: Int,
    val temperature: Int,
    val min: Int,
    val max: Int,
    val label: String,
    val symbol: String,
    val tomorrowMin: Int,
    val tomorrowMax: Int,
    val tomorrowSymbol: String,
)

/** Dernière météo reçue (null tant qu'aucun appel n'a réussi) et état du réseau. */
data class WeatherState(
    val weather: Weather? = null,
    val failed: Boolean = false,
    val online: Boolean = true,
)

// Ville réglée dans local.properties (voir app/build.gradle.kts)
private const val URL_OPEN_METEO =
    "https://api.open-meteo.com/v1/forecast?latitude=${BuildConfig.WEATHER_LATITUDE}" +
        "&longitude=${BuildConfig.WEATHER_LONGITUDE}" +
        "&current=temperature_2m,weather_code&daily=weather_code,temperature_2m_max,temperature_2m_min" +
        "&timezone=Europe%2FParis&forecast_days=2"

@Composable
fun rememberWeather(): WeatherState {
    val online = rememberOnline()
    var state by remember { mutableStateOf(WeatherState()) }
    // Relancé à chaque changement de réseau : au démarrage de la TV, la météo arrive dès
    // que le Wi-Fi est connecté. En cas d'échec (réseau ou heure, nécessaire au HTTPS,
    // pas encore prêts), on réessaie vite au lieu d'attendre 15 min.
    LaunchedEffect(online) {
        while (true) {
            val result = fetchWeather()
            state = WeatherState(result ?: state.weather, failed = result == null, online = online)
            delay(if (result != null) 15 * 60 * 1_000L else 30 * 1_000L)
        }
    }
    return state
}

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
        val min = daily.getJSONArray("temperature_2m_min")
        val max = daily.getJSONArray("temperature_2m_max")
        val code = current.getInt("weather_code")
        val (label, symbol) = describe(code)
        Weather(
            code = code,
            temperature = current.getDouble("temperature_2m").roundToInt(),
            min = min.getDouble(0).roundToInt(),
            max = max.getDouble(0).roundToInt(),
            label = label,
            symbol = symbol,
            tomorrowMin = min.getDouble(1).roundToInt(),
            tomorrowMax = max.getDouble(1).roundToInt(),
            tomorrowSymbol = describe(daily.getJSONArray("weather_code").getInt(1)).second,
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
