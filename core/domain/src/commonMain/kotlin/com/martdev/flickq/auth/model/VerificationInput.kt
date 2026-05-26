package com.martdev.flickq.auth.model

data class VerificationInput(
    val code: String,
    val emailId: String,
    val registrationToken: String
)
