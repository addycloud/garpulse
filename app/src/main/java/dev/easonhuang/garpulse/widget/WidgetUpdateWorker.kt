package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/** Periodically re-renders every home-screen widget with fresh Health Connect data. */
class WidgetUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        WidgetCatalog.placed(applicationContext).forEach { placed ->
            val widget = WidgetCatalog.widgetFor(placed.provider)
            ConfigurableWidget.refreshData(
                applicationContext,
                placed.glanceId,
                WidgetCatalog.defaultsFor(placed.provider),
            )
            widget.update(applicationContext, placed.glanceId)
        }
        Result.success()
    }.getOrElse { Result.retry() }
}
