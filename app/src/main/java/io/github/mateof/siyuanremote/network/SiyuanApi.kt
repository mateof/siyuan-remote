// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.network

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** The subset of the SiYuan kernel HTTP API this app talks to. */
@Singleton
class SiyuanApi @Inject constructor(
    private val client: OkHttpClient,
    private val json: Json,
) {
    /**
     * Short-timeout client used to decide, quickly, whether a candidate host is
     * up. Waiting 8 seconds per candidate before falling back would make every
     * cold start off the LAN feel broken.
     */
    private val probeClient: OkHttpClient by lazy {
        client.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .callTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Unauthenticated liveness probe. Returns the kernel version, or null when
     * the host does not answer or is not a SiYuan kernel.
     */
    suspend fun version(baseUrl: String): String? = runCatching {
        val request = Request.Builder()
            .url("$baseUrl/api/system/version")
            .post(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        val response = execute(request, probeClient)
        if (!response.ok) return@runCatching null
        response.data?.jsonPrimitive?.content
    }.getOrNull()

    /** `code == 0` means the session cookie is valid for this kernel. */
    suspend fun getConf(baseUrl: String): ApiResponse = post(baseUrl, "/api/system/getConf", EMPTY_JSON)

    suspend fun loginAuth(baseUrl: String, authCode: String, captcha: String? = null): ApiResponse {
        val body = buildJsonObject {
            put("authCode", authCode)
            put("rememberMe", true)
            captcha?.let { put("captcha", it) }
        }
        return post(baseUrl, "/api/system/loginAuth", body.toString())
    }

    suspend fun logoutAuth(baseUrl: String): ApiResponse = post(baseUrl, "/api/system/logoutAuth", EMPTY_JSON)

    /**
     * PNG the kernel serves once too many wrong codes have been submitted.
     * Rendering it in the app avoids the dead end of "captcha required" with no
     * way to see the captcha.
     */
    suspend fun captchaImage(baseUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$baseUrl/api/system/getCaptcha").get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        }.getOrNull()
    }

    suspend fun listNotebooks(baseUrl: String): List<Notebook> {
        val response = post(baseUrl, "/api/notebook/lsNotebooks", EMPTY_JSON)
        if (!response.ok) throw ApiException(response.msg.ifBlank { "Could not list notebooks" })
        val notebooks = response.data?.jsonObject?.get("notebooks")?.jsonArray ?: JsonArray(emptyList())
        return notebooks.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            Notebook(
                id = id,
                name = obj["name"]?.jsonPrimitive?.content.orEmpty(),
                closed = obj["closed"]?.jsonPrimitive?.content?.toBoolean() ?: false,
            )
        }
    }

    /**
     * Appends a markdown block to today's daily note in [notebookId], creating
     * the daily note if it does not exist yet. Returns the id of the new block
     * so the caller can offer "open in app".
     */
    suspend fun appendToDailyNote(baseUrl: String, notebookId: String, markdown: String): String? {
        val body = buildJsonObject {
            put("notebook", notebookId)
            put("dataType", "markdown")
            put("data", markdown)
        }
        val response = post(baseUrl, "/api/block/appendDailyNoteBlock", body.toString())
        if (!response.ok) throw ApiException(response.msg.ifBlank { "Could not append to the daily note" })
        return response.firstOperationId()
    }

    /**
     * Returns the document id of today's daily note in [notebookId], creating it
     * if needed. Used by the "Daily note" launcher shortcut.
     */
    suspend fun createDailyNote(baseUrl: String, notebookId: String): String? {
        val body = buildJsonObject {
            put("notebook", notebookId)
            put("app", APP_ID)
        }
        val response = post(baseUrl, "/api/filetree/createDailyNote", body.toString())
        if (!response.ok) throw ApiException(response.msg.ifBlank { "Could not open the daily note" })
        return response.data?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    /** Uploads a file to the workspace assets and returns its `assets/...` path. */
    suspend fun uploadAsset(
        baseUrl: String,
        resolver: ContentResolver,
        uri: Uri,
        fileName: String,
    ): String? = withContext(Dispatchers.IO) {
        val mediaType = (resolver.getType(uri) ?: "application/octet-stream").toMediaType()
        val filePart = object : RequestBody() {
            override fun contentType() = mediaType
            override fun writeTo(sink: BufferedSink) {
                resolver.openInputStream(uri)?.use { input -> sink.writeAll(input.source()) }
                    ?: throw IOException("Could not read $uri")
            }
        }
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("assetsDirPath", "/assets/")
            .addFormDataPart("file[]", fileName, filePart)
            .build()
        val request = Request.Builder()
            .url(baseUrl + "/api/asset/upload")
            .post(multipart)
            .build()
        val response = execute(request)
        if (!response.ok) throw ApiException(response.msg.ifBlank { "Could not upload the asset" })
        response.data?.jsonObject
            ?.get("succMap")?.jsonObject
            ?.values?.firstOrNull()?.jsonPrimitive?.content
    }

    suspend fun performSync(baseUrl: String): ApiResponse = post(baseUrl, "/api/sync/performSync", EMPTY_JSON)

    private suspend fun post(baseUrl: String, path: String, jsonBody: String): ApiResponse {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(jsonBody.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return execute(request)
    }

    private suspend fun execute(
        request: Request,
        httpClient: OkHttpClient = client,
    ): ApiResponse = withContext(Dispatchers.IO) {
        httpClient.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            val parsed = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ApiResponse(
                httpCode = response.code,
                code = parsed?.get("code")?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                msg = parsed?.get("msg")?.jsonPrimitive?.content.orEmpty(),
                data = parsed?.get("data"),
            )
        }
    }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        const val EMPTY_JSON = "{}"

        /** Identifies this client in kernel broadcast events. */
        const val APP_ID = "siyuan-remote-android"
    }
}

data class ApiResponse(
    val httpCode: Int,
    val code: Int,
    val msg: String,
    val data: JsonElement?,
) {
    val ok: Boolean get() = httpCode in 200..299 && code == 0
    val unauthorized: Boolean get() = httpCode == 401

    /**
     * The kernel answers block transactions with
     * `[{ "doOperations": [{ "id": "2024...-abcdefg", ... }] }]`.
     */
    fun firstOperationId(): String? = runCatching {
        (data as? JsonArray)?.firstOrNull()?.jsonObject
            ?.get("doOperations")?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("id")?.jsonPrimitive?.content
    }.getOrNull()
}

data class Notebook(val id: String, val name: String, val closed: Boolean)

class ApiException(message: String) : Exception(message)
