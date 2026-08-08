package io.github.mateof.siyuanremote.capture

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import io.github.mateof.siyuanremote.MainActivity
import io.github.mateof.siyuanremote.R
import io.github.mateof.siyuanremote.ui.theme.SiyuanRemoteTheme

/**
 * One text box between a thought and the daily note.
 *
 * Doubles as the system share target, so text, links and images from any other
 * app land in SiYuan without opening the editor. The web UI cannot register as
 * a share target, which makes this one of the clearest wins of running inside
 * an app.
 */
@AndroidEntryPoint
class QuickCaptureActivity : ComponentActivity() {

    private val viewModel: QuickCaptureViewModel by viewModels()

    private val dictationLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?: return@registerForActivityResult
            viewModel.appendDictation(spoken)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeSharedContent(intent)

        setContent {
            SiyuanRemoteTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.sent) {
                    if (state.sent) {
                        Toast.makeText(
                            this@QuickCaptureActivity,
                            getString(R.string.capture_sent),
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    }
                }

                Surface(color = MaterialTheme.colorScheme.surface) {
                    CaptureSheet(
                        state = state,
                        onTextChanged = viewModel::onTextChanged,
                        onAsTaskChanged = viewModel::onAsTaskChanged,
                        onDictate = ::startDictation,
                        onSubmit = viewModel::submit,
                        onOpenInApp = ::openCapturedBlock,
                        onCancel = ::finish,
                    )
                }
            }
        }
    }

    private fun consumeSharedContent(intent: Intent) {
        val text = listOfNotNull(
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.getStringExtra(Intent.EXTRA_TEXT),
        ).distinct().joinToString("\n\n")

        val attachments = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.parcelableExtra<Uri>(Intent.EXTRA_STREAM))
            Intent.ACTION_SEND_MULTIPLE -> intent.parcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
            else -> emptyList()
        }

        if (text.isNotBlank() || attachments.isNotEmpty()) {
            viewModel.prefill(text, attachments)
        }
    }

    private fun startDictation() {
        val request = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, getString(R.string.capture_dictate_prompt))
        }
        runCatching { dictationLauncher.launch(request) }
            .onFailure {
                Toast.makeText(this, getString(R.string.capture_dictate_unavailable), Toast.LENGTH_SHORT).show()
            }
    }

    private fun openCapturedBlock(blockId: String) {
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_BLOCK_ID, blockId),
        )
        finish()
    }

    private inline fun <reified T : android.os.Parcelable> Intent.parcelableExtra(name: String): T? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, T::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(name)
        }

    private inline fun <reified T : android.os.Parcelable> Intent.parcelableArrayListExtra(
        name: String,
    ): ArrayList<T>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(name, T::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra(name)
    }
}

@Composable
private fun CaptureSheet(
    state: QuickCaptureState,
    onTextChanged: (String) -> Unit,
    onAsTaskChanged: (Boolean) -> Unit,
    onDictate: () -> Unit,
    onSubmit: () -> Unit,
    onOpenInApp: (String) -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.capture_title),
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            text = if (state.notebookName.isBlank()) {
                stringResource(R.string.capture_target_default)
            } else {
                stringResource(R.string.capture_target_named, state.notebookName)
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = state.text,
            onValueChange = onTextChanged,
            label = { Text(stringResource(R.string.capture_hint)) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            trailingIcon = {
                IconButton(onClick = onDictate) {
                    Icon(Icons.Default.Mic, contentDescription = stringResource(R.string.capture_dictate))
                }
            },
        )

        if (state.attachments.isNotEmpty()) {
            Text(
                text = stringResource(R.string.capture_attachments, state.attachments.size),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        FilterChip(
            selected = state.asTask,
            onClick = { onAsTaskChanged(!state.asTask) },
            label = { Text(stringResource(R.string.capture_as_task)) },
        )

        state.error?.let {
            val message = when (it) {
                QuickCaptureError.EMPTY -> stringResource(R.string.capture_error_empty)
                QuickCaptureError.FAILED -> state.errorDetail.ifBlank {
                    stringResource(R.string.capture_error_generic)
                }
            }
            Text(text = message, color = MaterialTheme.colorScheme.error)
        }

        state.sentBlockId?.let { blockId ->
            TextButton(onClick = { onOpenInApp(blockId) }) {
                Text(stringResource(R.string.capture_open_in_app))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel)) }
            Button(onClick = onSubmit, enabled = !state.isSending) {
                if (state.isSending) {
                    CircularProgressIndicator(modifier = Modifier.heightIn(max = 18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.capture_send))
                }
            }
        }
    }
}
