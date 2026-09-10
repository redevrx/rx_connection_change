package com.redev.rx.rx_connection_change

import com.redev.rx.rx_connection_change.flowUtils.FlowX
import com.redev.rx.rx_connection_change.flowUtils.toFlowX
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
import platform.Network.nw_path_get_status
import platform.Network.nw_path_monitor_cancel
import platform.Network.nw_path_monitor_create
import platform.Network.nw_path_monitor_set_queue
import platform.Network.nw_path_monitor_set_update_handler
import platform.Network.nw_path_monitor_start
import platform.Network.nw_path_monitor_t
import platform.Network.nw_path_status_satisfied
import platform.darwin.dispatch_queue_create
import platform.darwin.dispatch_queue_t

@Suppress(names = ["EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING"])
actual class ConnectivityObserver {
    private var monitor: nw_path_monitor_t = null
    private var queue: dispatch_queue_t = null
    private var scope: CoroutineScope? = null
    private var probeJob: Job? = null

    actual val isConnected: Flow<Boolean>
        get() = callbackFlow {
            val flowScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
            scope = flowScope

            ///`nw_path` reports that a route exists, nothing more. The probe is what separates
            ///"joined a Wi-Fi" from "can reach anything", which is the case this library exists for.
            fun verdict(transportUp: Boolean) {
                probeJob?.cancel()
                if (!transportUp) {
                    trySend(false)
                    return
                }
                probeJob = flowScope.launch { trySend(hasUsableInternet()) }
            }

            val pathMonitor = nw_path_monitor_create()
            monitor = pathMonitor
            val monitorQueue = dispatch_queue_create("rx_connection_change.monitor", null)
            queue = monitorQueue

            ///The handler fires once with the current path as soon as the monitor starts, so the
            ///initial verdict comes from the same path this one code path produces. The old
            ///`checkInitialConnectivity` ran a second, separately-configured probe to get it.
            nw_path_monitor_set_update_handler(pathMonitor) { path ->
                verdict(nw_path_get_status(path) == nw_path_status_satisfied)
            }
            nw_path_monitor_set_queue(pathMonitor, monitorQueue)
            nw_path_monitor_start(pathMonitor)

            awaitClose { cleanup() }
        }.distinctUntilChanged()

    val onConnected: FlowX<Boolean> get() = isConnected.toFlowX()

    fun stop() = cleanup()

    private fun cleanup() {
        probeJob?.cancel()
        probeJob = null

        scope?.cancel()
        scope = null

        monitor?.let { nw_path_monitor_cancel(it) }
        monitor = null
        queue = null
    }
}
