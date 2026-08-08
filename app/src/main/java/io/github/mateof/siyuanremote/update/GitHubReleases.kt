package io.github.mateof.siyuanremote.update

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads the project's own releases from the GitHub API.
 *
 * Deliberately takes a **dedicated** client rather than the app's shared one.
 * The shared client carries [io.github.mateof.siyuanremote.network.WebViewCookieJar],
 * which is not host scoped, so reusing it would ship the SiYuan session cookie
 * to github.com on every check.
 */
@Singleton
class GitHubReleases @Inject constructor(
    @UpdateHttpClient private val client: OkHttpClient,
    private val json: Json,
) {
    /**
     * Latest non-prerelease, non-draft release that has an APK attached, or
     * null if there is none or the network is unavailable.
     */
    suspend fun latestRelease(): GitHubRelease? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$OWNER/$REPO/releases?per_page=10")
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub answered ${response.code}")
                    return@runCatching null
                }
                val body = response.body?.string().orEmpty()
                json.parseToJsonElement(body).jsonArray
                    .asSequence()
                    .map { it.jsonObject }
                    .filter { it["draft"]?.jsonPrimitive?.content != "true" }
                    .filter { it["prerelease"]?.jsonPrimitive?.content != "true" }
                    .mapNotNull { release ->
                        val tag = release["tag_name"]?.jsonPrimitive?.content ?: return@mapNotNull null
                        val asset = release["assets"]?.jsonArray
                            ?.map { it.jsonObject }
                            ?.firstOrNull { it["name"]?.jsonPrimitive?.content?.endsWith(".apk") == true }
                            ?: return@mapNotNull null
                        GitHubRelease(
                            tagName = tag,
                            version = tag.removePrefix("v"),
                            notes = release["body"]?.jsonPrimitive?.content.orEmpty(),
                            pageUrl = release["html_url"]?.jsonPrimitive?.content.orEmpty(),
                            apkName = asset["name"]?.jsonPrimitive?.content.orEmpty(),
                            apkUrl = asset["browser_download_url"]?.jsonPrimitive?.content.orEmpty(),
                            apkSizeBytes = asset["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        )
                    }
                    .firstOrNull()
            }
        }.onFailure { Log.w(TAG, "Update check failed", it) }.getOrNull()
    }

    private companion object {
        const val TAG = "GitHubReleases"
        const val OWNER = "mateof"
        const val REPO = "siyuan-remote"
    }
}

data class GitHubRelease(
    val tagName: String,
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkName: String,
    val apkUrl: String,
    val apkSizeBytes: Long,
)
