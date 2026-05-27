package com.martdev.flickq.features.payment.infrastructure.paystack

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object PaystackSignatureVerifier {

    private const val ALGORITHM = "HmacSHA512"

    /**
     * Verifies a Paystack webhook signature.
     *
     * Paystack signs the raw request body with HMAC-SHA512 using the merchant's secret key
     * and places the lowercase hex digest in the `x-paystack-signature` header.
     */
    fun isValid(rawBody: String, signature: String?, secret: String): Boolean {
        if (signature.isNullOrBlank()) return false
        val expected = compute(rawBody, secret)
        return constantTimeEquals(expected, signature.lowercase())
    }

    private fun compute(payload: String, secret: String): String {
        val mac = Mac.getInstance(ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), ALGORITHM))
        val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}
