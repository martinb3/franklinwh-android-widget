package org.mbs3.franklinwh.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PowerAveragesTest {
    @Test
    fun averagesOverThirtyMinutes() {
        val a = reading(0, kwhSolar = 10.0, kwhLoad = 20.0, kwhGridIn = 5.0, kwhGridOut = 1.0,
            kwhBatteryCharge = 2.0, kwhBatteryDischarge = 3.0)
        // +0.9 kWh solar, +1.2 load, +0.6 in, +0.1 out, +0.2 charge, +0.0 discharge over 0.5 h.
        val b = reading(30 * MIN, kwhSolar = 10.9, kwhLoad = 21.2, kwhGridIn = 5.6, kwhGridOut = 1.1,
            kwhBatteryCharge = 2.2, kwhBatteryDischarge = 3.0)
        val avg = PowerAverages.between(a, b)!!
        assertEquals(1800.0, avg.solarW, 1e-6)
        assertEquals(2400.0, avg.loadW, 1e-6)
        assertEquals(1000.0, avg.gridW, 1e-6) // (0.6 - 0.1) / 0.5 h
        assertEquals(-400.0, avg.batteryW, 1e-6) // (0.0 - 0.2) / 0.5 h, net charging
    }

    @Test
    fun exportAndDischargeSigns() {
        val a = reading(0, kwhGridOut = 1.0, kwhBatteryDischarge = 1.0)
        val b = reading(2 * HOUR, kwhGridOut = 2.0, kwhBatteryDischarge = 4.0)
        val avg = PowerAverages.between(a, b)!!
        assertEquals(-500.0, avg.gridW, 1e-6)
        assertEquals(1500.0, avg.batteryW, 1e-6)
    }

    @Test
    fun exactlyFifteenMinutesIsAllowed() {
        val a = reading(0, kwhSolar = 1.0)
        val b = reading(15 * MIN, kwhSolar = 1.25)
        assertEquals(1000.0, PowerAverages.between(a, b)!!.solarW, 1e-6)
    }

    @Test
    fun shortWindowIsNull() {
        val a = reading(0, kwhSolar = 1.0)
        assertNull(PowerAverages.between(a, reading(15 * MIN - 1, kwhSolar = 1.3)))
        assertNull(PowerAverages.between(a, reading(0, kwhSolar = 1.3)))
        assertNull(PowerAverages.between(reading(HOUR), a))
    }

    @Test
    fun counterResetIsNull() {
        val a = reading(0, kwhSolar = 30.0, kwhLoad = 40.0)
        val b = reading(HOUR, kwhSolar = 30.5, kwhLoad = 0.3)
        assertNull(PowerAverages.between(a, b))
        assertNull(PowerAverages.between(reading(0, kwhGenerator = 1.0), reading(HOUR)))
    }
}
