package org.mbs3.franklinwh.widget.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class GatewayReadingTest {
    private val full = """
        {"soc":42.9,"fhpSoc":[41.5,44.3],"p_sun":1816,"p_load":2400,"p_uti":-61,"p_fhp":-1500,
         "p_gen":0,"kwh_sun":10.3,"kwh_load":26.0,"kwh_uti_in":19.0,"kwh_uti_out":0.1,
         "kwh_fhp_chg":3.4,"kwh_fhp_di":0.2,"kwh_gen":0.0,"other":"ignored"}
    """.trimIndent()

    @Test
    fun parsesAllFields() {
        val r = GatewayReading.fromDataArea(JSONObject(full), 1234L)
        assertEquals(1234L, r.timestampMs)
        assertEquals(42.9, r.soc, 0.0)
        assertEquals(listOf(41.5, 44.3), r.batterySocEach)
        assertEquals(1816.0, r.solarW, 0.0)
        assertEquals(2400.0, r.loadW, 0.0)
        assertEquals(-61.0, r.gridW, 0.0)
        assertEquals(-1500.0, r.batteryW, 0.0)
        assertEquals(10.3, r.kwhSolar, 0.0)
        assertEquals(26.0, r.kwhLoad, 0.0)
        assertEquals(19.0, r.kwhGridIn, 0.0)
        assertEquals(0.1, r.kwhGridOut, 0.0)
        assertEquals(3.4, r.kwhBatteryCharge, 0.0)
        assertEquals(0.2, r.kwhBatteryDischarge, 0.0)
    }

    @Test
    fun optionalFieldsDefaultToZero() {
        val r = GatewayReading.fromDataArea(JSONObject("""{"soc":10,"p_sun":0,"p_load":300,"p_uti":"300"}"""), 1L)
        assertEquals(300.0, r.gridW, 0.0)
        assertEquals(0.0, r.batteryW, 0.0)
        assertEquals(0.0, r.kwhSolar, 0.0)
        assertEquals(emptyList<Double>(), r.batterySocEach)
    }

    @Test
    fun missingRequiredFieldThrows() {
        for (key in listOf("soc", "p_sun", "p_load", "p_uti")) {
            val json = JSONObject(full).apply { remove(key) }
            val e = assertThrows(ReadingParseException::class.java) { GatewayReading.fromDataArea(json, 1L) }
            assertEquals("missing field $key", e.message)
        }
    }

    @Test
    fun unparsableFieldThrows() {
        val json = JSONObject(full).apply { put("p_sun", "lots") }
        assertThrows(ReadingParseException::class.java) { GatewayReading.fromDataArea(json, 1L) }
        val json2 = JSONObject(full).apply { put("kwh_sun", JSONObject()) }
        assertThrows(ReadingParseException::class.java) { GatewayReading.fromDataArea(json2, 1L) }
    }

    @Test
    fun jsonRoundTrip() {
        val r = GatewayReading.fromDataArea(JSONObject(full), 1_760_000_000_123L)
        assertEquals(r, GatewayReading.fromJsonString(r.toJson().toString()))
    }

    @Test
    fun corruptStoredJsonThrowsParseException() {
        assertThrows(ReadingParseException::class.java) { GatewayReading.fromJsonString("{nope") }
        assertThrows(ReadingParseException::class.java) { GatewayReading.fromJsonString("{}") }
    }
}
