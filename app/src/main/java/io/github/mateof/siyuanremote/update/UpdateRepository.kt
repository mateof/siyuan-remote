package io.github.mateof.siyuanremote.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.mateof.siyuanremote.BuildConfig
import io.github.mateof.siyuanremote.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds newer releases on GitHub, downloads the APK and hands it to the system
 * installer.
 *
 * The app is distributed outside any store, so without this the only upgrade
 * path is "notice by accident, open the browser, find the release, download,
 * find the file". None of which happens in practice, and the result is people
 * running an old build.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val releases: GitHubReleases,
    private val settingsRepository: SettingsRepository,
    @UpdateHttpClient private val client: OkHttpClient,
) {
    val installedVersion: String get() = BuildConfig.VERSION_NAME

    /**
     * @param force ignores the once-a-day throttle, for the manual button.
     * @return the release when it is newer than what is installed and the user
     *         has not skipped it, null otherwise.
     */
    suspend fun check(force: Boolean = false): GitHubRelease? {
        val settings = settingsRepository.current()
        if (!force && !settings.updateChecksEnabled) return null

        val now = System.currentTimeMillis()
        if (!force && now - settings.lastUpdateCheckAt < CHECK_INTERVAL_MS) return null

        val release = releases.latestRelease()
        settingsRepository.setLastUpdateCheckAt(now)
        if (release == null) return null

        if (!isNewerVersion(release.version, installedVersion)) return null
        if (!force && release.version == settings.skippedUpdateVersion) return null
        return release
    }

    /**
     * Streams the APK into the cache directory, reporting progress as a
     * fraction, or null while the total size is unknown.
     */
    suspend fun download(
        release: GitHubRelease,
        onProgress: (Float?) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
            // One file per version, and nothing else, so a failed attempt or an
            // older download cannot be installed by mistake.
            directory.listFiles()?.forEach { it.delete() }
            val target = File(directory, release.apkName.ifBlank { "update.apk" })

            val request = Request.Builder().url(release.apkUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("GitHub answered ${response.code}")
                val body = response.body ?: error("Empty response")
                val total = body.contentLength().takeIf { it > 0 } ?: release.apkSizeBytes

                body.byteStream().use { input ->
                    target.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var copied = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress(if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else null)
                        }
                    }
                }
            }
            target
        }.onFailure { Log.w(TAG, "Could not download ${release.apkUrl}", it) }
    }

    /** True when the system will let us start an install without a detour. */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    /** Sends the user to the "install unknown apps" screen for this app. */
    fun unknownSourcesIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun installIntent(apk: File): Intent {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun skip(version: String) = settingsRepository.setSkippedUpdateVersion(version)

    private companion object {
        const val TAG = "UpdateRepository"
        const val UPDATE_DIR = "updates"
        const val CHECK_INTERVAL_MS = 24L * 60 * 60 * 1000
    }
}
