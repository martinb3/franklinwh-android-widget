package org.mbs3.franklinwh.widget.ui

import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ColorFilter
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.mbs3.franklinwh.widget.R
import org.mbs3.franklinwh.widget.data.CapacityBars
import org.mbs3.franklinwh.widget.data.DisplayFormat
import org.mbs3.franklinwh.widget.data.GatewayConfig
import org.mbs3.franklinwh.widget.data.GatewayReading
import org.mbs3.franklinwh.widget.data.PowerAverages
import org.mbs3.franklinwh.widget.data.ReadingStore
import org.mbs3.franklinwh.widget.data.WidgetState
import org.mbs3.franklinwh.widget.data.WidgetStateFactory

class FwhWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE, TALL, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appContext = context.applicationContext
        val store = ReadingStore(appContext)
        provideContent {
            // A running Glance session only recomposes when its Glance state changes, and our
            // data lives in ReadingStore, not Glance state. redrawAll() bumps this key so each
            // update produces a new state value; reading it here makes that dependency explicit.
            // Without it, update() re-sends the previous RemoteViews. Do not remove.
            currentState(GENERATION_KEY)
            val now = System.currentTimeMillis()
            val config = GatewayConfig.load(appContext)
            val state = WidgetStateFactory.build(store, config, now)
            Body(appContext, state, config, now)
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)
        val TALL = DpSize(110.dp, 220.dp)
        val LARGE = DpSize(250.dp, 220.dp)

        /** See provideGlance: changing this forces running sessions to recompose. */
        private val GENERATION_KEY = longPreferencesKey("generation")

        /** Redraws every placed widget from the current ReadingStore contents. Use instead of updateAll(). */
        suspend fun redrawAll(context: Context) {
            val widget = FwhWidget()
            GlanceAppWidgetManager(context).getGlanceIds(FwhWidget::class.java).forEach { id ->
                updateAppWidgetState(context, id) { it[GENERATION_KEY] = (it[GENERATION_KEY] ?: 0L) + 1 }
                widget.update(context, id)
            }
        }
    }
}

