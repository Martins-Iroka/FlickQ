package com.martdev.flickq.feature.notification.data

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test

class AppInfoSanitizerTest {

    private val serverLocale = Regex("^[A-Za-z]{2,3}([-_][A-Za-z0-9]{1,8}){0,4}$")

    @Test
    fun `keeps valid locales`() {
        assertThat(AppInfoSanitizer.locale("en")).isEqualTo("en")
        assertThat(AppInfoSanitizer.locale("en-NG")).isEqualTo("en-NG")
        assertThat(AppInfoSanitizer.locale("en_NG")).isEqualTo("en-NG")
        assertThat(AppInfoSanitizer.locale("zh-Hant-TW")).isEqualTo("zh-Hant-TW")
        assertThat(AppInfoSanitizer.locale("yue")).isEqualTo("yue")
    }

    @Test
    fun `strips iOS keyword suffixes`() {
        assertThat(AppInfoSanitizer.locale("en_NG@calendar=gregorian")).isEqualTo("en-NG")
        assertThat(AppInfoSanitizer.locale("ar_EG@numbers=arab;calendar=islamic")).isEqualTo("ar-EG")
    }

    @Test
    fun `drops malformed subtags and caps at four`() {
        assertThat(AppInfoSanitizer.locale("en__POSIX")).isEqualTo("en-POSIX")
        assertThat(AppInfoSanitizer.locale("en-US-u-ca-gregory-nu-latn")).isEqualTo("en-US-u-ca-gregory")
        assertThat(AppInfoSanitizer.locale("de-DE-toolongsubtag")).isEqualTo("de-DE")
        assertThat(AppInfoSanitizer.locale("en-US ")).isEqualTo("en-US")
    }

    @Test
    fun `falls back to en when the language is unusable`() {
        listOf(
            null,
            "",
            "   ",
            "e",
            "english",
            "12-US",
            "@calendar=gregorian",
            "-US"
        ).forEach { raw ->
            assertThat(AppInfoSanitizer.locale(raw), name = raw.toString()).isEqualTo("en")
        }
    }

    @Test
    fun `every output matches the server pattern`() {
        listOf(
            "en",
            "en_NG@calendar=gregorian",
            "en-US-u-ca-gregory-nu-latn-x-a",
            "x",
            "pt_BR.UTF-8",
            "und",
            "abc-abcdefgh-abcdefgh-abcdefgh-abcdefgh",
        ).forEach { raw ->
            val locale = AppInfoSanitizer.locale(raw)
            assertThat(serverLocale.matches(locale) && locale.length <= 35, name = raw).isEqualTo(
                true
            )
        }
    }

    @Test
    fun `trailing subtags are dropped to fit 35 characters`() {
        assertThat(AppInfoSanitizer.locale("abc-abcdefgh-abcdefgh-abcdefgh-abcdefgh"))
            .isEqualTo("abc-abcdefgh-abcdefgh-abcdefgh")
    }

    @Test
    fun `app version is trimmed and capped at 32 characters`() {
        assertThat(AppInfoSanitizer.appVersion(" 1.4.0 ")).isEqualTo("1.4.0")
        assertThat(AppInfoSanitizer.appVersion("1".repeat(40)).length).isEqualTo(32)
        assertThat(AppInfoSanitizer.appVersion(null)).isEqualTo("unknown")
        assertThat(AppInfoSanitizer.appVersion("  ")).isEqualTo("unknown")
    }
}