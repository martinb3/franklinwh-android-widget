package org.mbs3.franklinwh.widget.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Opt-in check against a real gateway. Skipped unless the FWH_HOST environment variable is set
 * (FWH_PORT defaults to 9000) when Gradle runs the tests:
 * ```
 * FWH_HOST=192.0.2.10 ./gradlew :app:testDebugUnitTest --tests '*GatewayLiveTest*' --rerun
 * ```
 * Prints no serial numbers.
 */
class GatewayLiveTest {
    private val host = System.getProperty("fwh.host").orEmpty()
    private val port = System.getProperty("fwh.port").orEmpty().toIntOrNull() ?: 9000

    @Test
    fun deviceDetailRoundTrip() {
        assumeTrue("FWH_HOST not set; skipping live gateway test", host.isNotEmpty())
        val client = GatewayClient(host, port)

        val envelope = client.request(Commands.DEVICE_DETAIL_INFO, "{\"opt\":0}")
        assertEquals(Commands.DEVICE_DETAIL_INFO + 1, envelope.cmdType)
        assertTrue("reply equipNo should be the real serial", envelope.equipNo != Frame.DEFAULT_EQUIP_NO)

        val data = envelope.dataArea
        for (key in listOf("soc", "p_sun", "p_load", "p_uti", "kwh_sun", "kwh_load")) {
            assertTrue("dataArea missing $key", data.has(key))
        }
        val soc = data.getDouble("soc")
        assertTrue("soc out of range: $soc", soc in 0.0..100.0)
        println(
            "live gateway ok: soc=${"%.1f".format(soc)} p_sun=${data.getInt("p_sun")} " +
                "p_load=${data.getInt("p_load")} p_uti=${data.getInt("p_uti")} " +
                "snno=${envelope.snno} len=${envelope.len} crcDigits=${envelope.crc.length}",
        )
    }
}
