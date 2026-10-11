package org.mbs3.franklinwh.widget.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sizes are real widget sizes from the Android sizing table for a typical handset:
 * width 73n-16 dp (portrait), height 118n-16 dp portrait / 66n-15 dp landscape.
 */
class MetricsTest {
    private fun size(w: Int, h: Int) = DpSize(w.dp, h.dp)

    private fun Metrics.isStrip() = strip != null
    private fun Metrics.isSummary() = strip == null && tiles != Tiles.GRID
    private fun Metrics.isDashboard() = tiles == Tiles.GRID

    @Test
    fun stripInRange() {
        val wide = Metrics.forSize(WidgetFamily.STRIP, size(276, 102)) // 4x1 portrait
        assertEquals(StripMetrics(readings = 4, showAge = true), wide.strip)
        val landscape = Metrics.forSize(WidgetFamily.STRIP, size(554, 51)) // 4x1 landscape
        assertEquals(4, landscape.strip!!.readings)
        assertEquals(StripMetrics(2, showAge = true), Metrics.forSize(WidgetFamily.STRIP, size(203, 102)).strip)
        assertEquals(StripMetrics(2, showAge = false), Metrics.forSize(WidgetFamily.STRIP, size(130, 102)).strip)
    }

    @Test
    fun stripOutOfRangeFallsBack() {
        assertTrue(Metrics.forSize(WidgetFamily.STRIP, size(276, 220)).isSummary()) // 4x2 portrait
        assertTrue(Metrics.forSize(WidgetFamily.STRIP, size(276, 337)).isDashboard()) // 4x3 portrait
    }

    @Test
    fun summaryInRange() {
        val wide = Metrics.forSize(WidgetFamily.SUMMARY, size(276, 220)) // 4x2 portrait
        assertEquals(Tiles.ROW_OF_FOUR, wide.tiles)
        assertEquals(Bar.INLINE, wide.bar)
        assertFalse(wide.capacityBars)
        assertEquals(Tiles.ROW_OF_FOUR, Metrics.forSize(WidgetFamily.SUMMARY, size(554, 117)).tiles) // landscape
        val compact = Metrics.forSize(WidgetFamily.SUMMARY, size(130, 220)) // 2x2 portrait
        assertEquals(Tiles.TWO, compact.tiles)
        assertEquals(Bar.NONE, compact.bar)
    }

    @Test
    fun summaryOutOfRangeFallsBack() {
        assertTrue(Metrics.forSize(WidgetFamily.SUMMARY, size(276, 102)).isStrip()) // 4x1 portrait
        assertTrue(Metrics.forSize(WidgetFamily.SUMMARY, size(276, 337)).isDashboard()) // 4x3 portrait
    }

    @Test
    fun dashboardInRange() {
        val tall = Metrics.forSize(WidgetFamily.DASHBOARD, size(203, 337)) // 3x3 portrait
        assertEquals(Tiles.GRID, tall.tiles)
        assertEquals(Bar.OWN_ROW, tall.bar)
        assertTrue(tall.capacityBars)
        val large = Metrics.forSize(WidgetFamily.DASHBOARD, size(301, 464))
        assertEquals(Bar.INLINE, large.bar)
        assertEquals(20, large.valueSp) // (301 - 28) / 2 = 136 dp per tile
        assertTrue(Metrics.forSize(WidgetFamily.DASHBOARD, size(412, 184)).isDashboard()) // 3x3 landscape
    }

    @Test
    fun dashboardOutOfRangeFallsBack() {
        assertTrue(Metrics.forSize(WidgetFamily.DASHBOARD, size(269, 117)).isSummary()) // 2 rows landscape
        assertTrue(Metrics.forSize(WidgetFamily.DASHBOARD, size(276, 102)).isStrip()) // 1 row portrait
    }

    @Test
    fun tileTypeFollowsTileWidth() {
        assertEquals(14, Metrics.forSize(WidgetFamily.SUMMARY, size(301, 220)).valueSp) // 68 dp tiles
        assertEquals(14, Metrics.forSize(WidgetFamily.SUMMARY, size(276, 220)).valueSp) // 62 dp tiles
        assertEquals(12, Metrics.forSize(WidgetFamily.SUMMARY, size(250, 220)).valueSp) // 55.5 dp tiles
        assertEquals(16, Metrics.forSize(WidgetFamily.DASHBOARD, size(203, 337)).valueSp) // 87.5 dp tiles
        assertNull(Metrics.forSize(WidgetFamily.SUMMARY, size(276, 220)).strip)
        assertNotNull(Metrics.forSize(WidgetFamily.STRIP, size(349, 102)).strip)
    }
}
