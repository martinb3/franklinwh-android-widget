package org.mbs3.franklinwh.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadingStoreTest {
    private val t0 = 1_760_000_000_000L

    @Test
    fun successStoresLatestAndClearsError() {
        val store = ReadingStore(FakeSharedPreferences())
        store.recordFailure(t0, "Unknown host")
        assertEquals("Unknown host", store.lastError)
        val r = reading(t0 + MIN, soc = 61.0)
        store.recordSuccess(r)
        assertEquals(r, store.latest)
        assertNull(store.lastError)
        assertEquals(t0 + MIN, store.lastAttemptMs)
    }

    @Test
    fun failureKeepsLatestReading() {
        val store = ReadingStore(FakeSharedPreferences())
        val r = reading(t0)
        store.recordSuccess(r)
        store.recordFailure(t0 + HOUR, "Timed out")
        assertEquals(r, store.latest)
        assertEquals(listOf(r), store.history)
        assertEquals("Timed out", store.lastError)
        assertEquals(t0 + HOUR, store.lastAttemptMs)
    }

    @Test
    fun historyDropsOldEntriesAndIsCapped() {
        val store = ReadingStore(FakeSharedPreferences())
        store.recordSuccess(reading(t0))
        store.recordSuccess(reading(t0 + 36 * HOUR))
        assertEquals(2, store.history.size)
        store.recordSuccess(reading(t0 + 36 * HOUR + 1))
        assertEquals(listOf(t0 + 36 * HOUR, t0 + 36 * HOUR + 1), store.history.map { it.timestampMs })

        val many = (0 until 250).map { reading(t0 + it * MIN) }
        val pruned = ReadingStore.pruneHistory(many, t0 + 249 * MIN)
        assertEquals(200, pruned.size)
        assertEquals(t0 + 50 * MIN, pruned.first().timestampMs)
    }

    @Test
    fun baselineIsNewestAtLeastFifteenMinutesOlder() {
        val store = ReadingStore(FakeSharedPreferences())
        listOf(0L, 10 * MIN, 30 * MIN, 44 * MIN).forEach { store.recordSuccess(reading(t0 + it)) }
        val latest = reading(t0 + 50 * MIN)
        store.recordSuccess(latest)
        assertEquals(t0 + 30 * MIN, store.baselineFor(latest)?.timestampMs)
        assertEquals(t0 + 30 * MIN, store.baselineFor(reading(t0 + 45 * MIN))?.timestampMs)
        assertNull(store.baselineFor(reading(t0 + 14 * MIN)))
    }

    @Test
    fun corruptPrefsReadAsEmpty() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("latest", "garbage").putString("history", "[{\"x\":1}]").commit()
        val store = ReadingStore(prefs)
        assertNull(store.latest)
        assertEquals(emptyList<GatewayReading>(), store.history)
    }
}
