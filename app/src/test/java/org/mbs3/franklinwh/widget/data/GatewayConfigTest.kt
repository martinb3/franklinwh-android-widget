package org.mbs3.franklinwh.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayConfigTest {
    @Test
    fun roundTripWithoutCapacities() {
        val prefs = FakeSharedPreferences()
        GatewayConfig("192.0.2.10", 9000).save(prefs)
        assertEquals(GatewayConfig("192.0.2.10", 9000, null, null), GatewayConfig.load(prefs))
    }

    @Test
    fun roundTripWithCapacities() {
        val prefs = FakeSharedPreferences()
        val config = GatewayConfig("192.0.2.10", 9001, solarCapacityKw = 11.4, gridLimitKw = 48.0)
        config.save(prefs)
        assertEquals(config, GatewayConfig.load(prefs))
    }

    @Test
    fun clearingCapacitiesRemovesThem() {
        val prefs = FakeSharedPreferences()
        GatewayConfig("h", 9000, 11.4, 48.0).save(prefs)
        GatewayConfig("h", 9000).save(prefs)
        val loaded = GatewayConfig.load(prefs)
        assertNull(loaded.solarCapacityKw)
        assertNull(loaded.gridLimitKw)
    }

    @Test
    fun defaultsWhenEmpty() {
        assertEquals(GatewayConfig("", 9000), GatewayConfig.load(FakeSharedPreferences()))
    }

    @Test
    fun cloudSourceRoundTrip() {
        val prefs = FakeSharedPreferences()
        val config = GatewayConfig(
            "192.0.2.10", 9000, 11.4, null,
            source = GatewayConfig.Source.CLOUD,
            cloudAccount = "user@example.com",
            cloudGatewayId = "TESTSN0001",
        )
        config.save(prefs)
        assertEquals(config, GatewayConfig.load(prefs))
    }

    @Test
    fun oldPrefsDefaultToLocal() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("host", "192.0.2.10").putInt("port", 9000).commit()
        val loaded = GatewayConfig.load(prefs)
        assertEquals(GatewayConfig.Source.LOCAL, loaded.source)
        assertTrue(loaded.isConfigured)
        prefs.edit().putString("source", "SOMETHING_ELSE").commit()
        assertEquals(GatewayConfig.Source.LOCAL, GatewayConfig.load(prefs).source)
    }

    @Test
    fun isConfiguredChecksTheSelectedSource() {
        val local = GatewayConfig("192.0.2.10", 9000)
        val cloud = local.copy(source = GatewayConfig.Source.CLOUD)
        assertTrue(local.isConfigured)
        assertFalse(cloud.isConfigured)
        assertFalse(cloud.copy(cloudAccount = "user@example.com").isConfigured)
        assertTrue(cloud.copy(cloudAccount = "user@example.com", cloudGatewayId = "TESTSN0001").isConfigured)
        val cloudOnly = GatewayConfig("", 0, source = GatewayConfig.Source.CLOUD, cloudAccount = "a", cloudGatewayId = "b")
        assertTrue(cloudOnly.isConfigured)
        assertFalse(cloudOnly.copy(source = GatewayConfig.Source.LOCAL).isConfigured)
    }

    @Test
    fun parseCapacity() {
        assertNull(GatewayConfig.parseCapacity(""))
        assertNull(GatewayConfig.parseCapacity("  "))
        assertEquals(11.4, GatewayConfig.parseCapacity(" 11.4 ")!!, 0.0)
        for (bad in listOf("0", "-5", "abc", "NaN", "Infinity")) {
            assertThrows(IllegalArgumentException::class.java) { GatewayConfig.parseCapacity(bad) }
        }
    }
}