@Composable
private fun Body(context: Context, state: WidgetState, config: GatewayConfig, nowMs: Long) {
    val tap: Action = when (state) {
        WidgetState.Unconfigured -> actionStartActivity(
            Intent(context, WidgetConfigActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        else -> actionRunCallback<RefreshAction>()
    }
    val metrics = Metrics.forSize(LocalSize.current)
    // Glance has no per-child alignment in a Box, so the card is a TopEnd Box holding a centred
    // Box for the content and, as its second child, the age text in the top-right corner.
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(WidgetPalette.card)
            .cornerRadius(20.dp)
            .padding(Metrics.CARD_PADDING)
            .clickable(tap),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val ready = state is WidgetState.Ready
            Column(
                modifier = if (ready && metrics.spread) GlanceModifier.fillMaxSize() else GlanceModifier.fillMaxWidth(),
                horizontalAlignment = if (ready) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                when (state) {
                    WidgetState.Unconfigured -> Message("Tap to set up", null)
                    is WidgetState.NoData -> Message("No data yet", state.lastError)
                    is WidgetState.Ready -> Ready(state, config, metrics)
                }
            }
        }
        if (state is WidgetState.Ready) {
            val age = DisplayFormat.ageText(nowMs, state.reading.timestampMs, DateFormat.is24HourFormat(context))
            Text(
                if (state.refreshing) "$age \u00b7 refreshing\u2026" else age,
                style = TextStyle(color = WidgetPalette.textSecondary, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Message(title: String, detail: String?) {
    Icon(R.drawable.ic_battery, WidgetPalette.iconNeutral, 28)
    Spacer(GlanceModifier.height(6.dp))
    Text(title, style = TextStyle(color = WidgetPalette.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium))
    if (detail != null) {
        Text(detail, style = TextStyle(color = WidgetPalette.textSecondary, fontSize = 11.sp), maxLines = 1)
    }
}

/** A tile shows a value and two label lines: a title, then an optional detail phrase. */
private class Tile(
    val icon: Int,
    val tint: ColorProvider,
    val value: String,
    val title: String,
    val detail: String? = null,
    /** Capacity bar fill (0..1), or null for no bar. */
    val bar: Float? = null,
)

/** How the battery charge bar is placed in the header. */
private enum class Bar { NONE, INLINE, OWN_ROW }

/** How the tiles are arranged. */
private enum class Tiles { TWO, ROW_OF_FOUR, GRID }

/** Per-size type and layout choices. This is the only place widget sizes are decided. */
private data class Metrics(
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
) {
    companion object {
        /** Card padding, also applied in Body. */
        val CARD_PADDING = 14.dp

        fun forSize(size: DpSize): Metrics {
            val wide = size.width >= FwhWidget.WIDE.width
            val tall = size.height >= FwhWidget.TALL.height
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

@Composable
private fun ColumnScope.Ready(state: WidgetState.Ready, config: GatewayConfig, m: Metrics) {
    val r = state.reading
    val direction = WidgetPalette.batteryColor(r.batteryW)

    @Composable
    fun ChargeBar(modifier: GlanceModifier) = LinearProgressIndicator(
        progress = (r.soc / 100.0).coerceIn(0.0, 1.0).toFloat(),
        modifier = modifier.height(6.dp).cornerRadius(3.dp),
        color = direction,
        backgroundColor = WidgetPalette.track,
    )

    // Glance only has equal weights, so a weight-2 gap is two weighted spacers. Outer gaps get
    // weight 2 and the middle gap weight 1, keeping header and tiles together in the upper part.
    if (m.spread) {
        Spacer(GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.defaultWeight())
    }
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val (glyph, glyphTint) = WidgetPalette.headerBatteryGlyph(r.batteryW)
        Icon(glyph, glyphTint, m.headerIconDp)
        Spacer(GlanceModifier.width(4.dp))
        Text(
            DisplayFormat.soc(r.soc),
            style = TextStyle(color = WidgetPalette.textPrimary, fontSize = m.percentSp.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        if (m.bar == Bar.INLINE) {
            Spacer(GlanceModifier.width(10.dp))
            ChargeBar(GlanceModifier.defaultWeight())
        }
    }
    if (m.bar == Bar.OWN_ROW) {
        Spacer(GlanceModifier.height(6.dp))
        ChargeBar(GlanceModifier.fillMaxWidth())
    }
    if (m.spread) Spacer(GlanceModifier.defaultWeight()) else Spacer(GlanceModifier.height(8.dp))

    val tiles = if (state.isLive) liveTiles(r, config, m.capacityBars) else dailyTiles(r, state.averages)
    // Reserve the bar slot in every tile only when some tile has a bar, so tiles stay aligned.
    val barSlot = tiles.any { it.bar != null }
    when (m.tiles) {
        Tiles.TWO -> TileRow(tiles.take(2), m, barSlot)
        Tiles.ROW_OF_FOUR -> TileRow(tiles, m, barSlot)
        Tiles.GRID -> {
            TileRow(tiles.take(2), m, barSlot)
            Spacer(GlanceModifier.height(12.dp))
            TileRow(tiles.drop(2), m, barSlot)
        }
    }
    if (m.spread) {
        Spacer(GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.defaultWeight())
    }
}

@Composable
private fun TileRow(tiles: List<Tile>, m: Metrics, barSlot: Boolean) {
    val labelStyle = TextStyle(color = WidgetPalette.textSecondary, fontSize = m.labelSp.sp)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        tiles.forEach { tile ->
            Column(GlanceModifier.defaultWeight()) {
                Icon(tile.icon, tile.tint, m.iconDp)
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    tile.value,
                    style = TextStyle(color = WidgetPalette.textPrimary, fontSize = m.valueSp.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                if (barSlot) {
                    Spacer(GlanceModifier.height(4.dp))
                    val bar = tile.bar
                    // Inset from the tile's end so bars in adjacent tiles do not touch.
                    Box(GlanceModifier.fillMaxWidth().height(4.dp).padding(end = 8.dp)) {
                        if (bar != null) {
                            LinearProgressIndicator(
                                progress = bar,
                                modifier = GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
                                color = tile.tint,
                                backgroundColor = WidgetPalette.track,
                            )
                        }
                    }
                    Spacer(GlanceModifier.height(4.dp))
                }
                Text(tile.title, style = labelStyle, maxLines = 1)
                // A blank line when there is no detail keeps tiles the same height in both views.
                Text(tile.detail ?: " ", style = labelStyle, maxLines = 1)
            }
        }
    }
}

private fun liveTiles(r: GatewayReading, config: GatewayConfig, bars: Boolean): List<Tile> = listOf(
    Tile(
        R.drawable.ic_sun, WidgetPalette.solar, DisplayFormat.powerUnsigned(r.solarW), "Solar",
        bar = if (bars) CapacityBars.solar(r.solarW, config.solarCapacityKw) else null,
    ),
    Tile(R.drawable.ic_house, WidgetPalette.load, DisplayFormat.powerUnsigned(r.loadW), "Load"),
    Tile(
        R.drawable.ic_pylon, WidgetPalette.grid, DisplayFormat.powerSigned(r.gridW), "Grid",
        bar = if (bars) CapacityBars.grid(r.gridW, config.gridLimitKw) else null,
    ),
    batteryTile(r, DisplayFormat.powerSigned(r.batteryW), "Battery"),
)

/** Label lines are capitalised; detail lines lead with the word, then the number ("Out 0.2 kWh"). */
private fun dailyTiles(r: GatewayReading, avg: PowerAverages?): List<Tile> = listOf(
    Tile(
        R.drawable.ic_sun, WidgetPalette.solar, DisplayFormat.energy(r.kwhSolar), "Solar",
        avg?.let { "Avg ${DisplayFormat.powerUnsigned(it.solarW)}" },
    ),
    Tile(
        R.drawable.ic_house, WidgetPalette.load, DisplayFormat.energy(r.kwhLoad), "Load",
        avg?.let { "Avg ${DisplayFormat.powerUnsigned(it.loadW)}" },
    ),
    Tile(
        R.drawable.ic_pylon, WidgetPalette.grid, DisplayFormat.energy(r.kwhGridIn), "Grid in",
        "Out ${DisplayFormat.energy(r.kwhGridOut)}",
    ),
    batteryTile(r, DisplayFormat.energy(r.kwhBatteryCharge), "Charged", "Used ${DisplayFormat.energy(r.kwhBatteryDischarge)}"),
)

private fun batteryTile(r: GatewayReading, value: String, title: String, detail: String? = null): Tile {
    val (glyph, tint) = WidgetPalette.tileBatteryGlyph(r.batteryW)
    return Tile(glyph, tint, value, title, detail)
}

@Composable
private fun Icon(resId: Int, tint: ColorProvider, sizeDp: Int) {
    Image(
        provider = ImageProvider(resId),
        contentDescription = null,
        modifier = GlanceModifier.size(sizeDp.dp),
        colorFilter = ColorFilter.tint(tint),
    )
}
