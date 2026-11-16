package org.mbs3.franklinwh.widget.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import org.mbs3.franklinwh.widget.data.GatewayConfig
import org.mbs3.franklinwh.widget.data.WidgetStateFactory
import java.util.concurrent.TimeUnit

object RefreshScheduler {
    private const val PERIODIC = "refresh-periodic"
    private const val NOW = "refresh-now"
    private const val REDRAW = "redraw"
    private const val PERIOD_MINUTES = 30L

    /**
     * The local gateway is on the LAN, so only try on an unmetered (normally Wi-Fi) network; the
     * cloud is reachable from any connected network.
     */
    private fun constraints(context: Context): Constraints {
        val network = when (GatewayConfig.load(context).source) {
            GatewayConfig.Source.LOCAL -> NetworkType.UNMETERED
            GatewayConfig.Source.CLOUD -> NetworkType.CONNECTED
        }
        return Constraints.Builder().setRequiredNetworkType(network).build()
    }

    fun ensurePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(PERIOD_MINUTES, TimeUnit.MINUTES)
            .setConstraints(constraints(context))
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /** Requests one refresh as soon as possible. A request already pending or running is kept. */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setConstraints(constraints(context))
            .setInputData(workDataOf(RefreshWorker.KEY_EXPEDITED to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    /** Redraws shortly after a fresh reading stops counting as live. */
    fun redrawWhenLiveExpires(context: Context) {
        val request = OneTimeWorkRequestBuilder<RedrawWorker>()
            .setInitialDelay(WidgetStateFactory.LIVE_MAX_AGE_MS + 5_000, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(REDRAW, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancelAll(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork(PERIODIC)
        wm.cancelUniqueWork(NOW)
        wm.cancelUniqueWork(REDRAW)
    }
}
