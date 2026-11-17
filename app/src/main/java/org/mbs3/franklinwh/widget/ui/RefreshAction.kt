package org.mbs3.franklinwh.widget.ui

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import org.mbs3.franklinwh.widget.data.ReadingStore
import org.mbs3.franklinwh.widget.work.RefreshScheduler

/** Widget tap: show the refreshing indicator and hand the fetch to WorkManager. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        requestRefresh(context)
    }

    companion object {
        suspend fun requestRefresh(context: Context) {
            ReadingStore(context).setRefreshing(System.currentTimeMillis())
            FwhBaseWidget.redrawAll(context)
            RefreshScheduler.refreshNow(context)
            RefreshScheduler.redrawWhenRefreshingExpires(context)
        }
    }
}
