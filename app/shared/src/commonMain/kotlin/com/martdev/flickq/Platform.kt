package com.martdev.flickq

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform