package com.redev.rx.rx_connection_change

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.*
import platform.darwin.NSObject
import kotlin.coroutines.resume

/**
 * Refuses every redirect, so a captive portal's 302 stays the final response instead of being
 * followed to a 200 on the portal's own login page.
 */
private class NoRedirectDelegate : NSObject(), NSURLSessionTaskDelegateProtocol {
    override fun URLSession(
        session: NSURLSession,
        task: NSURLSessionTask,
        willPerformHTTPRedirection: NSHTTPURLResponse,
        newRequest: NSURLRequest,
        completionHandler: (NSURLRequest?) -> Unit
    ) {
        completionHandler(null)
    }
}

internal actual suspend fun probeUrl(url: String, timeoutMillis: Long): Boolean {
    val nsUrl = NSURL.URLWithString(url) ?: return false

    val request = NSMutableURLRequest.requestWithURL(nsUrl).apply {
        setHTTPMethod("HEAD")
        setTimeoutInterval(timeoutMillis / 1000.0)
        setCachePolicy(NSURLRequestReloadIgnoringLocalAndRemoteCacheData)
        setValue("rx_connection_change/1.0", forHTTPHeaderField = "User-Agent")
        setValue("no-cache", forHTTPHeaderField = "Cache-Control")
    }

    val configuration = NSURLSessionConfiguration.ephemeralSessionConfiguration.apply {
        timeoutIntervalForRequest = timeoutMillis / 1000.0
    }
    val session = NSURLSession.sessionWithConfiguration(
        configuration,
        NoRedirectDelegate(),
        null
    )

    ///`setTimeoutInterval` covers a stalled response, not a socket that never opens on some
    ///network conditions, so the coroutine carries its own bound as well. Whichever fires first
    ///produces the same verdict.
    return try {
        withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine { continuation ->
                val task = session.dataTaskWithRequest(request) { _, response, error ->
                    val code = (response as? NSHTTPURLResponse)?.statusCode ?: -1L
                    val reachable = error == null && code in 200..299
                    if (continuation.isActive) continuation.resume(reachable)
                }
                continuation.invokeOnCancellation { task.cancel() }
                task.resume()
            }
        } ?: false
    } finally {
        session.finishTasksAndInvalidate()
    }
}
