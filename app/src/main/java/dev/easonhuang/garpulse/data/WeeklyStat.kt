package dev.easonhuang.garpulse.data

/** This-week vs last-week comparison for one metric on the Insights screen. */
data class WeeklyStat(
    val metric: Metric,
    val perDay: List<SeriesPoint>,   // last 7 days, oldest → newest
    val thisWeekAvg: Float,
    val lastWeekAvg: Float,
    val goal: Float,
    val system: UnitSystem = UnitSystem.METRIC,  // perDay, averages and goal are all in this
    /** Which source supplied this metric under the current mode/priority. */
    val source: ValueSource = ValueSource.HEALTH_CONNECT,
) {
    /** 0f..1f progress of this week's daily average toward the goal. */
    val progress: Float get() = if (goal > 0f) (thisWeekAvg / goal).coerceIn(0f, 1f) else 0f

    /** Week-over-week change as a percentage, or null when there's no prior-week baseline. */
    val deltaPercent: Float? get() =
        if (lastWeekAvg > 0f) (thisWeekAvg - lastWeekAvg) / lastWeekAvg * 100f else null
}
