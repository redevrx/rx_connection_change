package com.redev.rx.rx_connection_change

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Network.*
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ConnectivityObserver {
    private var monitor:nw_path_monitor_t? = null
    private var queue: dispatch_queue_t? = null
    private var callback: ((Boolean) -> Unit)? = null

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
        if (monitor != null) return

        monitor = nw_path_monitor_create()
        queue = dispatch_queue_create("NetworkMonitorQueue", null)

        var emitted = false
        nw_path_monitor_set_update_handler(monitor) { path ->
            val status = nw_path_get_status(path)
            val connected = status == nw_path_status_satisfied

            if (!emitted) {
                emitted = true
                onStatusChanged(connected)
            } else {
                onStatusChanged(connected)
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
        this.callback = callback
        setupMonitor(callback)
    }
}