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
        assertEquals("0.2", DisplayFormat.kwhNumber(0.15))
    }

    private val zone = ZoneId.of("America/Chicago")

    private fun at(day: Int, hour: Int, minute: Int, month: Int = 10) =
        ZonedDateTime.of(2026, month, day, hour, minute, 0, 0, zone).toInstant().toEpochMilli()

    private fun age(nowMs: Long, thenMs: Long, h24: Boolean = true) =
        DisplayFormat.ageText(nowMs, thenMs, h24, zone, Locale.US)

    @Test
    fun ageTextIsRelativeOnlyInsideTheLiveWindow() {
        val then = at(10, 15, 7) // Saturday 10 Oct 2026
        assertEquals("just now", age(then, then))
        assertEquals("just now", age(then + 90_000, then))
        assertEquals("just now", age(then - 5_000, then))
        assertEquals("just now", age(then + 2 * MIN - 1, then))
        assertEquals("as of 15:07", age(then + 2 * MIN, then))
        assertEquals("as of 15:07", age(then + 3 * MIN, then))
    }

    @Test
    fun ageTextSameDay() {
        assertEquals("as of 08:05", age(at(10, 23, 59), at(10, 8, 5)))
        assertEquals("as of 8:05 AM", age(at(10, 23, 59), at(10, 8, 5), h24 = false))
    }

    @Test
    fun ageTextEarlierDays() {
        val now = at(10, 12, 0)
        assertEquals("as of Fri 14:05", age(now, at(9, 14, 5)))
        assertEquals("as of Mon 14:05", age(now, at(5, 14, 5)))
        assertEquals("as of Sun 14:05", age(now, at(4, 14, 5))) // 6 days
        assertEquals("as of 3 Oct 14:05", age(now, at(3, 14, 5))) // 7 days
        assertEquals("as of 30 Sep 2:05 PM", age(now, at(30, 14, 5, month = 9), h24 = false)) // 10 days
    }

    @Test
    fun ageTextYesterdayIsByCalendarDayInZone() {
        // 20 minutes apart but across local midnight: yesterday.
        assertEquals("as of Fri 23:50", age(at(10, 0, 10), at(9, 23, 50)))
        // 23 hours apart on the same local day: today.
        assertEquals("as of 00:30", age(at(10, 23, 30), at(10, 0, 30)))
        // The same instants seen from Tokyo fall on different local days.
        val tokyo = ZoneId.of("Asia/Tokyo")
        assertEquals(
            "as of Sat 14:30",
            DisplayFormat.ageText(at(10, 23, 30), at(10, 0, 30), true, tokyo, Locale.US),
        )
    }
}
