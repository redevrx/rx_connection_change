package com.redev.rx.rx_connection_change

/**
 * The one-shot counterpart to [ConnectivityObserver]'s stream, and the public entry point to the
 * shared probe.
 *
 * It exists so the Flutter/Dart layer can stop carrying a probe of its own. Before this, "online"
 * had three separate definitions in the stack — Android's, iOS's, and Dart's `NetworkMonitor` —
 * which is what the ticket's "centralized handler" asks to collapse. Everything here reads
 * [ConnectivityConfig], so configuring the URLs once configures every caller.
 */
object InternetChecker {

    /**
     * `true` only when a configured endpoint answered 2xx without redirecting.
     *
     * Bounded by [ConnectivityConfig.probeTimeoutMillis] × [ConnectivityConfig.probeRetries]
     * plus the delays between passes, independent of how many URLs are configured.
     */
    suspend fun hasInternet(): Boolean = hasUsableInternet()
}
