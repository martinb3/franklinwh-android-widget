package org.mbs3.franklinwh.widget.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CapacityBarsTest {
    @Test
    fun nullWhenUnconfigured() {
        assertNull(CapacityBars.solar(1000.0, null))
        assertNull(CapacityBars.grid(1000.0, null))
    }

    @Test
    fun zeroAndMid() {
        assertEquals(0f, CapacityBars.solar(0.0, 10.0)!!, 0f)
        assertEquals(0.5f, CapacityBars.solar(5000.0, 10.0)!!, 1e-6f)
        assertEquals(0f, CapacityBars.grid(0.0, 48.0)!!, 0f)
        assertEquals(0.25f, CapacityBars.grid(12000.0, 48.0)!!, 1e-6f)
    }

    @Test
    fun overCapacityClampsToOne() {
        assertEquals(1f, CapacityBars.solar(12500.0, 10.0)!!, 0f)
        assertEquals(1f, CapacityBars.grid(60000.0, 48.0)!!, 0f)
    }

    @Test
    fun negativeValues() {
        // Exporting fills the grid bar by magnitude.
        assertEquals(0.25f, CapacityBars.grid(-12000.0, 48.0)!!, 1e-6f)
        assertEquals(1f, CapacityBars.grid(-90000.0, 48.0)!!, 0f)
        // Solar never shows a negative fill.
        assertEquals(0f, CapacityBars.solar(-20.0, 10.0)!!, 0f)
    }
}
