package org.mbs3.franklinwh.widget.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Which widget is rendering; each prefers its own layouts. */
enum class WidgetFamily { STRIP, SUMMARY, DASHBOARD }

/** How the battery charge bar is placed in the header. */
internal enum class Bar { NONE, INLINE, OWN_ROW }

/** How the tiles are arranged. */
internal enum class Tiles { TWO, ROW_OF_FOUR, GRID }

/** Strip (one-row) choices: how many readings to show and whether the age fits. */
internal data class StripMetrics(val readings: Int, val showAge: Boolean)

/** Per-size type and layout choices. This is the only place widget sizes are decided. */
internal data class Metrics(
    val percentSp: Int,
    val headerIconDp: Int,
    val valueSp: Int,
    val labelSp: Int,
    val iconDp: Int,
    val bar: Bar,
    val tiles: Tiles,
    /** Spread the header and tiles over the height with weighted spacers. */
    val spread: Boolean,
    /** Show solar and grid capacity bars in the live view (when capacities are set). */
    val capacityBars: Boolean,
    /** Non-null for the single-row strip layout instead of tiles. */
    val strip: StripMetrics? = null,
) {
    companion object {
        /** Card padding, also applied in Body. */
        val CARD_PADDING = 14.dp

        /*
         * Height cut-offs between families, from the Android widget sizing table for a typical
         * handset: n rows are about 118n-16 dp tall in portrait and 66n-15 dp in landscape
         * (1 row: 102/51, 2 rows: 220/117, 3 rows: 337/184). Each widget keeps its own layouts
         * across both orientations and only falls back when its size is clearly another family's,
         * which happens on pre-Android 12 launchers that ignore maxResize limits.
         */

        /** A strip at least this tall (2+ portrait rows) renders as a summary. */
        private val STRIP_FALLBACK_HEIGHT = 150.dp
        /** A summary shorter than this (1 portrait row) renders as a strip. */
        private val SUMMARY_MIN_HEIGHT = 110.dp
        /** A summary at least this tall (3+ portrait rows) renders as a dashboard. */
        private val SUMMARY_MAX_HEIGHT = 300.dp
        /** A dashboard shorter than this (2 landscape rows) renders as a summary. */
        private val DASHBOARD_MIN_HEIGHT = 160.dp

        /** At or above this width (4+ portrait cells) the wide variants are used. */
        private val WIDE_MIN_WIDTH = 250.dp
        /** A narrow strip shows the age text from this width (3 portrait cells). */
        private val NARROW_STRIP_AGE_MIN_WIDTH = 180.dp

        fun forSize(family: WidgetFamily, size: DpSize): Metrics = when (family) {
            WidgetFamily.STRIP ->
                if (size.height >= STRIP_FALLBACK_HEIGHT) forSize(WidgetFamily.SUMMARY, size) else strip(size)
            WidgetFamily.SUMMARY -> when {
                size.height < SUMMARY_MIN_HEIGHT -> strip(size)
                size.height >= SUMMARY_MAX_HEIGHT -> tiles(size, tall = true)
                else -> tiles(size, tall = false)
            }
            WidgetFamily.DASHBOARD ->
                if (size.height < DASHBOARD_MIN_HEIGHT) forSize(WidgetFamily.SUMMARY, size) else tiles(size, tall = true)
        }

        private fun strip(size: DpSize): Metrics {
            val narrow = size.width < WIDE_MIN_WIDTH
            return Metrics(
                percentSp = 20, headerIconDp = 18, valueSp = 13, labelSp = 10, iconDp = 16,
                bar = Bar.NONE, tiles = Tiles.TWO, spread = false, capacityBars = false,
                strip = StripMetrics(
                    readings = if (narrow) 2 else 4,
                    showAge = !narrow || size.width >= NARROW_STRIP_AGE_MIN_WIDTH,
                ),
            )
        }

        /** Tile layouts: compact/wide when not [tall], tall/large (grid, capacity bars) when [tall]. */
        private fun tiles(size: DpSize, tall: Boolean): Metrics {
            val wide = size.width >= WIDE_MIN_WIDTH
            val tiles = when {
                tall -> Tiles.GRID
                wide -> Tiles.ROW_OF_FOUR
                else -> Tiles.TWO
            }
            val columns = if (tiles == Tiles.ROW_OF_FOUR) 4 else 2
            // Value, label and icon sizes follow the width each tile gets, so values never ellipsize.
            val tileWidth = (size.width - CARD_PADDING * 2) / columns
            val (valueSp, labelSp, iconDp) = when {
                tileWidth >= 120.dp -> Triple(20, 12, 28)
                tileWidth >= 80.dp -> Triple(16, 11, 24)
                tileWidth >= 60.dp -> Triple(14, 10, 20)
                else -> Triple(12, 10, 20)
            }
            val (percentSp, headerIconDp, bar) = when {
                tall && wide -> Triple(36, 28, Bar.INLINE)
                tall -> Triple(32, 24, Bar.OWN_ROW)
                wide -> Triple(28, 22, Bar.INLINE)
                else -> Triple(22, 18, Bar.NONE)
            }
            return Metrics(percentSp, headerIconDp, valueSp, labelSp, iconDp, bar, tiles, spread = tall, capacityBars = tall)
        }
    }
}
