// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.ui.setup

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.mateof.siyuanremote.R
import io.github.mateof.siyuanremote.data.AppSettings
import io.github.mateof.siyuanremote.ui.SetupError

@Composable
fun SetupScreen(
    prefill: AppSettings,
    error: SetupError?,
    serverMessage: String,
    needsCaptcha: Boolean,
    isSubmitting: Boolean,
    loadCaptcha: suspend () -> ByteArray?,
    onSubmit: (primaryUrl: String, fallbackUrl: String, authCode: String, captcha: String?) -> Unit,
) {
    var primaryUrl by remember(prefill.primaryUrl) { mutableStateOf(prefill.primaryUrl) }
    var fallbackUrl by remember(prefill.fallbackUrl) { mutableStateOf(prefill.fallbackUrl) }
    var authCode by remember { mutableStateOf("") }
    var captcha by remember { mutableStateOf("") }
    var codeVisible by remember { mutableStateOf(false) }
    var captchaImage by remember { mutableStateOf<ByteArray?>(null) }

    LaunchedEffect(needsCaptcha) {
        captchaImage = if (needsCaptcha) loadCaptcha() else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        error?.let { ErrorCard(it, serverMessage) }

        OutlinedTextField(
            value = primaryUrl,
            onValueChange = { primaryUrl = it },
            label = { Text(stringResource(R.string.setup_primary_url)) },
            placeholder = { Text("http://192.168.1.50:6806") },
            supportingText = { Text(stringResource(R.string.setup_primary_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = fallbackUrl,
            onValueChange = { fallbackUrl = it },
            label = { Text(stringResource(R.string.setup_fallback_url)) },
            placeholder = { Text("https://siyuan.my-tailnet.ts.net") },
            supportingText = { Text(stringResource(R.string.setup_fallback_url_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = authCode,
            onValueChange = { authCode = it },
            label = { Text(stringResource(R.string.setup_auth_code)) },
            supportingText = { Text(stringResource(R.string.setup_auth_code_hint)) },
            singleLine = true,
            visualTransformation = if (codeVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = if (needsCaptcha) ImeAction.Next else ImeAction.Done,
            ),
            trailingIcon = {
                IconButton(onClick = { codeVisible = !codeVisible }) {
                    Icon(
                        imageVector = if (codeVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = stringResource(
                            if (codeVisible) R.string.action_hide_code else R.string.action_show_code,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        if (needsCaptcha) {
            captchaImage?.let { bytes ->
                val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
                bitmap?.let {
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = stringResource(R.string.setup_captcha),
                        modifier = Modifier.height(52.dp),
                    )
                }
            }
            OutlinedTextField(
                value = captcha,
                onValueChange = { captcha = it },
                label = { Text(stringResource(R.string.setup_captcha)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onSubmit(primaryUrl, fallbackUrl, authCode, captcha.takeIf { needsCaptcha }) },
            enabled = !isSubmitting && primaryUrl.isNotBlank() && authCode.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isSubmitting) {
                CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.setup_connect))
            }
        }

        Text(
            text = stringResource(R.string.setup_storage_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorCard(error: SetupError, serverMessage: String) {
    val message = when (error) {
        SetupError.UNREACHABLE -> stringResource(R.string.setup_error_unreachable)
        SetupError.WRONG_CODE -> stringResource(R.string.setup_error_wrong_code)
        SetupError.CAPTCHA_REQUIRED -> stringResource(R.string.setup_error_captcha)
        SetupError.SERVER_HAS_NO_AUTH_CODE -> stringResource(R.string.setup_error_no_auth_code)
        SetupError.SESSION_REJECTED -> stringResource(R.string.setup_error_session_rejected)
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            if (serverMessage.isNotBlank()) {
                Text(
                    text = serverMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
fun StatusScreen(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = onSecondary) { Text(secondaryLabel) }
        }
    }
}
