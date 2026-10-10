package org.mbs3.franklinwh.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetStateFactoryTest {
    private val t0 = 1_760_000_000_000L
    private val config = GatewayConfig("192.0.2.10", 9000)

    @Test
    fun unconfiguredWins() {
        val store = ReadingStore(FakeSharedPreferences()).apply { recordSuccess(reading(t0)) }
        assertEquals(WidgetState.Unconfigured, WidgetStateFactory.build(store, GatewayConfig("", 9000), t0))
        assertEquals(WidgetState.Unconfigured, WidgetStateFactory.build(store, GatewayConfig("h", 0), t0))
    }

    @Test
    fun noDataCarriesError() {
        val store = ReadingStore(FakeSharedPreferences())
        assertEquals(WidgetState.NoData(null), WidgetStateFactory.build(store, config, t0))
        store.recordFailure(t0, "Unknown host")
        assertEquals(WidgetState.NoData("Unknown host"), WidgetStateFactory.build(store, config, t0))
    }

    @Test
    fun liveSwitchesToLongAtTwoMinutes() {
        val store = ReadingStore(FakeSharedPreferences()).apply { recordSuccess(reading(t0)) }
        val live = WidgetStateFactory.build(store, config, t0 + 2 * MIN - 1) as WidgetState.Ready
        assertTrue(live.isLive)
        assertEquals(2 * MIN - 1, live.ageMs)
        val long = WidgetStateFactory.build(store, config, t0 + 2 * MIN) as WidgetState.Ready
        assertFalse(long.isLive)
    }

    @Test
    fun errorAfterSuccessPassesThrough() {
        val store = ReadingStore(FakeSharedPreferences()).apply { recordSuccess(reading(t0)) }
        store.recordFailure(t0 + HOUR, "Timed out")
        val s = WidgetStateFactory.build(store, config, t0 + HOUR) as WidgetState.Ready
        assertEquals("Timed out", s.lastError)
        assertFalse(s.isLive)
        store.recordSuccess(reading(t0 + 2 * HOUR))
        assertNull((WidgetStateFactory.build(store, config, t0 + 2 * HOUR) as WidgetState.Ready).lastError)
    }

    @Test
    fun averagesUseBaseline() {
        val store = ReadingStore(FakeSharedPreferences())
        store.recordSuccess(reading(t0, kwhSolar = 1.0))
        assertNull((WidgetStateFactory.build(store, config, t0) as WidgetState.Ready).averages)
        store.recordSuccess(reading(t0 + 30 * MIN, kwhSolar = 1.9))
        val s = WidgetStateFactory.build(store, config, t0 + 30 * MIN) as WidgetState.Ready
        assertEquals(1800.0, s.averages!!.solarW, 1e-6)
    }

    @Test
    fun refreshingExpires() {
        val store = ReadingStore(FakeSharedPreferences()).apply { recordSuccess(reading(t0)) }
        store.setRefreshing(t0)
        assertTrue((WidgetStateFactory.build(store, config, t0 + MIN) as WidgetState.Ready).refreshing)
        assertFalse((WidgetStateFactory.build(store, config, t0 + 3 * MIN) as WidgetState.Ready).refreshing)
        store.setRefreshing(null)
        assertFalse((WidgetStateFactory.build(store, config, t0 + MIN) as WidgetState.Ready).refreshing)
    }
}
