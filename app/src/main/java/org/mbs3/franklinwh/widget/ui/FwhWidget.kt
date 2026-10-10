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
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
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
            GlanceTheme {
                Body(appContext, state, now)
            }
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
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .appWidgetBackground()
            .background(GlanceTheme.colors.widgetBackground)
            .cornerRadius(16.dp)
            .padding(10.dp)
            .clickable(tap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (state) {
            WidgetState.Unconfigured -> Message("Tap to set up", null)
            is WidgetState.NoData -> Message("No data yet", state.lastError)
            is WidgetState.Ready -> Ready(context, state, nowMs, compact = LocalSize.current.width < FwhWidget.WIDE.width)
        }
    }
}

@Composable
private fun Message(title: String, detail: String?) {
    Text(title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Medium))
    if (detail != null) {
        Text(detail, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp), maxLines = 1)
    }
}

@Composable
private fun Ready(context: Context, state: WidgetState.Ready, nowMs: Long, compact: Boolean) {
    val r = state.reading
    val soc = DisplayFormat.soc(r.soc)
    val age = DisplayFormat.ageText(nowMs, r.timestampMs, DateFormat.is24HourFormat(context))
    val footer = if (state.refreshing) "$age \u00b7 refreshing\u2026" else age
    val rows = if (state.isLive) liveRows(r, compact) else longRows(r, state.averages, compact)
    if (compact) {
        Soc(soc)
        rows.forEach { (label, value) -> StatRow(label, value) }
        Footer(footer)
    } else {
        // Header row keeps the four stat rows within a 2-cell height.
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Soc(soc)
            Spacer(GlanceModifier.defaultWeight())
            Footer(footer)
        }
        rows.forEach { (label, value) -> StatRow(label, value) }
    }
}

@Composable
private fun Soc(text: String) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold))
}

@Composable
private fun Footer(text: String) {
    Text(text, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 10.sp), maxLines = 1)
}

private fun liveRows(r: GatewayReading, compact: Boolean): List<Pair<String, String>> {
    val rows = listOf(
        "Solar" to DisplayFormat.powerUnsigned(r.solarW),
        "Load" to DisplayFormat.powerUnsigned(r.loadW),
        "Grid" to DisplayFormat.powerSigned(r.gridW),
        "Battery" to DisplayFormat.powerSigned(r.batteryW),
    )
    return if (compact) rows.take(2) else rows
}

private fun longRows(r: GatewayReading, avg: PowerAverages?, compact: Boolean): List<Pair<String, String>> {
    fun withAvg(kwh: Double, avgW: Double?) =
        DisplayFormat.energy(kwh) + (avgW?.let { " (avg ${DisplayFormat.powerUnsigned(it)})" } ?: "")
    if (compact) {
        return listOf(
            "Solar" to DisplayFormat.energy(r.kwhSolar),
            "Load" to DisplayFormat.energy(r.kwhLoad),
        )
    }
    return listOf(
        "Solar" to withAvg(r.kwhSolar, avg?.solarW),
        "Load" to withAvg(r.kwhLoad, avg?.loadW),
        "Grid" to "in ${DisplayFormat.energy(r.kwhGridIn)} / out ${DisplayFormat.energy(r.kwhGridOut)}",
        "Battery" to "chg ${DisplayFormat.energy(r.kwhBatteryCharge)} / dis ${DisplayFormat.energy(r.kwhBatteryDischarge)}",
    )
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(GlanceModifier.fillMaxWidth()) {
        Text(
            label,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
            maxLines = 1,
        )
        Text(value, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp), maxLines = 1)
    }
}
