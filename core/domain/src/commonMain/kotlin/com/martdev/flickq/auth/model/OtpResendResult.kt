package com.martdev.flickq.auth.model

data class OtpResendResult(
    val emailId: String,
    val verificationToken: String
)
