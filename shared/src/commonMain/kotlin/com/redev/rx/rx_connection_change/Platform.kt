package com.redev.rx.rx_connection_change

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform