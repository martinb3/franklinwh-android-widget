package org.mbs3.franklinwh.widget.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import org.mbs3.franklinwh.widget.R
import org.mbs3.franklinwh.widget.data.GatewayRefresher
import org.mbs3.franklinwh.widget.ui.FwhWidget

/** Fetches a reading, redraws the widgets, and schedules the redraw that ends the live view. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val result = GatewayRefresher.refreshOnce(applicationContext)
        FwhWidget.redrawAll(applicationContext)
        return when (result) {
            is GatewayRefresher.Result.Success -> {
                RefreshScheduler.redrawWhenLiveExpires(applicationContext)
                Result.success()
            }
            is GatewayRefresher.Result.Failure ->
                // The error is shown on the widget. A tap gets one retry; periodic work just waits.
                if (inputData.getBoolean(KEY_EXPEDITED, false) && runAttemptCount == 0) Result.retry()
                else Result.success()
            GatewayRefresher.Result.NotConfigured -> Result.success()
        }
    }

    /** Expedited work runs as a foreground service before Android 12, which needs a notification. */
    override suspend fun getForegroundInfo(): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.refresh_channel),
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        val notification = Notification.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_battery)
            .setContentTitle(applicationContext.getString(R.string.refresh_notification))
            .build()
        return ForegroundInfo(NOTIFICATION_ID, notification)
    }

    companion object {
        const val KEY_EXPEDITED = "expedited"
        private const val CHANNEL_ID = "refresh"
        private const val NOTIFICATION_ID = 1
    }
}
