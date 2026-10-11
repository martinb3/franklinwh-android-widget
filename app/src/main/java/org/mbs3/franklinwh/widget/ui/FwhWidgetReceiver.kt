package org.mbs3.franklinwh.widget.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.mbs3.franklinwh.widget.work.RefreshScheduler

/** Shared receiver behaviour: one refresh schedule serves all three widgets. */
abstract class FwhBaseReceiver : GlanceAppWidgetReceiver() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.ensurePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // onDisabled fires when the last widget of this kind is removed; the schedule must stay
        // while any widget of another kind remains.
        if (!anyWidgetPlaced(context)) RefreshScheduler.cancelAll(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Cheap with the UPDATE policy; covers app upgrades and restores.
        RefreshScheduler.ensurePeriodic(context)
    }

    companion object {
        private val RECEIVERS = listOf(
            StripWidgetReceiver::class.java,
            FwhWidgetReceiver::class.java,
            DashboardWidgetReceiver::class.java,
        )

        fun anyWidgetPlaced(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context)
            return RECEIVERS.any { manager.getAppWidgetIds(ComponentName(context, it)).isNotEmpty() }
        }
    }
}

class StripWidgetReceiver : FwhBaseReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StripWidget()
}

/** Receiver for the "Energy summary" widget; the original name, kept so placed widgets survive. */
class FwhWidgetReceiver : FwhBaseReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FwhWidget()
}

class DashboardWidgetReceiver : FwhBaseReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DashboardWidget()
}
