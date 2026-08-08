package io.github.mateof.siyuanremote.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mateof.siyuanremote.data.AppSettings
import io.github.mateof.siyuanremote.data.SettingsRepository
import io.github.mateof.siyuanremote.data.normalizeBaseUrl
import io.github.mateof.siyuanremote.network.AuthFailure
import io.github.mateof.siyuanremote.network.SessionManager
import io.github.mateof.siyuanremote.network.SessionResult
import io.github.mateof.siyuanremote.network.SiyuanApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val sessionManager: SessionManager,
    private val settingsRepository: SettingsRepository,
    private val api: SiyuanApi,
) : ViewModel() {

    /** Null until DataStore has produced its first value, so the UI does not
     *  flash a locked screen for users who disabled the app lock. */
    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val currentSettings: AppSettings get() = settings.value ?: AppSettings()

    private val _uiState = MutableStateFlow<MainUiState>(MainUiState.Loading)
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private val _events = Channel<MainEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** Survives configuration changes so rotating the device does not re-prompt. */
    var unlockedAt: Long = 0L

    init {
        connect()
    }

    fun connect(startPath: String = "/") {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            _uiState.value = when (val result = sessionManager.prepare()) {
                is SessionResult.Ready -> MainUiState.Connected(result.baseUrl, result.baseUrl + startPath)
                SessionResult.NotConfigured -> MainUiState.NeedsSetup(currentSettings)
                is SessionResult.Unreachable -> MainUiState.Unreachable(result.triedUrls)
                is SessionResult.AuthRequired -> result.toSetupState()
            }
        }
    }

    /**
     * The WebView hit `/check-auth`, so the cookie is gone. Replay the login in
     * the background and send the user straight back where they were going.
     */
    fun onSessionExpired(targetPath: String) {
        val baseUrl = sessionManager.activeBaseUrl ?: return
        viewModelScope.launch {
            if (sessionManager.renewSession(baseUrl)) {
                _events.send(MainEvent.Load(baseUrl + targetPath))
            } else {
                _uiState.value = MainUiState.NeedsSetup(
                    prefill = currentSettings,
                    error = SetupError.SESSION_REJECTED,
                )
            }
        }
    }

    fun submitSetup(primaryUrl: String, fallbackUrl: String, authCode: String, captcha: String?) {
        viewModelScope.launch {
            _uiState.value = MainUiState.Loading
            val normalizedPrimary = normalizeBaseUrl(primaryUrl)
            val normalizedFallback = normalizeBaseUrl(fallbackUrl)
            settingsRepository.setServer(normalizedPrimary, normalizedFallback)

            var result = sessionManager.signIn(normalizedPrimary, authCode, captcha)
            if (result is SessionResult.Unreachable && normalizedFallback.isNotBlank()) {
                result = sessionManager.signIn(normalizedFallback, authCode, captcha)
            }

            _uiState.value = when (result) {
                is SessionResult.Ready -> MainUiState.Connected(result.baseUrl, result.baseUrl + "/")
                is SessionResult.Unreachable -> MainUiState.NeedsSetup(
                    prefill = currentSettings.copy(primaryUrl = normalizedPrimary, fallbackUrl = normalizedFallback),
                    error = SetupError.UNREACHABLE,
                )

                is SessionResult.AuthRequired -> result.toSetupState()
                SessionResult.NotConfigured -> MainUiState.NeedsSetup(currentSettings)
            }
        }
    }

    /** Loads the captcha PNG the kernel demands after repeated failed logins. */
    suspend fun fetchCaptcha(): ByteArray? {
        val baseUrl = (uiState.value as? MainUiState.NeedsSetup)?.prefill?.primaryUrl
            ?: currentSettings.primaryUrl
        if (baseUrl.isBlank()) return null
        return api.captchaImage(baseUrl)
    }

    fun signOut() {
        viewModelScope.launch {
            sessionManager.signOut()
            unlockedAt = 0L
            _uiState.value = MainUiState.NeedsSetup(AppSettings())
        }
    }

    fun requestSync() {
        val baseUrl = sessionManager.activeBaseUrl ?: return
        viewModelScope.launch { runCatching { api.performSync(baseUrl) } }
    }

    fun openBlock(blockId: String) {
        viewModelScope.launch { _events.send(MainEvent.OpenBlock(blockId)) }
    }

    /**
     * Backs the "Daily note" launcher shortcut. The kernel creates the note if
     * today's does not exist yet, so this is also a one tap "start writing".
     */
    fun openDailyNote() {
        viewModelScope.launch {
            val baseUrl = sessionManager.activeBaseUrl
                ?: (sessionManager.prepare() as? SessionResult.Ready)?.baseUrl
                ?: return@launch
            val notebookId = currentSettings.captureNotebookId.ifBlank {
                runCatching { api.listNotebooks(baseUrl).firstOrNull { !it.closed }?.id }.getOrNull().orEmpty()
            }
            if (notebookId.isBlank()) return@launch
            runCatching { api.createDailyNote(baseUrl, notebookId) }.getOrNull()?.let { blockId ->
                _events.send(MainEvent.OpenBlock(blockId))
            }
        }
    }

    fun reload() {
        viewModelScope.launch { _events.send(MainEvent.Reload) }
    }

    private fun SessionResult.AuthRequired.toSetupState() = MainUiState.NeedsSetup(
        prefill = currentSettings.copy(primaryUrl = baseUrl.ifBlank { currentSettings.primaryUrl }),
        error = when (failure) {
            AuthFailure.NO_CODE_STORED -> null
            AuthFailure.WRONG_CODE -> SetupError.WRONG_CODE
            AuthFailure.CAPTCHA_REQUIRED -> SetupError.CAPTCHA_REQUIRED
            AuthFailure.SERVER_HAS_NO_AUTH_CODE -> SetupError.SERVER_HAS_NO_AUTH_CODE
        },
        needsCaptcha = failure == AuthFailure.CAPTCHA_REQUIRED,
        serverMessage = serverMessage,
    )
}

sealed interface MainUiState {
    data object Loading : MainUiState

    data class NeedsSetup(
        val prefill: AppSettings,
        val error: SetupError? = null,
        val needsCaptcha: Boolean = false,
        val serverMessage: String = "",
    ) : MainUiState

    data class Connected(val baseUrl: String, val startUrl: String) : MainUiState

    data class Unreachable(val tried: List<String>) : MainUiState
}

enum class SetupError {
    UNREACHABLE,
    WRONG_CODE,
    CAPTCHA_REQUIRED,
    SERVER_HAS_NO_AUTH_CODE,
    SESSION_REJECTED,
}

sealed interface MainEvent {
    data class Load(val url: String) : MainEvent
    data class OpenBlock(val blockId: String) : MainEvent
    data object Reload : MainEvent
}
