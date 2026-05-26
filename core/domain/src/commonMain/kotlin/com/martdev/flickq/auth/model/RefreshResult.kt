package com.martdev.flickq.auth.model

data class RefreshResult(
    val accessToken: String,
    val refreshToken: String
)
