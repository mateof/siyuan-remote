// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.ui.web

import android.app.DownloadManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.webkit.CookieManager
import android.webkit.URLUtil
import java.io.File

/**
 * Saves what the SiYuan web UI hands us.
 *
 * Two paths, because SiYuan uses both: plain `http(s)` links (exports, assets)
 * go through [DownloadManager], while in-browser generated files arrive as
 * `blob:` URLs that no download manager can resolve. Mobile browsers usually
 * fail silently on the second kind, which is one of the concrete things this
 * app fixes compared to opening the server in Chrome.
 */
class Downloader(private val context: Context) {

    fun enqueueHttpDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ): Result<String> = runCatching {
        val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle(fileName)
            setMimeType(mimeType)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            userAgent?.let { addRequestHeader("User-Agent", it) }
            CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
        }
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.enqueue(request)
        fileName
    }.onFailure { Log.w(TAG, "Download of $url failed", it) }

    /** Writes bytes decoded from a `blob:` URL into the shared Downloads folder. */
    fun saveToDownloads(bytes: ByteArray, fileName: String, mimeType: String): Result<Uri> = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType.ifBlank { "application/octet-stream" })
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("MediaStore refused to create $fileName")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not open $uri")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } else {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!dir.exists()) dir.mkdirs()
            val target = File(dir, fileName)
            target.writeBytes(bytes)
            Uri.fromFile(target)
        }
    }.onFailure { Log.w(TAG, "Could not save $fileName", it) }

    /**
     * Decodes the `data:<mime>;base64,<payload>` string produced by FileReader
     * in [BLOB_DOWNLOAD_SCRIPT].
     */
    fun decodeDataUrl(dataUrl: String): ByteArray? = runCatching {
        val payload = dataUrl.substringAfter("base64,", missingDelimiterValue = "")
        if (payload.isEmpty()) return null
        Base64.decode(payload, Base64.DEFAULT)
    }.getOrNull()

    companion object {
        private const val TAG = "Downloader"

        /** Name the WebView exposes the Kotlin bridge under. */
        const val BRIDGE_NAME = "SiyuanRemoteBridge"

        /**
         * Reads a blob back into the page and ships it to Kotlin as a data URL.
         * Deliberately does not touch any SiYuan global, so it cannot interfere
         * with the frontend.
         */
        fun blobDownloadScript(blobUrl: String, fileName: String, mimeType: String): String = """
            (function () {
              try {
                fetch(${blobUrl.asJsString()})
                  .then(function (r) { return r.blob(); })
                  .then(function (blob) {
                    var reader = new FileReader();
                    reader.onloadend = function () {
                      $BRIDGE_NAME.onBlobDownloaded(
                        reader.result, ${fileName.asJsString()}, ${mimeType.asJsString()});
                    };
                    reader.onerror = function () { $BRIDGE_NAME.onBlobFailed('read error'); };
                    reader.readAsDataURL(blob);
                  })
                  .catch(function (e) { $BRIDGE_NAME.onBlobFailed(String(e)); });
              } catch (e) {
                $BRIDGE_NAME.onBlobFailed(String(e));
              }
            })();
        """.trimIndent()

        private fun String.asJsString(): String =
            '"' + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + '"'
    }
}
