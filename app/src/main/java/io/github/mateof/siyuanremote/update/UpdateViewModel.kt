package io.github.mateof.siyuanremote.update

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Shared by the launch dialog and the Settings section, so a download started
 * in one place is visible in the other.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(UpdateUiState(installedVersion = repository.installedVersion))
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    /** Throttled to once a day unless [force] is set by the manual button. */
    fun check(force: Boolean = false) {
        if (_state.value.isChecking || _state.value.isDownloading) return
        _state.update { it.copy(isChecking = true, checkFailed = false, upToDate = false) }
        viewModelScope.launch {
            val release = runCatching { repository.check(force) }.getOrNull()
            _state.update {
                it.copy(
                    isChecking = false,
                    available = release ?: it.available,
                    // Only claim "up to date" for an explicit check: a throttled
                    // one that returned nothing proves nothing.
                    upToDate = force && release == null,
                    checkFailed = false,
                )
            }
        }
    }

    fun download() {
        val release = _state.value.available ?: return
        if (_state.value.isDownloading) return
        _state.update { it.copy(isDownloading = true, progress = 0f, downloadFailed = false) }
        viewModelScope.launch {
            repository.download(release) { fraction ->
                _state.update { it.copy(progress = fraction) }
            }.onSuccess { file ->
                _state.update { it.copy(isDownloading = false, downloaded = file, progress = 1f) }
            }.onFailure {
                _state.update { it.copy(isDownloading = false, downloadFailed = true, progress = null) }
            }
        }
    }

    fun skipCurrent() {
        val release = _state.value.available ?: return
        viewModelScope.launch {
            repository.skip(release.version)
            _state.update { it.copy(available = null, dismissed = true) }
        }
    }

    fun dismiss() = _state.update { it.copy(dismissed = true) }

    fun canInstall(): Boolean = repository.canInstall()

    fun installIntent(apk: File): Intent = repository.installIntent(apk)

    fun unknownSourcesIntent(): Intent = repository.unknownSourcesIntent()
}

data class UpdateUiState(
    val installedVersion: String,
    val available: GitHubRelease? = null,
    val isChecking: Boolean = false,
    val isDownloading: Boolean = false,
    /** Null while the total size is unknown. */
    val progress: Float? = null,
    val downloaded: File? = null,
    val downloadFailed: Boolean = false,
    val checkFailed: Boolean = false,
    val upToDate: Boolean = false,
    /** The launch dialog was closed; Settings still shows the update. */
    val dismissed: Boolean = false,
)
