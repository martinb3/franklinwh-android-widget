package org.mbs3.franklinwh.widget.ui

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

/** Which widget is rendering; each prefers its own layouts. */
enum class WidgetFamily { STRIP, SUMMARY, DASHBOARD }

/** How the battery charge bar is placed in the header. */
internal enum class Bar { NONE, INLINE, OWN_ROW }

/** How the tiles are arranged. */
internal enum class Tiles { TWO, ROW_OF_FOUR, GRID }

/**
 * Strip (one-row) choices: how many readings, whether the age is shown, whether it sits at the end
 * of the row ([ageInRow]) or in the top-right corner above the row, and the gap after the header.
 */
internal data class StripMetrics(
    val readings: Int,
    val showAge: Boolean,
    val ageInRow: Boolean,
    val gapDp: Int,
)

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
         * Real sizes on a typical handset, from the Android widget sizing table: n rows are about
         * 118n-16 dp tall in portrait and 66n-15 dp in landscape (1 row: 102/51, 2 rows: 220/117,
         * 3 rows: 337/184); the emulator's Pixel 9 Pro XL measured 107 dp for 1 row and 226 dp for
         * 2 rows in portrait. Each widget keeps its own layouts and falls back only when its real
         * size clearly belongs to another family (pre-Android 12 launchers ignore maxResize limits)
         * or, for the grid, when the grid does not fit.
         */

        /** A strip at least this tall (2+ portrait rows) renders as a summary. */
        private val STRIP_FALLBACK_HEIGHT = 150.dp
        /** A summary shorter than this (1 portrait row) renders as a strip. */
        private val SUMMARY_MIN_HEIGHT = 110.dp
        /** A summary at least this tall (3+ portrait rows) renders as a dashboard if the grid fits. */
        private val SUMMARY_MAX_HEIGHT = 300.dp

        /** At or above this width (4+ portrait cells) the wide variants are used. */
        private val WIDE_MIN_WIDTH = 250.dp
        /** Strips at least this tall put the age in the corner instead of at the end of the row. */
        private val STRIP_AGE_CORNER_MIN_HEIGHT = 80.dp

        /*
         * Text width estimates in em (Roboto, font scale 1), used to fit the strip. Worst cases:
         * a value like "23.5 kWh" or "+12.3 kW", the percentage "100%" in bold, and an age like
         * "as of 12:58 PM" at 10sp. These are estimates, not measurements.
         */
        private const val VALUE_EM = 4.2f
        private const val PERCENT_EM = 2.7f
        private const val AGE_DP = 70f

        /** Line height as a multiple of the font size, for fitting the tile grid vertically. */
        private const val LINE = 1.3f

        fun forSize(family: WidgetFamily, size: DpSize): Metrics = when (family) {
            WidgetFamily.STRIP ->
                if (size.height >= STRIP_FALLBACK_HEIGHT) forSize(WidgetFamily.SUMMARY, size) else strip(size)
            WidgetFamily.SUMMARY -> when {
                size.height < SUMMARY_MIN_HEIGHT -> strip(size)
                size.height >= SUMMARY_MAX_HEIGHT -> gridOrSummary(size)
                else -> tiles(size, tall = false)
            }
            WidgetFamily.DASHBOARD ->
                if (size.height < SUMMARY_MIN_HEIGHT) strip(size) else gridOrSummary(size)
        }

        /** The tall grid layout if it fits the height, otherwise the summary (row of four or compact). */
        private fun gridOrSummary(size: DpSize): Metrics {
            val grid = tiles(size, tall = true)
            return if (grid.gridHeightDp() <= size.height.value) grid else tiles(size, tall = false)
        }

        /** Height the tall grid layout needs: padding, header, bar row, gaps and two tile rows. */
        private fun Metrics.gridHeightDp(): Float {
            val header = maxOf(headerIconDp.toFloat(), percentSp * LINE)
            val barRow = if (bar == Bar.OWN_ROW) 6f + 6f else 0f
            val capacityBar = if (capacityBars) 12f else 0f
            val tile = iconDp + 2f + valueSp * LINE + 2 * labelSp * LINE + capacityBar
            val minGap = 8f
            return CARD_PADDING.value * 2 + header + barRow + minGap + 2 * tile + 12f
        }

        private fun strip(size: DpSize): Metrics {
            val narrow = size.width < WIDE_MIN_WIDTH
            val readings = if (narrow) 2 else 4
            val ageInRow = size.height < STRIP_AGE_CORNER_MIN_HEIGHT
            val (percentSp, headerIconDp, gapDp) = if (narrow) Triple(18, 16, 8) else Triple(20, 18, 10)
            val inner = size.width.value - CARD_PADDING.value * 2
            val header = headerIconDp + 3 + PERCENT_EM * percentSp + gapDp

            fun groupWidth(valueSp: Int, iconDp: Int) = iconDp + 4 + VALUE_EM * valueSp
            fun fits(valueSp: Int, iconDp: Int, withAge: Boolean) =
                header + readings * groupWidth(valueSp, iconDp) + (if (withAge && ageInRow) AGE_DP else 0f) <= inner

            // Largest value size that fits all readings (and the age when it shares the row).
            val sizes = listOf(13 to 16, 12 to 14, 11 to 14)
            var choice = sizes.firstOrNull { (sp, icon) -> fits(sp, icon, withAge = true) }
            var showAge = true
            if (choice == null && ageInRow) {
                choice = sizes.firstOrNull { (sp, icon) -> fits(sp, icon, withAge = false) }
                showAge = false
            }
            val (valueSp, iconDp) = choice ?: sizes.last()
            return Metrics(
                percentSp = percentSp, headerIconDp = headerIconDp, valueSp = valueSp, labelSp = 10, iconDp = iconDp,
                bar = Bar.NONE, tiles = Tiles.TWO, spread = false, capacityBars = false,
                strip = StripMetrics(readings, showAge, ageInRow, gapDp),
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
