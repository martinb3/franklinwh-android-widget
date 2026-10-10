package org.mbs3.franklinwh.widget.protocol

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.math.BigDecimal
import java.math.BigInteger

/** Checks the Kotlin port against frames produced by franklinwh-local (see tools/gen_golden.py). */
class GoldenFrameTest {

    private val cases: List<JSONObject> by lazy {
        val text = javaClass.classLoader!!.getResource("golden.json")!!.readText()
        val arr = JSONArray(text)
        List(arr.length()) { arr.getJSONObject(it) }
    }

    private fun case(name: String) = cases.single { it.getString("name") == name }

    private fun responseCases() = cases.filter {
        it.getString("name").startsWith("response_") && !it.optBoolean("expect_error", false)
    }

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }

    @Test
    fun fixturesArePresent() {
        assertEquals(5, cases.size)
        assertEquals(3, responseCases().size)
    }

    @Test
    fun requestBytesMatchFixture() {
        val c = case("request_device_detail_dummy_sn")
        val env = c.getJSONObject("envelope")
        val frame = Frame.buildRequest(
            cmdType = Commands.DEVICE_DETAIL_INFO,
            equipNo = c.getString("equip_no"),
            dataAreaJson = "{\"opt\":0}",
            type = env.getInt("type"),
            snno = env.getInt("snno"),
            timestamp = env.getLong("timeStamp"),
        )
        assertArrayEquals(hex(c.getString("frame_hex")), frame)
    }

    @Test
    fun requestFixtureDecodesToEnvelope() {
        val c = case("request_device_detail_dummy_sn")
        assertEnvelope(c.getJSONObject("envelope"), Frame.parseResponse(hex(c.getString("frame_hex"))))
    }

    @Test
    fun responseFixturesDecodeToExpectedEnvelope() {
        for (c in responseCases()) {
            val actual = Frame.parseResponse(hex(c.getString("frame_hex")))
            assertEnvelope(c.getJSONObject("envelope"), actual, c.getString("name"))
        }
    }

    @Test
    fun deriveSecretMatchesFixture() {
        for (c in cases) {
            if (c.isNull("secret")) continue
            assertEquals(c.getString("name"), c.getInt("secret"), Obfuscation.deriveSecret(c.getString("equip_no")))
        }
    }

    @Test
    fun bruteForceRecoversFixtureSecret() {
        for (c in cases) {
            if (c.isNull("secret")) continue
            val frame = hex(c.getString("frame_hex"))
            val headerLen = "{\"cmdType\":${c.getJSONObject("envelope").getInt("cmdType")}," +
                "\"equipNo\":\"${c.getString("equip_no")}\","
            val obf = frame.copyOfRange(headerLen.toByteArray(Charsets.UTF_8).size, frame.size)
            assertEquals(c.getString("name"), c.getInt("secret"), Obfuscation.bruteForceSecret(obf))
        }
    }

    @Test
    fun responseWithMismatchedHeaderSerialFallsBackToBruteForce() {
        // Re-label a real-shaped reply with a different serial so the derived secret is wrong.
        val c = case("response_device_detail_sn_10060010A00X00000000")
        val frame = hex(c.getString("frame_hex"))
        val oldHeader = "{\"cmdType\":1302,\"equipNo\":\"10060010A00X00000000\",".toByteArray(Charsets.UTF_8)
        val newHeader = "{\"cmdType\":1302,\"equipNo\":\"00000000\",".toByteArray(Charsets.UTF_8)
        assertArrayEquals(oldHeader, frame.copyOfRange(0, oldHeader.size))
        val relabelled = newHeader + frame.copyOfRange(oldHeader.size, frame.size)
        assertTrue(Obfuscation.deriveSecret("00000000") != c.getInt("secret"))

        val env = Frame.parseResponse(relabelled)
        assertEquals("00000000", env.equipNo)
        assertJsonEquals(c.getJSONObject("envelope").getJSONObject("dataArea"), env.dataArea, "dataArea")
    }

    @Test
    fun undecodableFixtureThrows() {
        val c = case("response_undecodable")
        assertTrue(c.getBoolean("expect_error"))
        val frame = hex(c.getString("frame_hex"))
        val header = "{\"cmdType\":1302,\"equipNo\":\"00000000\",".toByteArray(Charsets.UTF_8)
        assertNull(Obfuscation.bruteForceSecret(frame.copyOfRange(header.size, frame.size)))
        try {
            Frame.parseResponse(frame)
            fail("expected FrameException")
        } catch (_: FrameException) {
        }
    }

    @Test(expected = FrameException::class)
    fun missingHeaderThrows() {
        Frame.parseResponse("not a frame".toByteArray())
    }

    private fun assertEnvelope(expected: JSONObject, actual: Frame.Envelope, label: String = "") {
        assertEquals("$label cmdType", expected.getInt("cmdType"), actual.cmdType)
        assertEquals("$label equipNo", expected.getString("equipNo"), actual.equipNo)
        assertEquals("$label type", expected.getInt("type"), actual.type)
        assertEquals("$label timeStamp", expected.getLong("timeStamp"), actual.timeStamp)
        assertEquals("$label snno", expected.getInt("snno"), actual.snno)
        assertEquals("$label len", expected.getInt("len"), actual.len)
        assertEquals("$label crc", expected.getString("crc"), actual.crc)
        assertJsonEquals(expected.getJSONObject("dataArea"), actual.dataArea, "$label dataArea")
    }

    /** Structural comparison; integers must stay integral and doubles must match bit for bit. */
    private fun assertJsonEquals(expected: Any?, actual: Any?, path: String) {
        when (expected) {
            is JSONObject -> {
                assertTrue("$path is not an object", actual is JSONObject)
                actual as JSONObject
                val keys = expected.keys().asSequence().toSet()
                assertEquals("$path keys", keys, actual.keys().asSequence().toSet())
                for (k in keys) assertJsonEquals(expected.get(k), actual.get(k), "$path.$k")
            }
            is JSONArray -> {
                assertTrue("$path is not an array", actual is JSONArray)
                actual as JSONArray
                assertEquals("$path length", expected.length(), actual.length())
                for (i in 0 until expected.length()) assertJsonEquals(expected.get(i), actual.get(i), "$path[$i]")
            }
            is Int, is Long, is BigInteger -> {
                assertTrue("$path should be integral, was $actual", actual is Int || actual is Long || actual is BigInteger)
                assertEquals(path, (expected as Number).toLong(), (actual as Number).toLong())
            }
            is Double, is Float, is BigDecimal -> {
                assertNotNull(path, actual)
                assertFalse("$path should be fractional, was integral $actual", actual is Int || actual is Long)
                val e = (expected as Number).toDouble()
                val a = (actual as Number).toDouble()
                assertEquals(path, java.lang.Double.doubleToLongBits(e), java.lang.Double.doubleToLongBits(a))
            }
            else -> assertEquals(path, expected, actual)
        }
    }
}
