package org.mbs3.franklinwh.widget.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.mbs3.franklinwh.widget.ui.FwhBaseWidget

/** Redraws the widgets without fetching, so the live view switches to the daily view on time. */
class RedrawWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        FwhBaseWidget.redrawAll(applicationContext)
        return Result.success()
    }
}
