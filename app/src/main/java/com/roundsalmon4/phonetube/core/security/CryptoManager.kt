package com.roundsalmon4.phonetube.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.roundsalmon4.phonetube.core.database.entity.IptvProvider
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts provider credentials at rest with an AES-GCM key held in the
 * Android Keystore, so the Room database never stores a plaintext login.
 * Values are kept as "enc:v1:<iv>:<ciphertext>" so an undecryptable value can
 * be recognized as legacy plaintext and read as-is.
 */
@Singleton
class CryptoManager @Inject constructor() {

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "phonetube_iptv_credentials"
        const val TRANSFORM = "AES/GCM/NoPadding"
        const val GCM_TAG_BITS = 128
        const val PREFIX = "enc:v1:"
    }

    private val key: SecretKey by lazy { getOrCreateKey() }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    /** Encrypts [plaintext] for storage, or returns it unchanged when empty. */
    fun encrypt(plaintext: String): String {
        if (plaintext.isEmpty()) return plaintext
        val cipher = Cipher.getInstance(TRANSFORM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val body = Base64.encodeToString(encrypted, Base64.NO_WRAP)
        return "$PREFIX$iv:$body"
    }

    /**
     * Decrypts a value produced by [encrypt]. Returns null when the value is
     * not in encrypted form, i.e. legacy plaintext or an empty placeholder.
     */
    fun decrypt(value: String): String? {
        if (!value.startsWith(PREFIX)) return null
        val payload = value.removePrefix(PREFIX)
        val separator = payload.indexOf(':')
        if (separator <= 0) return null
        val iv = Base64.decode(payload.substring(0, separator), Base64.NO_WRAP)
        val body = Base64.decode(payload.substring(separator + 1), Base64.NO_WRAP)
        return try {
            val cipher = Cipher.getInstance(TRANSFORM)
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(body), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}

/** Plaintext credentials for a stored provider, falling back to legacy values. */
fun IptvProvider.plaintextPassword(crypto: CryptoManager): String =
    crypto.decrypt(password) ?: password