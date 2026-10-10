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
import org.mbs3.franklinwh.widget.data.DisplayFormat
import org.mbs3.franklinwh.widget.data.GatewayConfig
import org.mbs3.franklinwh.widget.data.GatewayReading
import org.mbs3.franklinwh.widget.data.PowerAverages
import org.mbs3.franklinwh.widget.data.ReadingStore
import org.mbs3.franklinwh.widget.data.WidgetState
import org.mbs3.franklinwh.widget.data.WidgetStateFactory

class FwhWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE))

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
            val state = WidgetStateFactory.build(store, GatewayConfig.load(appContext), now)
            Body(appContext, state, now)
        }
    }

    companion object {
        val COMPACT = DpSize(110.dp, 110.dp)
        val WIDE = DpSize(250.dp, 110.dp)

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
private fun Body(context: Context, state: WidgetState, nowMs: Long) {
    val tap: Action = when (state) {
        WidgetState.Unconfigured -> actionStartActivity(
            Intent(context, WidgetConfigActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        else -> actionRunCallback<RefreshAction>()
    }
    val compact = LocalSize.current.width < FwhWidget.WIDE.width
    // Glance has no per-child alignment in a Box, so the card is a TopEnd Box holding a centred
    // Box for the content and, as its second child, the age text in the top-right corner.
    Box(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(WidgetPalette.card)
            .cornerRadius(20.dp)
            .padding(14.dp)
            .clickable(tap),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = GlanceModifier.fillMaxWidth(),
                horizontalAlignment = if (state is WidgetState.Ready) Alignment.Start else Alignment.CenterHorizontally,
            ) {
                when (state) {
                    WidgetState.Unconfigured -> Message("Tap to set up", null)
                    is WidgetState.NoData -> Message("No data yet", state.lastError)
                    is WidgetState.Ready -> Ready(state, compact)
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
)

@Composable
private fun Ready(state: WidgetState.Ready, compact: Boolean) {
    val r = state.reading
    val direction = WidgetPalette.batteryColor(r.batteryW)

    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        val (glyph, glyphTint) = WidgetPalette.headerBatteryGlyph(r.batteryW)
        Icon(glyph, glyphTint, if (compact) 18 else 22)
        Spacer(GlanceModifier.width(4.dp))
        Text(
            DisplayFormat.soc(r.soc),
            style = TextStyle(
                color = WidgetPalette.textPrimary,
                fontSize = if (compact) 22.sp else 28.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        if (!compact) {
            Spacer(GlanceModifier.width(10.dp))
            LinearProgressIndicator(
                progress = (r.soc / 100.0).coerceIn(0.0, 1.0).toFloat(),
                modifier = GlanceModifier.defaultWeight().height(6.dp).cornerRadius(3.dp),
                color = direction,
                backgroundColor = WidgetPalette.track,
            )
        }
    }
    Spacer(GlanceModifier.height(8.dp))

    val tiles = if (state.isLive) liveTiles(r) else dailyTiles(r, state.averages)
    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        (if (compact) tiles.take(2) else tiles).forEach { tile ->
            Column(GlanceModifier.defaultWeight()) {
                Icon(tile.icon, tile.tint, 20)
                Spacer(GlanceModifier.height(2.dp))
                Text(
                    tile.value,
                    style = TextStyle(color = WidgetPalette.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1,
                )
                val labelStyle = TextStyle(color = WidgetPalette.textSecondary, fontSize = 10.sp)
                Text(tile.title, style = labelStyle, maxLines = 1)
                // A blank line when there is no detail keeps tiles the same height in both views.
                Text(tile.detail ?: " ", style = labelStyle, maxLines = 1)
            }
        }
    }
}

private fun liveTiles(r: GatewayReading): List<Tile> = listOf(
    Tile(R.drawable.ic_sun, WidgetPalette.solar, DisplayFormat.powerUnsigned(r.solarW), "Solar"),
    Tile(R.drawable.ic_house, WidgetPalette.load, DisplayFormat.powerUnsigned(r.loadW), "Load"),
    Tile(R.drawable.ic_pylon, WidgetPalette.grid, DisplayFormat.powerSigned(r.gridW), "Grid"),
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
