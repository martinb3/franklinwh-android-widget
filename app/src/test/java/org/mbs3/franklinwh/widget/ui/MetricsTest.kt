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
        // Pixel 9 Pro XL 4x1 portrait: four readings, age in the corner above the row.
        val wide = Metrics.forSize(WidgetFamily.STRIP, size(406, 107)).strip!!
        assertEquals(4, wide.readings)
        assertTrue(wide.showAge)
        assertFalse(wide.ageInRow)
        // Narrow strip: solar and load, age in the corner.
        val narrow = Metrics.forSize(WidgetFamily.STRIP, size(200, 107)).strip!!
        assertEquals(2, narrow.readings)
        assertTrue(narrow.showAge)
        // Landscape 4x1: short, so the age shares the row and must fit there.
        val landscape = Metrics.forSize(WidgetFamily.STRIP, size(554, 51)).strip!!
        assertEquals(4, landscape.readings)
        assertTrue(landscape.ageInRow)
        assertTrue(landscape.showAge)
    }

    @Test
    fun stripShrinksValuesToFitFourReadings() {
        // 13sp values would overflow 406 dp with four readings; the budget picks a smaller size.
        assertEquals(12, Metrics.forSize(WidgetFamily.STRIP, size(406, 107)).valueSp)
        assertEquals(13, Metrics.forSize(WidgetFamily.STRIP, size(554, 51)).valueSp)
    }

    @Test
    fun shortNarrowStripDropsAgeWhenItCannotFit() {
        val s = Metrics.forSize(WidgetFamily.STRIP, size(269, 51)).strip!! // 2x1 landscape
        assertTrue(s.ageInRow)
        assertEquals(4, s.readings)
        assertFalse(s.showAge)
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
        assertTrue(Metrics.forSize(WidgetFamily.DASHBOARD, size(300, 340)).isDashboard())
    }

    @Test
    fun dashboardFallsBackWhenGridDoesNotFit() {
        // Pixel 9 Pro XL 3x2 portrait: the grid needs about 293 dp, so use the summary row of four.
        val m = Metrics.forSize(WidgetFamily.DASHBOARD, size(300, 226))
        assertTrue(m.isSummary())
        assertEquals(Tiles.ROW_OF_FOUR, m.tiles)
        assertEquals(Tiles.TWO, Metrics.forSize(WidgetFamily.DASHBOARD, size(203, 226)).tiles)
        assertTrue(Metrics.forSize(WidgetFamily.DASHBOARD, size(412, 184)).isSummary()) // 3x3 landscape
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
