// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses the shapes real tags come in`() {
        assertEquals(listOf(0, 2, 0), AppVersion.parse("v0.2.0")?.parts)
        assertEquals(listOf(0, 2, 0), AppVersion.parse("0.2.0")?.parts)
        assertEquals(listOf(1, 10), AppVersion.parse(" 1.10 ")?.parts)
    }

    @Test
    fun `ignores the build suffix`() {
        // The debug variant appends -debug to versionName. Without this, a debug
        // 0.2.0 would look older than release 0.2.0 and the app would offer to
        // update to the version it is already running.
        assertEquals(AppVersion.parse("0.2.0"), AppVersion.parse("0.2.0-debug"))
        assertEquals(AppVersion.parse("0.2.0"), AppVersion.parse("0.2.0+build7"))
    }

    @Test
    fun `treats missing components as zero`() {
        assertEquals(0, AppVersion.parse("1.2")!!.compareTo(AppVersion.parse("1.2.0")!!))
        assertTrue(AppVersion.parse("1.2.1")!! > AppVersion.parse("1.2")!!)
    }

    @Test
    fun `compares numerically, not as text`() {
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.0")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
    }

    @Test
    fun `rejects what is not a version`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
    }

    @Test
    fun `only reports strictly newer releases`() {
        assertTrue(isNewerVersion("0.2.0", "0.1.0"))
        assertTrue(isNewerVersion("v0.2.0", "0.1.9"))
        assertFalse(isNewerVersion("0.1.0", "0.1.0"))
        assertFalse(isNewerVersion("0.1.0", "0.2.0"))
        assertFalse(isNewerVersion("0.1.0", "0.1.0-debug"))
    }

    @Test
    fun `refuses to guess when either side is unparseable`() {
        assertFalse(isNewerVersion("nightly", "0.1.0"))
        assertFalse(isNewerVersion("0.2.0", null))
    }
}
