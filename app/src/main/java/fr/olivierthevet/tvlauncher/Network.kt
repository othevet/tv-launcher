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
fun rememberVpnConnected(): Boolean =
    rememberDefaultNetwork { it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) }

/** Vrai quand le réseau par défaut a accès à Internet (vérifié par Android). */
@Composable
fun rememberOnline(): Boolean =
    rememberDefaultNetwork { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }

/** Suit le réseau par défaut et renvoie `test` appliqué à ses capacités (faux sans réseau). */
@Composable
private fun rememberDefaultNetwork(test: (NetworkCapabilities) -> Boolean): Boolean {
    val context = LocalContext.current
    val cm = remember { context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager }
    var value by remember {
        mutableStateOf(cm.getNetworkCapabilities(cm.activeNetwork)?.let(test) == true)
    }

    DisposableEffect(cm) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                value = test(caps)
            }

            override fun onLost(network: Network) {
                value = false
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return value
}
