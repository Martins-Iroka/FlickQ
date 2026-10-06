package com.martdev.flickq.features.notification.domain.service

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FcmErrorClassifierTest {
    private fun err(code: String, status: Int? = null) = FcmError(code, status, null, null)

    private val cases = listOf(
        Case("UNREGISTERED", 404, true, FcmErrorClass.INVALID_TOKEN),
        Case("SENDER_ID_MISMATCH", 403, true, FcmErrorClass.INVALID_TOKEN),
        Case("TOO_MANY_TOPICS", null, true, FcmErrorClass.INVALID_TOKEN),
        Case("INVALID_ARGUMENT", 400, true, FcmErrorClass.INVALID_TOKEN),
        Case("INVALID_ARGUMENT", 400, false, FcmErrorClass.FATAL),
        Case("QUOTA_EXCEEDED", 429, true, FcmErrorClass.TRANSIENT),
        Case("UNAVAILABLE", 503, true, FcmErrorClass.TRANSIENT),
        Case("INTERNAL", 500, true, FcmErrorClass.TRANSIENT),
        Case("IO", null, true, FcmErrorClass.TRANSIENT),
        Case("THIRD_PARTY_AUTH_ERROR", 401, true, FcmErrorClass.CONFIG),
        Case("UNAUTHENTICATED", 40, false, FcmErrorClass.FATAL),
        Case("PERMISSION_DENIED", 403, false, FcmErrorClass.FATAL),
        Case("SOMETHING_NEW", 503, true, FcmErrorClass.TRANSIENT),
        Case("SOMETHING_NEW", null, true, FcmErrorClass.TRANSIENT)
    )

    @Test
    fun `classifies every known error`() = cases.forEach { c ->
        assertEquals(c.expected,
            FcmErrorClassifier.classify(err(c.code, c.status), c.targetsToken),
            "${c.code}/${c.status}/${c.targetsToken}"
        )
    }

    private data class Case(
        val code: String,
        val status: Int?,
        val targetsToken: Boolean,
        val expected: FcmErrorClass
    )
}