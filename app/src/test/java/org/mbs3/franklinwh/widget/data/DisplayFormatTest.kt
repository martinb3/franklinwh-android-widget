package org.mbs3.franklinwh.widget.data

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

class DisplayFormatTest {
    private val m = "−"

    @Test
    fun unsignedPower() {
        assertEquals("1.8 kW", DisplayFormat.powerUnsigned(1816.0))
        assertEquals("0.0 kW", DisplayFormat.powerUnsigned(99.9))
        assertEquals("0.1 kW", DisplayFormat.powerUnsigned(100.0))
        assertEquals("1.3 kW", DisplayFormat.powerUnsigned(1250.0))
        assertEquals("12.0 kW", DisplayFormat.powerUnsigned(11960.0))
        assertEquals("0.5 kW", DisplayFormat.powerUnsigned(-450.0))
    }

    @Test
    fun signedPower() {
        assertEquals("0.0 kW", DisplayFormat.powerSigned(-61.0))
        assertEquals("0.0 kW", DisplayFormat.powerSigned(99.0))
        assertEquals("0.0 kW", DisplayFormat.powerSigned(0.0))
        assertEquals("+2.3 kW", DisplayFormat.powerSigned(2340.0))
        assertEquals("${m}1.5 kW", DisplayFormat.powerSigned(-1500.0))
        assertEquals("+0.1 kW", DisplayFormat.powerSigned(100.0))
        assertEquals("${m}0.1 kW", DisplayFormat.powerSigned(-149.0))
    }

    @Test
    fun socAndEnergy() {
        assertEquals("43%", DisplayFormat.soc(42.9))
        assertEquals("100%", DisplayFormat.soc(99.6))
        assertEquals("10.3 kWh", DisplayFormat.energy(10.3))
        assertEquals("26.0 kWh", DisplayFormat.energy(25.96))
        assertEquals("0.0 kWh", DisplayFormat.energy(0.0))
    }

    @Test
    fun ageText() {
        val zone = ZoneId.of("America/Chicago")
        val then = ZonedDateTime.of(2026, 10, 10, 15, 7, 0, 0, zone).toInstant().toEpochMilli()
        fun age(deltaMs: Long, h24: Boolean = true) =
            DisplayFormat.ageText(then + deltaMs, then, h24, zone, Locale.US)
        assertEquals("just now", age(0))
        assertEquals("just now", age(59_999))
        assertEquals("just now", age(-5_000))
        assertEquals("1m ago", age(MIN))
        assertEquals("59m ago", age(HOUR - 1))
        assertEquals("as of 15:07", age(HOUR))
        assertEquals("as of 3:07 PM", age(3 * HOUR, h24 = false))
    }
}
