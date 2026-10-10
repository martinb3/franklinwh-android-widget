package org.mbs3.franklinwh.widget.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mbs3.franklinwh.widget.protocol.FrameException
import java.net.UnknownHostException

class GatewayRefresherTest {
    private val config = GatewayConfig("192.0.2.10", 9000)
    private val t0 = 1_760_000_000_000L

    @Test
    fun successIsStored() {
        val store = ReadingStore(FakeSharedPreferences())
        store.setRefreshing(t0)
        val result = GatewayRefresher.refresh(config, store, { t0 }) {
            JSONObject("""{"soc":55,"p_sun":1,"p_load":2,"p_uti":3}""")
        }
        assertTrue(result is GatewayRefresher.Result.Success)
        assertEquals(55.0, store.latest!!.soc, 0.0)
        assertEquals(0L, store.refreshingSinceMs)
    }

    @Test
    fun networkFailureKeepsLastGoodReading() {
        val store = ReadingStore(FakeSharedPreferences())
        val good = reading(t0)
        store.recordSuccess(good)
        store.setRefreshing(t0)
        val result = GatewayRefresher.refresh(config, store, { t0 + MIN }) { throw UnknownHostException("x.lan") }
        assertEquals(GatewayRefresher.Result.Failure("Unknown host"), result)
        assertEquals(good, store.latest)
        assertEquals("Unknown host", store.lastError)
        assertEquals(0L, store.refreshingSinceMs)
    }

    @Test
    fun parseFailuresAreRecordedWithoutDetailFromTheFrame() {
        val store = ReadingStore(FakeSharedPreferences())
        GatewayRefresher.refresh(config, store, { t0 }) { throw FrameException("bad SERIAL123") }
        assertEquals("Unreadable reply from gateway", store.lastError)
        GatewayRefresher.refresh(config, store, { t0 }) { JSONObject("{}") }
        assertEquals("Unexpected reply: missing field soc", store.lastError)
    }

    @Test
    fun unconfiguredDoesNotFetch() {
        val store = ReadingStore(FakeSharedPreferences())
        val result = GatewayRefresher.refresh(GatewayConfig("", 9000), store, { t0 }) { error("fetched") }
        assertEquals(GatewayRefresher.Result.NotConfigured, result)
    }
}
