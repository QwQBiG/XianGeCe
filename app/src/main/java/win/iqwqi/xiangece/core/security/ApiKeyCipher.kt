package win.iqwqi.xiangece.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyCipher @Inject constructor() {
    private val alias = "xiangece_ai_key"
    private val keyStore by lazy {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }

    fun encrypt(plainText: String): String {
        if (plainText.isBlank()) return ""
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val payload = cipher.iv + cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(encoded: String): String {
        if (encoded.isBlank()) return ""
        return runCatching {
            val payload = Base64.decode(encoded, Base64.NO_WRAP)
            require(payload.size > 12)
            val iv = payload.copyOfRange(0, 12)
            val encrypted = payload.copyOfRange(12, payload.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8)
        }.getOrDefault("")
    }

    private fun getOrCreateKey(): SecretKey {
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }
}

