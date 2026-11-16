package org.mbs3.franklinwh.widget.data

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts small secrets at rest. Behind an interface so JVM tests can use a fake. */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray

    /** @throws GeneralSecurityException if [sealed] cannot be decrypted. */
    fun decrypt(sealed: ByteArray): ByteArray
}

/**
 * AES-256-GCM with a non-exportable key in `AndroidKeyStore`, created on first use. The output
 * is the 12-byte IV followed by the ciphertext and tag.
 */
class KeystoreCipher(private val alias: String = DEFAULT_ALIAS) : SecretCipher {

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.size <= IV_BYTES) throw GeneralSecurityException("sealed value too short")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "cloud-credentials"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}

/**
 * The cloud sign-in secrets: the MD5 hex of the password (never the password itself) and the
 * latest login token, both encrypted with [SecretCipher]. Kept in their own prefs file; app
 * backup is off (`allowBackup="false"`). Synchronous; call off the main thread where possible.
 */
class CloudCredentials(private val prefs: SharedPreferences, private val cipher: SecretCipher) {

    constructor(context: Context) : this(
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        KeystoreCipher(),
    )

    /** The stored password hash, or null if none is stored or it cannot be decrypted. */
    val passwordMd5: String?
        get() = read(KEY_HASH)

    /** The stored login token, or null if none is stored or it cannot be decrypted. */
    val token: String?
        get() = read(KEY_TOKEN)

    /** Stores a new password hash and drops the token, which belongs to the previous sign-in. */
    fun savePasswordMd5(md5Hex: String) {
        prefs.edit().putString(KEY_HASH, seal(md5Hex)).remove(KEY_TOKEN).commit()
    }

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, seal(token)).commit()
    }

    private fun seal(value: String): String =
        Base64.getEncoder().encodeToString(cipher.encrypt(value.toByteArray(Charsets.UTF_8)))

    private fun read(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            cipher.decrypt(Base64.getDecoder().decode(stored)).toString(Charsets.UTF_8)
        } catch (_: GeneralSecurityException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    companion object {
        const val PREFS_NAME = "cloud"
        private const val KEY_HASH = "passwordMd5"
        private const val KEY_TOKEN = "token"
    }
}
