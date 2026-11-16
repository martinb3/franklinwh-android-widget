package org.mbs3.franklinwh.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.GeneralSecurityException

/** Reversible stand-in for [KeystoreCipher]: XOR with a fixed byte, plus a marker to check. */
class FakeCipher : SecretCipher {
    override fun encrypt(plain: ByteArray): ByteArray = byteArrayOf(MARK) + plain.map { (it.toInt() xor 0x5A).toByte() }

    override fun decrypt(sealed: ByteArray): ByteArray {
        if (sealed.isEmpty() || sealed[0] != MARK) throw GeneralSecurityException("not sealed by FakeCipher")
        return sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    private companion object {
        const val MARK: Byte = 0x7F
    }
}

class CloudCredentialsTest {
    private val hash = "5f4dcc3b5aa765d61d8327deb882cf99"

    @Test
    fun roundTripThroughCipher() {
        val prefs = FakeSharedPreferences()
        val creds = CloudCredentials(prefs, FakeCipher())
        creds.savePasswordMd5(hash)
        creds.saveToken("tok-123")

        val reloaded = CloudCredentials(prefs, FakeCipher())
        assertEquals(hash, reloaded.passwordMd5)
        assertEquals("tok-123", reloaded.token)
        val stored = prefs.all.values.joinToString()
        assertFalse(stored.contains(hash))
        assertFalse(stored.contains("tok-123"))
    }

    @Test
    fun emptyByDefault() {
        val creds = CloudCredentials(FakeSharedPreferences(), FakeCipher())
        assertNull(creds.passwordMd5)
        assertNull(creds.token)
    }

    @Test
    fun newPasswordDropsToken() {
        val creds = CloudCredentials(FakeSharedPreferences(), FakeCipher())
        creds.savePasswordMd5(hash)
        creds.saveToken("tok-123")
        creds.savePasswordMd5("0123456789abcdef0123456789abcdef")
        assertEquals("0123456789abcdef0123456789abcdef", creds.passwordMd5)
        assertNull(creds.token)
    }

    @Test
    fun undecryptableValuesReadAsMissing() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("passwordMd5", "not base64!").putString("token", "AAAA").commit()
        val creds = CloudCredentials(prefs, FakeCipher())
        assertNull(creds.passwordMd5)
        assertNull(creds.token)
    }
}
