package com.martdev.flickq.features.payment.infrastructure.paystack

import org.junit.jupiter.api.Test
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PaystackSignatureVerifierTest {

    private val secret = "sk_test_super_secret"
    private val body = """{"event":"charge.success","data":{"reference":"ref_1"}}"""

    @Test
    fun `accepts matching signature`() {
        val sig = hmacSha512(body, secret)
        assertTrue(PaystackSignatureVerifier.isValid(body, sig, secret))
    }

    @Test
    fun `rejects mismatched signature`() {
        assertFalse(PaystackSignatureVerifier.isValid(body, "deadbeef", secret))
    }

    @Test
    fun `rejects null or blank signature`() {
        assertFalse(PaystackSignatureVerifier.isValid(body, null, secret))
        assertFalse(PaystackSignatureVerifier.isValid(body, "   ", secret))
    }

    @Test
    fun `rejects signature on tampered body`() {
        val sig = hmacSha512(body, secret)
        val tampered = body.replace("ref_1", "ref_2")
        assertFalse(PaystackSignatureVerifier.isValid(tampered, sig, secret))
    }

    private fun hmacSha512(payload: String, secret: String): String {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), "HmacSHA512"))
        val digest = mac.doFinal(payload.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
