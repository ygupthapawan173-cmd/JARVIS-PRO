package com.thanish.jarvispro

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object SecureKeyStore {
    private const val STORE = "jarvis_secure_settings"
    private const val PREF = "encrypted_gemini_key"
    private const val ALIAS = "jarvis_gemini_api_key_v1"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(
            ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build())
        return generator.generateKey()
    }

    fun save(context: Context, value: String) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val packed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
            .putString(PREF, Base64.encodeToString(packed, Base64.NO_WRAP)).apply()
    }

    fun load(context: Context): String? {
        val encoded = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).getString(PREF, null) ?: return null
        val packed = Base64.decode(encoded, Base64.NO_WRAP)
        require(packed.size > 12) { "Stored key data is invalid" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, packed.copyOfRange(0, 12)))
        return String(cipher.doFinal(packed.copyOfRange(12, packed.size)), Charsets.UTF_8)
    }
}
