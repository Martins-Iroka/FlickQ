package com.martdev.flickq.core.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test

class JwtDecoderTest {

    @OptIn(ExperimentalEncodingApi::class)
    private fun tokenWith(payloadJson: String): String {
        val b64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
        val header = b64.encode("""{"alg":"HS256","typ":"JWT"}""".encodeToByteArray())
        val payload = b64.encode(payloadJson.encodeToByteArray())
        return "$header.$payload.signature-not-verified"
    }

    @Test
    fun decodes_role_and_userId_for_admin() {
        val claims = JwtDecoder.decode(tokenWith("""{"userId":"42","role":"ADMIN"}"""))
        assertThat(claims?.userId).isEqualTo("42")
        assertThat(claims?.role).isEqualTo("ADMIN")
        assertThat(claims?.isAdmin ?: false).isTrue()
    }

    @Test
    fun user_role_is_not_admin() {
        val claims = JwtDecoder.decode(tokenWith("""{"userId":"7","role":"USER"}"""))
        assertThat(claims?.isAdmin ?: true).isFalse()
    }

    @Test
    fun tolerates_missing_claims_and_extra_fields() {
        val claims = JwtDecoder.decode(tokenWith("""{"sub":"x","iat":123}"""))
        assertThat(claims?.userId).isNull()
        assertThat(claims?.role).isNull()
    }

    @Test
    fun returns_null_for_blank_or_malformed_tokens() {
        assertThat(JwtDecoder.decode(null)).isNull()
        assertThat(JwtDecoder.decode("")).isNull()
        assertThat(JwtDecoder.decode("not-a-jwt")).isNull()
    }
}
