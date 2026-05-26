package com.martdev.flickq.features.auth.domain.security

interface Auth {
    fun generateAccessToken(userID: String, role: String): String
    fun generateRefreshToken(): String
}