// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the SiYuan access authorization code encrypted with an AES-256/GCM key
 * held in the Android Keystore, so the raw code never touches disk.
 *
 * The key is created with `setUserAuthenticationRequired(false)` on purpose:
 * background entry points (share target, widget, reply notification) must be
 * able to authenticate against the server without a foreground unlock. The
 * user-facing gate is the optional biometric app lock, not this key.
 */
@Singleton
class SecretStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun readAuthCode(): String? {
        val stored = prefs.getString(KEY_AUTH_CODE, null) ?: return null
        return runCatching { decrypt(stored) }
            .onFailure { Log.w(TAG, "Could not decrypt the stored auth code, clearing it", it) }
            .getOrElse {
                prefs.edit().remove(KEY_AUTH_CODE).apply()
                null
            }
    }

    fun writeAuthCode(code: String) {
        prefs.edit().putString(KEY_AUTH_CODE, encrypt(code)).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun hasAuthCode(): Boolean = prefs.contains(KEY_AUTH_CODE)

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val payload = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        // iv length | iv | ciphertext, so the IV travels with the payload.
        val combined = ByteArray(1 + iv.size + payload.size)
        combined[0] = iv.size.toByte()
        System.arraycopy(iv, 0, combined, 1, iv.size)
        System.arraycopy(payload, 0, combined, 1 + iv.size, payload.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun decrypt(encoded: String): String {
        val combined = Base64.decode(encoded, Base64.NO_WRAP)
        val ivSize = combined[0].toInt()
        val iv = combined.copyOfRange(1, 1 + ivSize)
        val payload = combined.copyOfRange(1 + ivSize, combined.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(payload), Charsets.UTF_8)
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val TAG = "SecretStore"
        const val PREFS_NAME = "siyuan_remote_secrets"
        const val KEY_AUTH_CODE = "access_auth_code"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "siyuan_remote_secret_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
    }
}
