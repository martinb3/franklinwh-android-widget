package org.mbs3.franklinwh.widget.protocol

import org.json.JSONException
import org.json.JSONObject
import java.util.zip.CRC32

/**
 * Wire-frame builder/parser for the gateway's local TCP protocol (port 9000).
 *
 * A frame is a single JSON object split into a cleartext header and an obfuscated continuation:
 * ```
 * {"cmdType":<int>,"equipNo":"<SN>",<obfuscated>
 * ```
 * The continuation decodes (see [Obfuscation]) to
 * ```
 * "type":<int>,"timeStamp":<unix s>,"snno":<seq>,"len":<n>,"crc":"<HEX>","dataArea":<json>}
 * ```
 * where `len` is the byte length of the compact `dataArea` JSON and `crc` is its CRC-32 in
 * uppercase hex. There is no length prefix or delimiter. Ported from franklinwh-local's `frame.py`.
 */
object Frame {
    const val DEFAULT_EQUIP_NO = "00000000"

    private val HEADER_RE = Regex("^\\{\"cmdType\":(\\d+),\"equipNo\":\"([^\"]+)\",")

    /**
     * CRC-32 (zlib) of [data] as uppercase hex. Like the reference implementation this is not
     * zero-padded, so a value below 0x10000000 has fewer than 8 digits.
     */
    fun crc32Hex(data: ByteArray): String {
        val crc = CRC32()
        crc.update(data)
        return java.lang.Long.toHexString(crc.value).uppercase()
    }

    /**
     * Builds an obfuscated request frame. [dataAreaJson] must already be compact JSON (for example
     * `{"opt":0}`); its exact bytes are what `len` and `crc` describe.
     */
    fun buildRequest(
        cmdType: Int,
        equipNo: String,
        dataAreaJson: String,
        type: Int = 0,
        snno: Int,
        timestamp: Long,
    ): ByteArray {
        val equip = equipNo.ifEmpty { DEFAULT_EQUIP_NO }
        val data = dataAreaJson.toByteArray(Charsets.UTF_8)
        val header = "{\"cmdType\":$cmdType,\"equipNo\":\"$equip\",".toByteArray(Charsets.UTF_8)
        val continuation = (
            "\"type\":$type,\"timeStamp\":$timestamp,\"snno\":$snno," +
                "\"len\":${data.size},\"crc\":\"${crc32Hex(data)}\",\"dataArea\":"
            ).toByteArray(Charsets.UTF_8) + data + '}'.code.toByte()
        return header + Obfuscation.encode(continuation, Obfuscation.deriveSecret(equip))
    }

    /**
     * Decodes one received frame. The secret is first derived from the serial in the cleartext
     * header; if that does not yield [Obfuscation.CONTINUATION_PREFIX], all 256 secrets are tried.
     *
     * @throws FrameException if the header is missing, no secret decodes the body, or the result
     *   is not a JSON object with an object `dataArea`.
     */
    fun parseResponse(bytes: ByteArray): Envelope {
        // ISO-8859-1 maps each byte to one char, so match offsets are byte offsets.
        val latin = String(bytes, Charsets.ISO_8859_1)
        val m = HEADER_RE.find(latin) ?: throw FrameException("frame missing cleartext cmdType/equipNo header")
        val cmdTypeText = m.groupValues[1]
        val equipRange = m.groups[2]!!.range
        val equipNo = String(bytes, equipRange.first, equipRange.last - equipRange.first + 1, Charsets.UTF_8)
        val headerEnd = m.range.last + 1
        val obf = bytes.copyOfRange(headerEnd, bytes.size)

        val prefix = Obfuscation.CONTINUATION_PREFIX.toByteArray(Charsets.UTF_8)
        var plain = Obfuscation.decode(obf, Obfuscation.deriveSecret(equipNo))
        if (!plain.startsWith(prefix)) {
            val s = Obfuscation.bruteForceSecret(obf)
                ?: throw FrameException("unable to decode obfuscated payload")
            plain = Obfuscation.decode(obf, s)
        }

        val full = bytes.copyOfRange(0, headerEnd) + plain
        val obj = try {
            JSONObject(String(full, Charsets.UTF_8))
        } catch (e: JSONException) {
            throw FrameException("decoded payload is not valid JSON: ${e.message}", e)
        }
        return try {
            Envelope(
                cmdType = cmdTypeText.toInt(),
                equipNo = equipNo,
                type = obj.getInt("type"),
                timeStamp = obj.getLong("timeStamp"),
                snno = obj.getInt("snno"),
                len = obj.getInt("len"),
                crc = obj.getString("crc"),
                dataArea = obj.getJSONObject("dataArea"),
            )
        } catch (e: JSONException) {
            throw FrameException("decoded frame is missing envelope fields: ${e.message}", e)
        } catch (e: NumberFormatException) {
            throw FrameException("cmdType out of range: $cmdTypeText", e)
        }
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        for (i in prefix.indices) if (this[i] != prefix[i]) return false
        return true
    }

    /** A decoded frame. */
    class Envelope(
        val cmdType: Int,
        val equipNo: String,
        val type: Int,
        val timeStamp: Long,
        val snno: Int,
        val len: Int,
        val crc: String,
        val dataArea: JSONObject,
    )
}
