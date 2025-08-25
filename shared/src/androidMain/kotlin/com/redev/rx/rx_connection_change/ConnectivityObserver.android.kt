package com.redev.rx.rx_connection_change

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ConnectivityObserver(
    context: Context,
) {
    private val connectivityManager: ConnectivityManager =
        context.getSystemService(ConnectivityManager::class.java)

    actual val isConnected: Flow<Boolean>
        @SuppressLint("MissingPermission")
        get() = callbackFlow {
            val initialState = getCurrentConnectivityState()
            trySend(initialState)

            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                    super.onCapabilitiesChanged(network, networkCapabilities)
                    val isConnected = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    trySend(isConnected)
                }

                override fun onLosing(network: Network, maxMsToLive: Int) {
                    super.onLosing(network, maxMsToLive)
                    trySend(false)
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    trySend(false)
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    trySend(false)
                }

                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    val isValidated = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                    trySend(isValidated)
                }
            }

            connectivityManager.registerDefaultNetworkCallback(callback)

            awaitClose {
                connectivityManager.unregisterNetworkCallback(callback)
            }
        }



    @SuppressLint("MissingPermission")
    private fun getCurrentConnectivityState(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
            val networkCapabilities = activeNetwork?.let {
                connectivityManager.getNetworkCapabilities(it)
            }
            networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        } catch (e: Exception) {
            false
        }
    }
}