package org.mbs3.franklinwh.widget.ui

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import org.mbs3.franklinwh.widget.work.RefreshScheduler

class FwhWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FwhWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.ensurePeriodic(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshScheduler.cancelAll(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Cheap with the UPDATE policy; covers app upgrades and restores.
        RefreshScheduler.ensurePeriodic(context)
    }
}
