package com.martdev.flickq.core.data

/**
 * Base URL for the FlickQ backend. Only used once real (Ktor) data sources replace
 * the fakes. Android emulator should target 10.0.2.2 instead of localhost.
 */
const val BASE_URL = "http://localhost:8080/api/v1"
