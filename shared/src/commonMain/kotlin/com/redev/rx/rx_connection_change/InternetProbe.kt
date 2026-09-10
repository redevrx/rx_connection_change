package com.redev.rx.rx_connection_change

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One HEAD request against [url], platform HTTP client.
 *
 * Must return `true` only for a 2xx answered by the host that was asked. A redirect is a
 * `false` on purpose: a captive portal announces itself by 302-ing to its login page, and a
 * followed redirect would otherwise land on a 200 and read as working internet.
 */
internal expect suspend fun probeUrl(url: String, timeoutMillis: Long): Boolean

/**
 * The single definition of "the internet is usable", shared by both platforms.
 */
internal suspend fun hasUsableInternet(): Boolean {
    val urls = ConnectivityConfig.probeUrls
    if (urls.isEmpty()) return false

    val passes = ConnectivityConfig.probeRetries.coerceAtLeast(1)
    repeat(passes) { pass ->
        if (probeAny(urls, ConnectivityConfig.probeTimeoutMillis)) return true
        if (pass < passes - 1) delay(ConnectivityConfig.probeRetryDelayMillis)
    }
    return false
}

/**
 * Races every URL and answers on the first success.
 *
 * Probing serially made the worst case `urls.size * timeout`, so every extra endpoint added to
 * the list — the very thing that protects against one blocked vendor — made a genuinely offline
 * verdict slower to reach. Racing them costs one timeout no matter how many URLs there are.
 */
private suspend fun probeAny(urls: List<String>, timeoutMillis: Long): Boolean = coroutineScope {
    val firstSuccess = CompletableDeferred<Boolean>()

    val probes = urls.map { url ->
        launch {
            if (probeUrl(url, timeoutMillis)) firstSuccess.complete(true)
        }
    }

    ///Settles the race when every probe has failed. Without it `await` would hang on a network
    ///where nothing answers — which is precisely the network this library is for.
    val watcher = launch {
        probes.forEach { it.join() }
        firstSuccess.complete(false)
    }

    val reachable = firstSuccess.await()

    ///The losing probes hold sockets open for the rest of their timeout otherwise, and on a dead
    ///network the next pass would start while the previous one is still running.
    probes.forEach { it.cancel() }
    watcher.cancel()

    reachable
}
