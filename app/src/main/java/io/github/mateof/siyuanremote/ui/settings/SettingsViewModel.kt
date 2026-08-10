// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mateof.siyuanremote.capture.CaptureRepository
import io.github.mateof.siyuanremote.data.AppSettings
import io.github.mateof.siyuanremote.data.SettingsRepository
import io.github.mateof.siyuanremote.network.Notebook
import io.github.mateof.siyuanremote.network.SessionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val captureRepository: CaptureRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    val settings: StateFlow<AppSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _notebooks = MutableStateFlow<NotebookListState>(NotebookListState.Idle)
    val notebooks: StateFlow<NotebookListState> = _notebooks.asStateFlow()

    private val _signedOut = MutableStateFlow(false)
    val signedOut: StateFlow<Boolean> = _signedOut.asStateFlow()

    fun loadNotebooks() {
        if (_notebooks.value is NotebookListState.Loading) return
        _notebooks.value = NotebookListState.Loading
        viewModelScope.launch {
            _notebooks.value = captureRepository.notebooks().fold(
                onSuccess = { NotebookListState.Loaded(it) },
                onFailure = { NotebookListState.Failed(it.message.orEmpty()) },
            )
        }
    }

    fun setServer(primary: String, fallback: String) = launchEdit {
        settingsRepository.setServer(primary, fallback)
    }

    fun setAppLock(enabled: Boolean) = launchEdit { settingsRepository.setAppLockEnabled(enabled) }
    fun setKeepScreenOn(enabled: Boolean) = launchEdit { settingsRepository.setKeepScreenOn(enabled) }
    fun setQuickButton(enabled: Boolean) = launchEdit { settingsRepository.setShowQuickButton(enabled) }
    fun setForceDesktopUi(enabled: Boolean) = launchEdit { settingsRepository.setForceDesktopUi(enabled) }
    fun setAllowMixedContent(enabled: Boolean) = launchEdit { settingsRepository.setAllowMixedContent(enabled) }
    fun setNativeClipboard(enabled: Boolean) = launchEdit { settingsRepository.setNativeClipboard(enabled) }
    fun setOpenExternalLinks(enabled: Boolean) = launchEdit {
        settingsRepository.setOpenExternalLinksInBrowser(enabled)
    }

    fun setTextZoom(zoom: Int) = launchEdit { settingsRepository.setTextZoom(zoom) }
    fun setSyncOnResume(enabled: Boolean) = launchEdit { settingsRepository.setSyncOnResume(enabled) }
    fun setUpdateChecks(enabled: Boolean) = launchEdit { settingsRepository.setUpdateChecksEnabled(enabled) }
    fun setCaptureAsTask(enabled: Boolean) = launchEdit { settingsRepository.setCaptureAsTask(enabled) }
    fun setCaptureTimestamp(enabled: Boolean) = launchEdit { settingsRepository.setCaptureTimestamp(enabled) }
    fun setCaptureNotification(enabled: Boolean) = launchEdit {
        settingsRepository.setCaptureNotificationEnabled(enabled)
    }

    fun setCaptureNotebook(notebook: Notebook?) = launchEdit {
        settingsRepository.setCaptureNotebook(notebook?.id.orEmpty(), notebook?.name.orEmpty())
    }

    fun signOut() {
        viewModelScope.launch {
            sessionManager.signOut()
            _signedOut.value = true
        }
    }

    private fun launchEdit(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}

sealed interface NotebookListState {
    data object Idle : NotebookListState
    data object Loading : NotebookListState
    data class Loaded(val notebooks: List<Notebook>) : NotebookListState
    data class Failed(val message: String) : NotebookListState
}
