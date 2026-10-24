package fr.olivierthevet.tvlauncher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableIntStateOf

class MainActivity : ComponentActivity() {

    // Incrémenté à chaque retour sur l'accueil pour recharger la liste d'apps
    // (installation/désinstallation pendant qu'on était dans une autre app).
    private val resumeCount = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeScreen(refreshKey = resumeCount.intValue)
        }
    }

    override fun onResume() {
        super.onResume()
        resumeCount.intValue++
    }
}
