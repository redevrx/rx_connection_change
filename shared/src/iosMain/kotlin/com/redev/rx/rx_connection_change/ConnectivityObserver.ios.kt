package com.redev.rx.rx_connection_change

import com.redev.rx.rx_connection_change.flowUtils.FlowX
import com.redev.rx.rx_connection_change.flowUtils.toFlowX
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.*
import platform.Network.*
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t
import kotlin.coroutines.resume

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ConnectivityObserver {
    private var monitor: nw_path_monitor_t? = null
    private var queue: dispatch_queue_t? = null
    private var coroutineScope: CoroutineScope? = null
    private var retryJob: Job? = null
    private var lastKnownState: Boolean? = null

    actual val isConnected: Flow<Boolean>
        get() = callbackFlow {
            coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())


            checkInitialConnectivity { initialState ->
                lastKnownState = initialState
                trySend(initialState)
            }

            setupMonitor { isConnected ->
                if (lastKnownState != isConnected) {
                    lastKnownState = isConnected
                    trySend(isConnected)
                }
            }

            awaitClose {
                cleanup()
            }
        }.distinctUntilChanged()

    val onConnected: FlowX<Boolean> get() = isConnected.toFlowX()

    private fun checkInitialConnectivity(onResult: (Boolean) -> Unit) {
        coroutineScope?.launch {
            try {
                val hasInternet = withTimeoutOrNull(3000) {
                    suspendCancellableCoroutine<Boolean> { cont ->
                        pingInternet { result ->
                            if (cont.isActive) {
                                cont.resume(result)
                            }
                        }
                    }
                } ?: false
                onResult(hasInternet)
            } catch (e: Exception) {
                onResult(false)
            }
        }
    }

    private fun setupMonitor(onStatusChanged: (Boolean) -> Unit) {
        monitor = nw_path_monitor_create()
        queue = dispatch_queue_create("NetworkMonitorQueue", null)

        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = nw_path_get_status(path)
            val basicConnected = status == nw_path_status_satisfied

            if (!basicConnected) {
                onStatusChanged(false)
                return@nw_path_monitor_set_update_handler
            }

            retryPingInternet { hasInternet ->
                onStatusChanged(hasInternet)
            }
        }

        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }

    fun stop() {
        cleanup()
    }

    private fun cleanup() {
        retryJob?.cancel()
        retryJob = null

        coroutineScope?.cancel()
        coroutineScope = null

        monitor?.let {
            nw_path_monitor_cancel(it)
        }
        monitor = null
        queue = null
        lastKnownState = null
    }

    fun internetChange(callback: (Boolean) -> Unit) {
        setupMonitor(callback)
    }

    private fun pingInternet(onResult: (Boolean) -> Unit) {
        val url = NSURL.URLWithString("https://clients3.google.com/generate_204")
        val request = NSMutableURLRequest.requestWithURL(url!!)
        request.setHTTPMethod("HEAD")
        request.setTimeoutInterval(5.0)

        val session = NSURLSession.sharedSession

        val task = session.dataTaskWithRequest(request) { _, response, error ->
            val reachable = ((response as? NSHTTPURLResponse)?.statusCode ?: 500) in
                    200..399 && error == null
            onResult(reachable)
        }

        task.resume()
    }

    private fun retryPingInternet(
        attempts: Int = 3,
        delaySeconds: Long = 1,
        onResult: (Boolean) -> Unit
    ) {
        retryJob?.cancel()
        retryJob = coroutineScope?.launch {
            repeat(attempts) { attemptIndex ->
                try {
                    val reachable = withTimeoutOrNull(6000) {
                        suspendCancellableCoroutine<Boolean> { cont ->
                            pingInternet { result ->
                                if (cont.isActive) {
                                    cont.resume(result)
                                }
                            }
                        }
                    } ?: false

                    if (reachable) {
                        onResult(true)
                        return@launch
                    }


                    if (attemptIndex < attempts - 1) {
                        delay(delaySeconds * 1000)
                    }
                } catch (e: Exception) {
                    if (attemptIndex == attempts - 1) {
                        onResult(false)
                        return@launch
                    }
                }
            }


            onResult(false)
        }
    }
}