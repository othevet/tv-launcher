package fr.olivierthevet.tvlauncher

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Vrai quand le réseau par défaut passe par un VPN (Surfshark ou autre).
 * Android ne dit pas vers quel pays : seulement si un tunnel est actif.
 */
@Composable
fun rememberVpnConnected(): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var connected by remember {
        mutableStateOf(cm.getNetworkCapabilities(cm.activeNetwork)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true)
    }

    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                connected = caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
            }

            override fun onLost(network: Network) {
                connected = false
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return connected
}
