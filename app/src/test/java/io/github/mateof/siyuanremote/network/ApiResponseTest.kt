// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResponseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `reads the block id out of a transaction result`() {
        val data = json.parseToJsonElement(
            """
            [
              {
                "doOperations": [
                  { "action": "insert", "id": "20260808120000-abcdefg", "parentID": "20260808-root" }
                ]
              }
            ]
            """.trimIndent(),
        )
        val response = ApiResponse(httpCode = 200, code = 0, msg = "", data = data)
        assertEquals("20260808120000-abcdefg", response.firstOperationId())
    }

    @Test
    fun `returns null instead of throwing on an unexpected shape`() {
        val data = json.parseToJsonElement("""{"unexpected": true}""")
        val response = ApiResponse(httpCode = 200, code = 0, msg = "", data = data)
        assertNull(response.firstOperationId())
    }

    @Test
    fun `only treats code zero on a 2xx as success`() {
        assertTrue(ApiResponse(200, 0, "", null).ok)
        assertFalse(ApiResponse(200, -1, "wrong code", null).ok)
        assertFalse(ApiResponse(401, 0, "", null).ok)
    }

    @Test
    fun `flags 401 so the session layer can renew instead of failing`() {
        assertTrue(ApiResponse(401, -1, "Auth failed [session]", null).unauthorized)
        assertFalse(ApiResponse(500, -1, "boom", null).unauthorized)
    }
}
