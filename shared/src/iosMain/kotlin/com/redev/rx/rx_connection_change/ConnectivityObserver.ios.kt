package com.redev.rx.rx_connection_change

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPMethod
import platform.Network.*
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ConnectivityObserver {
    private var monitor:nw_path_monitor_t? = null
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

    private fun setupMonitor(onStatusChanged: (Boolean) -> Unit) {
        stop()
        monitor = nw_path_monitor_create()
        queue = dispatch_queue_create("NetworkMonitorQueue", null)

        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = nw_path_get_status(path)
            val basicConnected = status == nw_path_status_satisfied

            if(basicConnected){
                pingInternet { hasInternet ->
                    onStatusChanged(hasInternet && basicConnected)
                }
            }else {
                onStatusChanged(basicConnected)
            }
        }

        nw_path_monitor_set_queue(monitor, queue)
        nw_path_monitor_start(monitor)
    }

    fun stop(){
        monitor?.let {
            nw_path_monitor_cancel(it)
        }
        monitor = null
        queue = null
    }

    fun internetChange(callback:(Boolean)->Unit){
        setupMonitor(callback)
    }

    private fun pingInternet(onResult: (Boolean) -> Unit) {
        val url = NSURL.URLWithString("https://apple.com")
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
}