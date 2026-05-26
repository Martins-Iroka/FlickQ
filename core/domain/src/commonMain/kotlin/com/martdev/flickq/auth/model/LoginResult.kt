package com.martdev.flickq.auth.model

data class LoginResult(
    val userId: Long,
    val accessToken: String,
    val refreshToken: String
)
