package com.redev.rx.rx_connection_change

/**
 * Shared configuration for the reachability probe.
 *
 * Both platforms read this object, so "connected" means the same thing on Android and iOS
 * instead of each actual deciding for itself.
 *
 * The setter names mirror the Dart `NetworkMonitor` API (`setHost` / `addHost` / `clearHosts`)
 * so the Flutter binding is a straight pass-through.
 */
object ConnectivityConfig {

    /**
     * Used only while no custom URL has been set.
     *
     * Three vendors on purpose. Each is an OS captive-portal detection endpoint, so each is
     * cheap, stable and answers a tiny fixed response — but each is also blockable, and a
     * single-vendor default turns "this network blocks Google" into "there is no internet".
     * The list is probed in parallel, so three entries cost the same wall clock as one.
     */
    private val defaultProbeUrls = listOf(
        "https://clients3.google.com/generate_204",
        "https://captive.apple.com/hotspot-detect.html",
        "https://detectportal.firefox.com/success.txt",
    )

    private val customProbeUrls = mutableListOf<String>()

    val probeUrls: List<String>
        get() = customProbeUrls.ifEmpty { defaultProbeUrls }

    /** Per-request budget. One attempt against one URL may not exceed this. */
    var probeTimeoutMillis: Long = 3_000

    /**
     * How many times the whole URL list is probed before the verdict is `false`.
     *
     * With parallel probing the worst case is `probeRetries * probeTimeoutMillis` plus the delays
     * between passes — 7s at the defaults — regardless of how many URLs are configured. A verdict
     * is on the startup path, so this is a latency budget, not just a robustness knob.
     */
    var probeRetries: Int = 2

    /** Waited between passes over the URL list, not between individual URLs. */
    var probeRetryDelayMillis: Long = 1_000

    /** Replaces the probe list. Blank entries are dropped; an all-blank list falls back to the default. */
    fun setProbeUrls(urls: List<String>) {
        customProbeUrls.clear()
        customProbeUrls.addAll(urls.filter { it.isNotBlank() })
    }

    fun addProbeUrl(url: String) {
        if (url.isNotBlank() && url !in customProbeUrls) customProbeUrls.add(url)
    }

    fun clearProbeUrls() {
        customProbeUrls.clear()
    }
}
