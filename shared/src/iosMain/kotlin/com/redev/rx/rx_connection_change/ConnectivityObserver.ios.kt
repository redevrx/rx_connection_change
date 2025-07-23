package com.redev.rx.rx_connection_change

import com.redev.rx.rx_connection_change.flowUtils.FlowX
import com.redev.rx.rx_connection_change.flowUtils.toFlowX
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.*
import platform.Network.*
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ConnectivityObserver {
    private var monitor: nw_path_monitor_t? = null
    private var queue: dispatch_queue_t? = null

    actual val isConnected: Flow<Boolean>
        get() = callbackFlow {
            setupMonitor { isConnected ->
                trySend(isConnected)
            }

            awaitClose {
                stop()
            }
        }

    val onConnected: FlowX<Boolean> get() = isConnected.toFlowX()

    private fun setupMonitor(onStatusChanged: (Boolean) -> Unit) {
        monitor = nw_path_monitor_create()
        queue = dispatch_queue_create("NetworkMonitorQueue", null)

        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = nw_path_get_status(path)
            val basicConnected = status == nw_path_status_satisfied
            onStatusChanged(basicConnected)

            retryPingInternet { hasInternet ->
                onStatusChanged(hasInternet)
            }
        }

        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }

    fun stop() {
        monitor?.let {
            nw_path_monitor_cancel(it)
        }
        monitor = null
        queue = null
    }

    fun internetChange(callback: (Boolean) -> Unit) {
        setupMonitor(callback)
    }

    private fun pingInternet(onResult: (Boolean) -> Unit) {
        val url = NSURL.URLWithString("https://clients3.google.com/generate_204")
        val request = NSMutableURLRequest.requestWithURL(url!!)
        request.setHTTPMethod("HEAD")

        val session = NSURLSession.sharedSession

        val task = session.dataTaskWithRequest(request) { _, response, error ->
            val reachable = ((response as? NSHTTPURLResponse)?.statusCode ?: 500) in
                    200..399 && error == null
            onResult(reachable)
        }

        task.resume()
    }

    private val coroutineScope = CoroutineScope(Dispatchers.Default)
    private var retryJob: Job? = null
    private fun retryPingInternet(
        attempts: Int = 4,
        delaySeconds: Long = 2,
        onResult: (Boolean) -> Unit
    ) {
        retryJob?.cancel()
        retryJob = coroutineScope.launch {
            repeat(attempts) { attemptIndex ->
                val reachable = suspendCancellableCoroutine { cont ->
                    pingInternet { result ->
                        if (cont.isActive) cont.resume(result) { cause, _, _ -> }
                    }
                }

                if (reachable) {
                    onResult(true)
                    return@launch
                }
                if (attempts == 4 && !reachable) {
                    onResult(false)
                }

                if (attemptIndex < attempts - 1) {
                    delay(delaySeconds * 1000)
                }
            }

            onResult(false)
        }
    }


}