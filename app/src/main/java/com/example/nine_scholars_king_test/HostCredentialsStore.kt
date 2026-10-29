package com.example.nine_scholars_king_test

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/** User edits are encrypted locally; the chosen transport controls exposure to the test page. */
internal class HostCredentialsStore(context: Context) {
    private val preferences = context.getSharedPreferences("host_credentials", Context.MODE_PRIVATE)

    fun loadOrDefault(): HostCredentials = load() ?: ExamConfig.defaultCredentials

    fun load(): HostCredentials? {
        val blob = preferences.getString("encrypted", null) ?: return null
        val parts = blob.split(':', limit = 2)
        require(parts.size == 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, decode(parts[0])))
        val json = JSONObject(String(cipher.doFinal(decode(parts[1])), Charsets.UTF_8))
        return HostCredentials(json.getString("appId"), json.getString("appKey"))
    }

    fun save(credentials: HostCredentials) {
        val plaintext = JSONObject().put("appId", credentials.appId)
            .put("appKey", credentials.appKey).toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val blob = encode(cipher.iv) + ":" + encode(cipher.doFinal(plaintext))
        check(preferences.edit().putString("encrypted", blob).commit())
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    private fun encode(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun decode(text: String) = Base64.decode(text, Base64.NO_WRAP)

    private companion object {
        const val KEY_ALIAS = "exam-host-credentials-v1"
    }
}
