package com.redev.rx.rx_connection_change

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ConnectivityObserver(
    context: Context,
) {
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    actual val isConnected: Flow<Boolean>
        @SuppressLint("MissingPermission")
        get() = callbackFlow {
            val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            var probeJob: Job? = null

            ///The OS tells us a transport exists; only [hasUsableInternet] decides whether it
            ///carries anything. `NET_CAPABILITY_VALIDATED` used to be the whole verdict here, which
            ///is what let a Wi-Fi with a dead WAN or an unauthenticated captive portal report as
            ///online — the flag is the OS's own cached opinion, and it is exactly what a portal is
            ///built to satisfy.
            fun verdict(transportUp: Boolean) {
                probeJob?.cancel()
                if (!transportUp) {
                    trySend(false)
                    return
                }
                probeJob = scope.launch { trySend(hasUsableInternet()) }
            }

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    verdict(
                        networkCapabilities.hasCapability(
                            NetworkCapabilities.NET_CAPABILITY_INTERNET
                        )
                    )
                }

                override fun onAvailable(network: Network) = verdict(hasTransport(network))

                override fun onLost(network: Network) = verdict(false)

                override fun onUnavailable() = verdict(false)

                ///`onLosing` is deliberately not overridden. It fires while the network is still
                ///usable and merely expected to go away, and answering it with `false` announced an
                ///outage that had not happened — the flapping in TC 11 and TC 15. The last verdict
                ///stands until `onLost` or a probe says otherwise.
            }

            connectivityManager.registerDefaultNetworkCallback(callback)
            verdict(hasTransport(connectivityManager.activeNetwork))

            awaitClose {
                probeJob?.cancel()
                scope.cancel()
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }.distinctUntilChanged()

    @SuppressLint("MissingPermission")
    private fun hasTransport(network: Network?): Boolean = try {
        network?.let {
            connectivityManager.getNetworkCapabilities(it)
                ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } == true
    } catch (e: Exception) {
        false
    }
}
