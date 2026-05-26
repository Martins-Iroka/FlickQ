package com.martdev.flickq.auth.model

data class UserData(
    val id: Long = 0,
    val email: String = "",
    val password: String = "",
    val isVerified: Boolean = false,
    val role: Role = Role.USER,
)
