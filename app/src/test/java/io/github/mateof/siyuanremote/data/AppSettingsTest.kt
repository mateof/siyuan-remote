package io.github.mateof.siyuanremote.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NormalizeBaseUrlTest {

    @Test
    fun `adds http when the scheme is missing`() {
        assertEquals("http://192.168.1.50:6806", normalizeBaseUrl("192.168.1.50:6806"))
    }

    @Test
    fun `keeps an explicit https scheme`() {
        assertEquals("https://notes.example.com", normalizeBaseUrl("https://notes.example.com"))
    }

    @Test
    fun `strips trailing slashes and surrounding spaces`() {
        assertEquals("http://siyuan.lan:6806", normalizeBaseUrl("  http://siyuan.lan:6806/  "))
    }

    @Test
    fun `leaves an empty value empty`() {
        assertEquals("", normalizeBaseUrl("   "))
    }
}

class AppSettingsTest {

    @Test
    fun `is not configured without a primary url`() {
        assertFalse(AppSettings().isConfigured)
        assertTrue(AppSettings(primaryUrl = "http://siyuan.lan:6806").isConfigured)
    }

    @Test
    fun `probes the last good url first so cold starts skip a dead candidate`() {
        val settings = AppSettings(
            primaryUrl = "http://siyuan.lan:6806",
            fallbackUrl = "https://siyuan.example.ts.net",
            lastGoodUrl = "https://siyuan.example.ts.net",
        )
        assertEquals(
            listOf("https://siyuan.example.ts.net", "http://siyuan.lan:6806"),
            settings.candidateUrls,
        )
    }

    @Test
    fun `drops blank candidates`() {
        val settings = AppSettings(primaryUrl = "http://siyuan.lan:6806")
        assertEquals(listOf("http://siyuan.lan:6806"), settings.candidateUrls)
    }
}
