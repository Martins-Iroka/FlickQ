package com.martdev.flickq.config

import io.ktor.server.application.*

data class PaystackConfig(
    val secretKey: String = "",
    val publicKey: String = "",
    val baseUrl: String = "https://api.paystack.co",
    val callbackUrl: String = "",
    val webhookSecret: String = "",
    val currency: String = "NGN",
) {
    companion object {
        fun fromEnvironment(environment: ApplicationEnvironment): PaystackConfig {
            return PaystackConfig(
                secretKey = environment.getEnvValue("paystack.secretKey"),
                publicKey = environment.getEnvValue("paystack.publicKey"),
                baseUrl = environment.getEnvValue("paystack.baseUrl"),
                callbackUrl = environment.getEnvValue("paystack.callbackUrl"),
                webhookSecret = environment.getEnvValue("paystack.webhookSecret"),
                currency = environment.getEnvValue("paystack.currency"),
            )
        }
    }
}
