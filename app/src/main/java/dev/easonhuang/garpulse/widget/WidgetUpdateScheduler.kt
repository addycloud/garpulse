package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object WidgetUpdateScheduler {
    private const val WORK_NAME = "garpulse_widget_refresh"
    private const val LAST_REFRESH = "garpulse_last_widget_refresh"
    /** Do not re-read Health Connect more often than this when the app is brought forward. */
    private const val FOREGROUND_THROTTLE_MS = 90_000L

    fun ensureScheduled(context: Context) {
        // WorkManager's minimum periodic interval is 15 minutes.
        val request = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /** Re-renders every widget now, e.g. after a setting that changes what they show. */
    fun refreshNow(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(LAST_REFRESH, Context.MODE_PRIVATE)
            .edit()
            .putLong("at", System.currentTimeMillis())
            .apply()
        enqueueNow(app)
    }

    /**
     * Called when the app returns to the foreground. Widgets otherwise only refresh on the
     * periodic worker, which can be delayed by Doze, so they can lag behind the app badly.
     */
    fun refreshIfStale(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(LAST_REFRESH, Context.MODE_PRIVATE)
        val last = prefs.getLong("at", 0L)
        if (System.currentTimeMillis() - last >= FOREGROUND_THROTTLE_MS) enqueueNow(app)
    }

    private fun enqueueNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${WORK_NAME}_now",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build(),
        )
    }
}
