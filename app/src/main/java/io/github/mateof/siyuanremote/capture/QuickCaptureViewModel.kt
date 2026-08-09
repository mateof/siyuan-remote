// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.capture

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.mateof.siyuanremote.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val captureRepository: CaptureRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QuickCaptureState())
    val state: StateFlow<QuickCaptureState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.current()
            _state.update {
                it.copy(
                    notebookName = settings.captureNotebookName,
                    asTask = settings.captureAsTask,
                )
            }
        }
    }

    fun prefill(text: String, attachments: List<Uri>) {
        _state.update { it.copy(text = text, attachments = attachments) }
    }

    fun onTextChanged(text: String) {
        _state.update { it.copy(text = text, error = null) }
    }

    fun appendDictation(text: String) {
        _state.update {
            val separator = if (it.text.isBlank() || it.text.endsWith(" ")) "" else " "
            it.copy(text = it.text + separator + text)
        }
    }

    fun onAsTaskChanged(asTask: Boolean) {
        _state.update { it.copy(asTask = asTask) }
        viewModelScope.launch { settingsRepository.setCaptureAsTask(asTask) }
    }

    fun submit() {
        val current = _state.value
        if (current.isSending) return
        if (current.text.isBlank() && current.attachments.isEmpty()) {
            _state.update { it.copy(error = QuickCaptureError.EMPTY) }
            return
        }

        _state.update { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            captureRepository.capture(current.text, current.attachments)
                .onSuccess { result ->
                    _state.update { it.copy(isSending = false, sentBlockId = result.blockId, sent = true) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isSending = false,
                            error = QuickCaptureError.FAILED,
                            errorDetail = throwable.message.orEmpty(),
                        )
                    }
                }
        }
    }
}

data class QuickCaptureState(
    val text: String = "",
    val attachments: List<Uri> = emptyList(),
    val notebookName: String = "",
    val asTask: Boolean = false,
    val isSending: Boolean = false,
    val sent: Boolean = false,
    val sentBlockId: String? = null,
    val error: QuickCaptureError? = null,
    val errorDetail: String = "",
)

enum class QuickCaptureError { EMPTY, FAILED }
