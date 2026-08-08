package io.github.mateof.siyuanremote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toAppSettings() }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setServer(primaryUrl: String, fallbackUrl: String) = edit {
        it[Keys.PRIMARY_URL] = normalizeBaseUrl(primaryUrl)
        it[Keys.FALLBACK_URL] = normalizeBaseUrl(fallbackUrl)
        it.remove(Keys.LAST_GOOD_URL)
    }

    suspend fun setLastGoodUrl(url: String) = edit { it[Keys.LAST_GOOD_URL] = normalizeBaseUrl(url) }

    suspend fun setAppLockEnabled(enabled: Boolean) = edit { it[Keys.APP_LOCK] = enabled }
    suspend fun setKeepScreenOn(enabled: Boolean) = edit { it[Keys.KEEP_SCREEN_ON] = enabled }
    suspend fun setShowQuickButton(enabled: Boolean) = edit { it[Keys.QUICK_BUTTON] = enabled }

    suspend fun setQuickButtonPosition(x: Float, y: Float) = edit {
        it[Keys.QUICK_BUTTON_X] = x.coerceIn(0f, 1f)
        it[Keys.QUICK_BUTTON_Y] = y.coerceIn(0f, 1f)
    }
    suspend fun setOpenExternalLinksInBrowser(enabled: Boolean) = edit { it[Keys.EXTERNAL_LINKS] = enabled }
    suspend fun setForceDesktopUi(enabled: Boolean) = edit { it[Keys.FORCE_DESKTOP] = enabled }
    suspend fun setAllowMixedContent(enabled: Boolean) = edit { it[Keys.MIXED_CONTENT] = enabled }
    suspend fun setTextZoom(zoom: Int) = edit { it[Keys.TEXT_ZOOM] = zoom.coerceIn(50, 200) }
    suspend fun setCaptureNotificationEnabled(enabled: Boolean) = edit { it[Keys.CAPTURE_NOTIFICATION] = enabled }
    suspend fun setSyncOnResume(enabled: Boolean) = edit { it[Keys.SYNC_ON_RESUME] = enabled }
    suspend fun setUpdateChecksEnabled(enabled: Boolean) = edit { it[Keys.UPDATE_CHECKS] = enabled }
    suspend fun setLastUpdateCheckAt(millis: Long) = edit { it[Keys.LAST_UPDATE_CHECK] = millis }
    suspend fun setSkippedUpdateVersion(version: String) = edit { it[Keys.SKIPPED_UPDATE] = version }
    suspend fun setCaptureAsTask(enabled: Boolean) = edit { it[Keys.CAPTURE_AS_TASK] = enabled }
    suspend fun setCaptureTimestamp(enabled: Boolean) = edit { it[Keys.CAPTURE_TIMESTAMP] = enabled }

    suspend fun setCaptureNotebook(id: String, name: String) = edit {
        it[Keys.CAPTURE_NOTEBOOK_ID] = id
        it[Keys.CAPTURE_NOTEBOOK_NAME] = name
    }

    suspend fun clearServer() = edit {
        it.remove(Keys.PRIMARY_URL)
        it.remove(Keys.FALLBACK_URL)
        it.remove(Keys.LAST_GOOD_URL)
        it.remove(Keys.CAPTURE_NOTEBOOK_ID)
        it.remove(Keys.CAPTURE_NOTEBOOK_NAME)
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit(block)
    }

    private fun Preferences.toAppSettings() = AppSettings(
        primaryUrl = this[Keys.PRIMARY_URL].orEmpty(),
        fallbackUrl = this[Keys.FALLBACK_URL].orEmpty(),
        lastGoodUrl = this[Keys.LAST_GOOD_URL].orEmpty(),
        appLockEnabled = this[Keys.APP_LOCK] ?: true,
        keepScreenOn = this[Keys.KEEP_SCREEN_ON] ?: false,
        showQuickButton = this[Keys.QUICK_BUTTON] ?: true,
        quickButtonX = this[Keys.QUICK_BUTTON_X] ?: 1f,
        quickButtonY = this[Keys.QUICK_BUTTON_Y] ?: 1f,
        openExternalLinksInBrowser = this[Keys.EXTERNAL_LINKS] ?: true,
        forceDesktopUi = this[Keys.FORCE_DESKTOP] ?: false,
        allowMixedContent = this[Keys.MIXED_CONTENT] ?: false,
        textZoom = this[Keys.TEXT_ZOOM] ?: 100,
        captureNotificationEnabled = this[Keys.CAPTURE_NOTIFICATION] ?: false,
        syncOnResume = this[Keys.SYNC_ON_RESUME] ?: false,
        updateChecksEnabled = this[Keys.UPDATE_CHECKS] ?: true,
        lastUpdateCheckAt = this[Keys.LAST_UPDATE_CHECK] ?: 0L,
        skippedUpdateVersion = this[Keys.SKIPPED_UPDATE].orEmpty(),
        captureNotebookId = this[Keys.CAPTURE_NOTEBOOK_ID].orEmpty(),
        captureNotebookName = this[Keys.CAPTURE_NOTEBOOK_NAME].orEmpty(),
        captureAsTask = this[Keys.CAPTURE_AS_TASK] ?: false,
        captureTimestamp = this[Keys.CAPTURE_TIMESTAMP] ?: true,
    )

    private object Keys {
        val PRIMARY_URL = stringPreferencesKey("primary_url")
        val FALLBACK_URL = stringPreferencesKey("fallback_url")
        val LAST_GOOD_URL = stringPreferencesKey("last_good_url")
        val APP_LOCK = booleanPreferencesKey("app_lock")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val QUICK_BUTTON = booleanPreferencesKey("quick_button")
        val QUICK_BUTTON_X = floatPreferencesKey("quick_button_x")
        val QUICK_BUTTON_Y = floatPreferencesKey("quick_button_y")
        val EXTERNAL_LINKS = booleanPreferencesKey("external_links")
        val FORCE_DESKTOP = booleanPreferencesKey("force_desktop")
        val MIXED_CONTENT = booleanPreferencesKey("mixed_content")
        val TEXT_ZOOM = intPreferencesKey("text_zoom")
        val CAPTURE_NOTIFICATION = booleanPreferencesKey("capture_notification")
        val SYNC_ON_RESUME = booleanPreferencesKey("sync_on_resume")
        val UPDATE_CHECKS = booleanPreferencesKey("update_checks")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val SKIPPED_UPDATE = stringPreferencesKey("skipped_update")
        val CAPTURE_NOTEBOOK_ID = stringPreferencesKey("capture_notebook_id")
        val CAPTURE_NOTEBOOK_NAME = stringPreferencesKey("capture_notebook_name")
        val CAPTURE_AS_TASK = booleanPreferencesKey("capture_as_task")
        val CAPTURE_TIMESTAMP = booleanPreferencesKey("capture_timestamp")
    }
}

/**
 * Accepts what a human types (`192.168.1.50:6806`, trailing slashes, spaces) and
 * returns something OkHttp and the WebView both agree on.
 */
fun normalizeBaseUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return ""
    val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        trimmed
    } else {
        "http://$trimmed"
    }
    return withScheme.trimEnd('/')
}
