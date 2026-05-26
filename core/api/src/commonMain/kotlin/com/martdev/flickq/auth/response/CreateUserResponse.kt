package com.martdev.flickq.auth.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateUserResponse(
    @SerialName("email_id")
    val emailId: String,
    val token: String
)
