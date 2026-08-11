// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.ui.lock

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Optional gate in front of the WebView.
 *
 * It exists because the access code is stored indefinitely: without a lock,
 * anyone holding an unlocked phone reaches the whole knowledge base. The prompt
 * is intentionally *not* wired to the Keystore key that protects the code, so
 * background capture keeps working while the app is locked.
 */
object AppLock {

    fun availableAuthenticators(context: Context): Int? {
        val manager = BiometricManager.from(context)
        // BIOMETRIC_WEAK or DEVICE_CREDENTIAL is rejected on API 28-29, so the
        // combinations are probed from most to least capable.
        val candidates = listOf(BIOMETRIC_WEAK or DEVICE_CREDENTIAL, BIOMETRIC_WEAK, DEVICE_CREDENTIAL)
        return candidates.firstOrNull {
            manager.canAuthenticate(it) == BiometricManager.BIOMETRIC_SUCCESS
        }
    }

    fun isAvailable(context: Context): Boolean = availableAuthenticators(context) != null

    private const val TAG = "AppLock"

    /** Errors that mean no credential exists to check against, ever. */
    private val UNAUTHENTICATABLE = setOf(
        BiometricPrompt.ERROR_HW_NOT_PRESENT,
        BiometricPrompt.ERROR_HW_UNAVAILABLE,
        BiometricPrompt.ERROR_NO_BIOMETRICS,
        BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
        BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
    )

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        cancelLabel: String,
        onSuccess: () -> Unit,
        onDismissed: () -> Unit,
    ) {
        val authenticators = availableAuthenticators(activity) ?: run {
            onSuccess()
            return
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Some errors mean the device cannot authenticate at all:
                    // no hardware, nothing enrolled, no device credential. The
                    // lock has nothing to check against, and refusing entry
                    // would make the app permanently unusable on that phone.
                    if (errorCode in UNAUTHENTICATABLE) {
                        Log.i(TAG, "Cannot authenticate on this device ($errorCode), unlocking")
                        onSuccess()
                    } else {
                        onDismissed()
                    }
                }
            },
        )

        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
            .apply {
                // A negative button is required unless device credential is offered.
                if (authenticators and DEVICE_CREDENTIAL == 0) setNegativeButtonText(cancelLabel)
            }
            .build()

        prompt.authenticate(info)
    }
}
