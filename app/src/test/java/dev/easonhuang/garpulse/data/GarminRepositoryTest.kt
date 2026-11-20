package dev.easonhuang.garpulse.data

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Base64

@RunWith(RobolectricTestRunner::class)
class GarminRepositoryTest {

    private val summaryJson = """
        {"data":[{
          "calendarDate":"2026-11-17",
          "movement":{
            "steps":{"goal":10000,"value":5201,"distanceInMeters":3858.0},
            "floorsAscended":{"goal":10,"value":3,"distanceInMeters":9.14}
          },
          "calories":{"burnedResting":1845,"burnedActive":103,"burnedTotal":1948},
          "heartRate":{"minValue":51,"maxValue":93,"restingValue":54},
          "intensityMinutes":{"goal":150,"moderate":1,"vigorous":2},
          "stress":{"avgLevel":9,"maxLevel":75},
          "bodyBattery":{"latestValue":73,"chargedValue":22,"drainedValue":12},
          "respiration":{"avgValue":15,"latestValue":14},
          "hydration":{"consumedInMl":1200}
        }]}
    """.trimIndent()

    @Test
    fun `daily summary maps every core Garmin field`() {
        val day = parseDailySummaries(summaryJson).single()
        assertEquals("2026-11-17", day.date)
        assertEquals(5201f, day.steps!!, 0f)
        assertEquals(3.858f, day.distanceKm!!, 0.0001f)
        assertEquals(3f, day.floors!!, 0f)
        assertEquals(103f, day.activeCalories!!, 0f)
        assertEquals(1948f, day.totalCalories!!, 0f)
        assertEquals(54f, day.restingHr!!, 0f)
        assertEquals(9f, day.stress!!, 0f)
        assertEquals(73f, day.bodyBattery!!, 0f)
        assertEquals(14f, day.respiration!!, 0f)
        // Garmin counts vigorous minutes double for intensity minutes.
        assertEquals(5f, day.intensityMinutes!!, 0f)
        assertEquals(1.2f, day.hydrationLiters!!, 0.0001f)
    }

    @Test
    fun `missing data and Garmin minus-one sentinel stay null`() {
        val json = """{"data":[{"calendarDate":"2026-11-13","stress":{"avgLevel":-1},"bodyBattery":{},"respiration":{}}]}"""
        val day = parseDailySummaries(json).single()
        assertNull(day.steps)
        assertNull(day.stress)
        assertNull(day.bodyBattery)
        assertNull(day.respiration)
    }

    @Test
    fun `sleep detail maps score and stages`() {
        val json = """{"calendarDate":"2026-11-19","sleepTimeSeconds":21420,"deepSleepSeconds":4980,
            "lightSleepSeconds":12480,"remSleepSeconds":3960,"averageRespirationValue":15.0,
            "sleepScores":{"overall":{"value":70}}}"""
        val sleep = parseDailySleep(json)!!
        assertEquals(5.95f, sleep.sleepHours!!, 0.001f)
        assertEquals(70f, sleep.sleepScore!!, 0f)
        assertEquals(4980f / 3600f, sleep.deepSleepHours!!, 0.0001f)
        assertEquals(15f, sleep.respiration!!, 0f)
    }

    @Test
    fun `sleep summaries skip empty days`() {
        val json = """[{"calendarDate":null,"sleepTimeSeconds":null},{"calendarDate":"2026-11-18","sleepTimeSeconds":25200}]"""
        val parsed = parseSleepSummaries(json)
        assertEquals(1, parsed.size)
        assertEquals(7f, parsed["2026-11-18"]!!, 0f)
    }

    @Test
    fun `HRV weight and heart rate parsers read the newest sample`() {
        val hrv = parseHrvSummaries("""{"hrvSummaries":[{"calendarDate":"2026-11-18","lastNightAvg":58}]}""")
        assertEquals(58f, hrv["2026-11-18"]!!, 0f)
        assertEquals(54f, parseDailyHrv("""{"hrvSummary":{"lastNightAvg":54}}""")!!, 0f)

        val weight = parseWeights("""{"dailyWeightSummaries":[{"summaryDate":"2026-11-19","latestWeight":{"weight":63700.0,"bodyFat":18.5}}]}""")
        assertEquals(63.7f, weight.single().weightKg!!, 0.0001f)
        assertEquals(18.5f, weight.single().bodyFat!!, 0f)

        val hr = parseHeartRate("""{"minHeartRate":51,"maxHeartRate":139,"heartRateValues":[[1,70],[2,72],[3,-1]]}""")
        assertEquals(72f, hr.first!!, 0f)
        assertEquals(51f, hr.second!!, 0f)
        assertEquals(139f, hr.third!!, 0f)
    }

    @Test
    fun `jwt expiry is decoded from the payload`() {
        val payload = Base64.getUrlEncoder().withoutPadding().encodeToString("""{"exp":1800000000}""".toByteArray())
        val jwt = "${Base64.getUrlEncoder().withoutPadding().encodeToString("header".toByteArray())}.$payload.signature"
        assertEquals(1_800_000_000L, jwtExpirySeconds(jwt))
        assertEquals(0L, jwtExpirySeconds("not-a-jwt"))
    }

    @Test
    fun `Garmin-only metrics never request Health Connect permissions`() {
        val manager = HealthConnectManager(ApplicationProvider.getApplicationContext())
        assertEquals(18, manager.metricPermissions.size)
        assertTrue(Metric.BODY_BATTERY.garminOnly)
        assertTrue(Metric.SLEEP_SCORE.garminOnly)
        assertFalse(Metric.STEPS.garminOnly)
    }
}
