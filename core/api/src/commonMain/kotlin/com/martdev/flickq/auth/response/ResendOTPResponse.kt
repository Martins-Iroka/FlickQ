package com.martdev.flickq.auth.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ResendOTPResponse(
    @SerialName("email_id")
    val emailId: String,
    @SerialName("verification_token")
    val verificationToken: String
)
