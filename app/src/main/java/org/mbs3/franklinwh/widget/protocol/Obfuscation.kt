package org.mbs3.franklinwh.widget.protocol

/**
 * Per-byte obfuscation applied to the part of a wire frame after the cleartext header.
 *
 * ```
 * enc[i]   = (plain[i] + i + secret) & 0xFF
 * plain[i] = (enc[i]   - i - secret) & 0xFF
 * ```
 *
 * `i` is the offset within the obfuscated portion only (it starts at 0 right after the header).
 * Ported from franklinwh-local's `encryption.py`.
 */
object Obfuscation {
    /** Prefix every decoded continuation starts with; used to recognise a correct secret. */
    const val CONTINUATION_PREFIX = "\"type\":"

    /** `(sum of UTF-8 bytes + byte length + 29) & 0xFF`; 0 for an empty serial. */
    fun deriveSecret(equipNo: String): Int {
        if (equipNo.isEmpty()) return 0
        val raw = equipNo.toByteArray(Charsets.UTF_8)
        var sum = 0
        for (b in raw) sum += b.toInt() and 0xFF
        return (sum + raw.size + 29) and 0xFF
    }

    fun encode(plain: ByteArray, secret: Int): ByteArray = transform(plain, secret, +1)

    fun decode(encoded: ByteArray, secret: Int): ByteArray = transform(encoded, secret, -1)

    /**
     * Returns the secret byte whose decoding of [encoded] starts with [prefix], or null if none of
     * the 256 candidates matches.
     */
    fun bruteForceSecret(encoded: ByteArray, prefix: String = CONTINUATION_PREFIX): Int? {
        val expected = prefix.toByteArray(Charsets.UTF_8)
        val n = minOf(encoded.size, expected.size)
        if (n == 0) return null
        for (s in 0..255) {
            var ok = true
            for (i in 0 until n) {
                if (((encoded[i].toInt() - i - s) and 0xFF) != (expected[i].toInt() and 0xFF)) {
                    ok = false
                    break
                }
            }
            if (ok) return s
        }
        return null
    }

    private fun transform(input: ByteArray, secret: Int, sign: Int): ByteArray {
        val s = secret and 0xFF
        return ByteArray(input.size) { i -> ((input[i].toInt() + sign * (i + s)) and 0xFF).toByte() }
    }
}
