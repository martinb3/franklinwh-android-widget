package org.mbs3.franklinwh.widget.ui

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.updateAll
import org.mbs3.franklinwh.widget.data.GatewayRefresher
import org.mbs3.franklinwh.widget.data.ReadingStore

/** Widget tap: show the refreshing indicator, then fetch a new reading. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        requestRefresh(context)
    }

    companion object {
        suspend fun requestRefresh(context: Context) {
            ReadingStore(context).setRefreshing(System.currentTimeMillis())
            FwhWidget().updateAll(context)
            GatewayRefresher.refreshOnce(context)
            FwhWidget().updateAll(context)
        }
    }
}
