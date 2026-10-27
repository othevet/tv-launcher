package fr.olivierthevet.tvlauncher

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import kotlinx.coroutines.delay
import java.time.LocalTime

/** Fond de l'accueil : dégradé selon le moment de la journée, grisé par mauvais temps. */
@Composable
fun Wallpaper(weather: Weather?, bottom: Color) {
    // Relu chaque minute, mais ne recompose que quand l'heure change.
    var hour by remember { mutableIntStateOf(LocalTime.now().hour) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            hour = LocalTime.now().hour
        }
    }
    val top by animateColorAsState(skyColor(hour, weather?.code), tween(3_000), label = "sky")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom))))
}

private fun skyColor(hour: Int, weatherCode: Int?): Color {
    val sky = when (hour) {
        in 6..9 -> Color(0xFF1E3A5F)    // matin
        in 10..16 -> Color(0xFF1F4E79)  // journée
        in 17..21 -> Color(0xFF4A2C4F)  // soir
        else -> Color(0xFF0F1A2E)       // nuit
    }
    val greyed = when (weatherCode) {
        null, 0, 1, 2 -> 0f
        in 95..99 -> 0.7f  // orage
        else -> 0.45f      // couvert, brouillard, pluie, neige
    }
    return lerp(sky, Color(0xFF2A3038), greyed)
}
