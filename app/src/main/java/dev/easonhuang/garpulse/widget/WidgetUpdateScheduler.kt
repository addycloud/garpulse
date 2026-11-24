package dev.easonhuang.garpulse.widget

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Widget refresh scheduling. Two mechanisms, both user-configurable (see the settings screen):
 *  - a periodic worker every [intervalMinutes] (WorkManager minimum is 15), and
 *  - optional fixed daily times ("08:00,22:00"): one 24-hour periodic worker per time, whose
 *    first run is delayed until the next occurrence. Delays are recomputed on every app start
 *    (UPDATE policy), so clock changes and reboots self-heal.
 */
object WidgetUpdateScheduler {
    private const val WORK_NAME = "garpulse_widget_refresh"
    private const val PREFS = "garpulse_widget_refresh"
    private const val LAST_REFRESH = "at"
    const val KEY_INTERVAL_MINUTES = "interval_minutes"
    const val KEY_FIXED_TIMES = "fixed_times"
    /** Do not re-read Health Connect more often than this when the app is brought forward. */
    private const val FOREGROUND_THROTTLE_MS = 90_000L

    fun intervalMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(KEY_INTERVAL_MINUTES, 30)

    fun fixedTimes(context: Context): List<LocalTime> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_FIXED_TIMES, "")?.split(',')?.mapNotNull {
                runCatching { LocalTime.parse(it.trim()) }.getOrNull()
            }.orEmpty()

    fun setSchedule(context: Context, intervalMinutes: Int, fixedTimes: List<LocalTime>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_INTERVAL_MINUTES, intervalMinutes.coerceAtLeast(15))
            .putString(KEY_FIXED_TIMES, fixedTimes.joinToString(",") { it.toString() })
            .apply()
        ensureScheduled(context)
    }

    fun ensureScheduled(context: Context) {
        val wm = WorkManager.getInstance(context)
        // Periodic interval (WorkManager's minimum periodic interval is 15 minutes).
        val interval = intervalMinutes(context).coerceAtLeast(15)
        wm.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WidgetUpdateWorker>(interval.toLong(), TimeUnit.MINUTES).build(),
        )
        // Fixed daily times: one 24h periodic worker per time, first run delayed to the next one.
        val times = fixedTimes(context)
        val currentWorkNames = times.map { fixedWorkName(it) }
        times.forEach { time ->
            val delayMinutes = java.time.Duration.between(LocalDateTime.now(), nextOccurrence(time)).toMinutes().coerceAtLeast(0)
            wm.enqueueUniquePeriodicWork(
                fixedWorkName(time),
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<WidgetUpdateWorker>(1, TimeUnit.DAYS)
                    .setInitialDelay(delayMinutes.toLong(), TimeUnit.MINUTES)
                    .build(),
            )
        }
        // Times that were removed from the config: cancel their workers.
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val known = prefs.getStringSet("known_fixed_works", emptySet()).orEmpty()
        (known - currentWorkNames.toSet()).forEach { wm.cancelUniqueWork(it) }
        prefs.edit().putStringSet("known_fixed_works", currentWorkNames.toSet()).apply()
    }

    private fun fixedWorkName(time: LocalTime): String =
        "garpulse_widget_fixed_${time.toString().replace(":", "_")}"

    private fun nextOccurrence(time: LocalTime): LocalDateTime {
        val now = LocalDateTime.now()
        var next = now.toLocalDate().atTime(time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next
    }

    /** Re-renders every widget now, e.g. after a setting that changes what they show. */
    fun refreshNow(context: Context) {
        val app = context.applicationContext
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(LAST_REFRESH, System.currentTimeMillis()).apply()
        enqueueNow(app)
    }

    /**
     * Called when the app returns to the foreground. Widgets otherwise only refresh on the
     * periodic worker, which can be delayed by Doze, so they can lag behind the app badly.
     */
    fun refreshIfStale(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val last = prefs.getLong(LAST_REFRESH, 0L)
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
