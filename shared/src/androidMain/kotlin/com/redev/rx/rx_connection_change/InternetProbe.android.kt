package com.redev.rx.rx_connection_change

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal actual suspend fun probeUrl(url: String, timeoutMillis: Long): Boolean =
    withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "HEAD"
                connectTimeout = timeoutMillis.toInt()
                readTimeout = timeoutMillis.toInt()

                ///A captive portal answers with a 302 to its login page. Following it would land on
                ///a 200 served by the portal and read as working internet, which is the exact case
                ///this library exists to catch.
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "rx_connection_change/1.0")
                setRequestProperty("Cache-Control", "no-cache")
            }
            connection.responseCode in 200..299
        } catch (e: Exception) {
            false
        } finally {
            connection?.disconnect()
        }
    }
