package dev.easonhuang.garpulse.widget

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.mutablePreferencesOf
import dev.easonhuang.garpulse.data.Metric
import dev.easonhuang.garpulse.data.ValueSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetConfigTest {

    @Test
    fun `config survives a Glance preferences round trip`() {
        val original = WidgetConfig(
            metrics = listOf(Metric.STEPS, Metric.HEART_RATE, Metric.SLEEP),
            layout = WidgetLayout.MULTI,
            showChart = false,
            showLabels = false,
            showRefreshStrip = false,
            fontSizeSp = 34,
            fontScale = 1.3f,
            backgroundColor = Color(0xFF123456),
            backgroundOpacity = 45,
            cardColor = Color(0xFF654321),
            cardOpacity = 60,
            accentColor = Color(0xFFFF0000),
            textColor = Color(0xFF00FF00),
            tapPackage = "com.example.other",
            tapActivity = "com.example.other/.Launcher",
            tapTargets = mapOf(Metric.STEPS.key to TapTarget("com.garmin.connect", "com.garmin.connect/.Main")),
            imagePath = "/data/user/0/app/files/widget_images/a.jpg",
            imageOpacity = 35,
            showSource = true,
        )
        val prefs = mutablePreferencesOf()
        WidgetConfigCodec.write(prefs, original)

        assertEquals(original, WidgetConfigCodec.read(prefs, WidgetConfig.DEFAULT))
    }

    @Test
    fun `refresh strip defaults to on for old instances`() {
        assertTrue(WidgetConfigCodec.read(mutablePreferencesOf(), WidgetConfig.DEFAULT).showRefreshStrip)
    }

    @Test
    fun `empty metric list stays empty through Glance prefs round trip`() {
        // A photo-only widget deliberately has zero metrics. Saving must not resurrect STEPS.
        val original = WidgetConfig(metrics = emptyList(), imagePath = "/data/data/app/p.jpg")
        val prefs = mutablePreferencesOf()
        WidgetConfigCodec.write(prefs, original)
        val read = WidgetConfigCodec.read(prefs, WidgetConfig.DEFAULT)
        assertTrue(read.metrics.isEmpty())
        assertEquals(original.imagePath, read.imagePath)
    }

    @Test
    fun `writing a config clears legacy metric keys`() {
        // Otherwise a deliberately empty widget is rendered with a leftover "steps" snapshot.
        val prefs = mutablePreferencesOf(
            WidgetPrefs.LEGACY_METRIC to Metric.STEPS.key,
            WidgetPrefs.LEGACY_VALUE to "1,200 steps",
        )
        WidgetConfigCodec.write(prefs, WidgetConfig(metrics = emptyList()))
        assertTrue(WidgetConfigCodec.legacySnapshot(prefs, WidgetConfig.DEFAULT).isEmpty())
    }

    @Test
    fun `empty metric list stays empty through draft round trip`() {
        val original = WidgetConfig(metrics = emptyList(), imagePath = "/data/data/app/p.jpg")
        val decoded = WidgetConfigCodec.decodeDraft(WidgetConfigCodec.encodeDraft(original))
        assertTrue(decoded!!.metrics.isEmpty())
        assertEquals(original.imagePath, decoded.imagePath)
    }

    @Test
    fun `tap uri survives prefs round trip`() {
        val original = WidgetConfig(tapUri = "mijia://device?page=scale", tapTargets = mapOf("steps" to TapTarget(uri = "mijia://x")))
        val prefs = mutablePreferencesOf()
        WidgetConfigCodec.write(prefs, original)
        val read = WidgetConfigCodec.read(prefs, WidgetConfig.DEFAULT)
        assertEquals("mijia://device?page=scale", read.tapUri)
        assertEquals("mijia://x", read.tapFor("steps").uri)
        assertEquals(original, read)
    }

    @Test
    fun `tap targets stored without a uri field still decode`() {
        // Old snapshots stored three fields per target; a missing fourth must not break them.
        val raw = listOf("steps", "com.x.app", "com.x.app/.Main").joinToString('\u001D'.toString())
        val targets = WidgetConfigCodec.decodeTapTargets(raw)
        assertEquals("com.x.app", targets["steps"]?.packageName)
        assertEquals(null, targets["steps"]?.uri)
    }

    @Test
    fun `draft without tap uri field decodes with null uri`() {
        // A 19-field legacy draft has no 20th field; the URI must default to null, not crash.
        val legacy = WidgetConfig(metrics = listOf(Metric.STEPS), tapPackage = "com.x")
        val encoded = WidgetConfigCodec.encodeDraft(legacy).split('\u001F').dropLast(1).joinToString('\u001F'.toString())
        val decoded = WidgetConfigCodec.decodeDraft(encoded)
        assertEquals("com.x", decoded?.tapPackage)
        assertEquals(null, decoded?.tapUri)
    }

    @Test
    fun `blank tap package reads back as null`() {
        val config = WidgetConfig(tapPackage = "   ")
        val prefs = mutablePreferencesOf()
        WidgetConfigCodec.write(prefs, config)
        assertEquals(null, WidgetConfigCodec.read(prefs, WidgetConfig.DEFAULT).tapPackage)
    }

    @Test
    fun `snapshots round trip with points and units`() {
        val states = listOf(
            WidgetMetricState(Metric.STEPS.key, true, "8,432", "steps", listOf(100f, 200f, 150f)),
            WidgetMetricState(Metric.WEIGHT.key, false, "-", "", emptyList()),
        )
        val decoded = WidgetConfigCodec.decodeSnapshots(WidgetConfigCodec.encodeSnapshots(states))
        assertEquals(states, decoded)
    }

    @Test
    fun `snapshots preserve the concrete data source per metric`() {
        val states = listOf(
            WidgetMetricState(Metric.STEPS.key, true, "5,201", "steps", listOf(10f), source = ValueSource.GARMIN_API),
            WidgetMetricState(Metric.SLEEP.key, true, "7.4", "h", listOf(8f), source = ValueSource.HEALTH_CONNECT),
        )
        val decoded = WidgetConfigCodec.decodeSnapshots(WidgetConfigCodec.encodeSnapshots(states))
        assertEquals(ValueSource.GARMIN_API, decoded[0].source)
        assertEquals(ValueSource.HEALTH_CONNECT, decoded[1].source)
    }

    @Test
    fun `snapshots without a source field default to Health Connect`() {
        // Old snapshots stored five fields; a missing sixth must not break them. The codec uses
        // control characters (US = field, RS = record) as delimiters, not printable separators.
        val field = '\u001F'
        val raw = listOf("steps", "1", "8,432", "steps", "1,2,3").joinToString(field.toString())
        assertEquals(ValueSource.HEALTH_CONNECT, WidgetConfigCodec.decodeSnapshots(raw).single().source)
    }

    @Test
    fun `legacy single-metric state is still readable`() {
        val prefs = mutablePreferencesOf(
            WidgetPrefs.LEGACY_METRIC to Metric.STEPS.key,
            WidgetPrefs.LEGACY_VALUE to "1,200 steps",
            WidgetPrefs.LEGACY_POINTS to "1.0,2.0",
        )
        val snapshots = WidgetConfigCodec.legacySnapshot(prefs, WidgetConfig.DEFAULT)
        assertEquals(1, snapshots.size)
        assertEquals(Metric.STEPS.key, snapshots[0].metricKey)
        assertEquals(listOf(1f, 2f), snapshots[0].points)
        assertEquals(listOf(Metric.STEPS), WidgetConfigCodec.read(prefs, WidgetConfig.DEFAULT).metrics)
    }

    @Test
    fun `effective background applies opacity`() {
        val config = WidgetConfig(backgroundColor = Color(0xFF1E2433), backgroundOpacity = 50)
        assertEquals(0.5f, config.effectiveBackground.alpha, 0.01f)
        assertEquals(0x80, config.effectiveBackground.toArgbCompat() ushr 24)
        assertEquals(0x1E2433, config.effectiveBackground.toArgbCompat() and 0xFFFFFF)
    }

    @Test
    fun `card colour and opacity are independent of background`() {
        val config = WidgetConfig(
            backgroundColor = Color(0xFF111111),
            backgroundOpacity = 100,
            cardColor = Color(0xFFFFFFFF),
            cardOpacity = 30,
        )
        assertEquals(1f, config.effectiveBackground.alpha, 0.01f)
        assertEquals(0.3f, config.effectiveCard.alpha, 0.01f)
        assertEquals(0xFFFFFF, config.effectiveCard.toArgbCompat() and 0xFFFFFF)
    }

    @Test
    fun `per-metric tap falls back to global target`() {
        val global = TapTarget("com.example.global", null)
        val config = WidgetConfig(
            tapPackage = global.packageName,
            tapTargets = mapOf(Metric.STEPS.key to TapTarget("com.garmin.connect", null)),
        )
        assertEquals("com.garmin.connect", config.tapFor(Metric.STEPS.key).packageName)
        assertEquals("com.example.global", config.tapFor(Metric.SLEEP.key).packageName)
    }

    @Test
    fun `one cell shows value and chart together`() {
        val grid = WidgetLayoutPlanner.plan(80f, 80f, 1, 1f, true)
        assertEquals(1, grid.cells.size)
        assertEquals(CellPart.BOTH, grid.cells[0].part)
    }

    @Test
    fun `two metrics in a wide widget get one cell each`() {
        val grid = WidgetLayoutPlanner.plan(160f, 80f, 2, 1f, true)
        assertEquals(2, grid.columns)
        assertEquals(1, grid.rows)
        assertEquals(listOf(CellPart.BOTH, CellPart.BOTH), grid.cells.map { it.part })
    }

    @Test
    fun `single metric never splits value from chart`() {
        val grid = WidgetLayoutPlanner.plan(160f, 80f, 1, 1f, true)
        assertEquals(1, grid.cells.size)
        assertEquals(CellPart.BOTH, grid.cells[0].part)
    }

    @Test
    fun `grid prefers square cells instead of tall strips`() {
        val grid = WidgetLayoutPlanner.plan(320f, 320f, 4, 1f, true)
        assertEquals(2, grid.columns)
        assertEquals(2, grid.rows)
        assertTrue(grid.cells.all { it.part == CellPart.BOTH })
    }

    @Test
    fun `four metrics fill a square grid`() {
        val grid = WidgetLayoutPlanner.plan(160f, 160f, 4, 1f, true)
        assertEquals(2, grid.columns)
        assertEquals(2, grid.rows)
        assertTrue(grid.cells.all { it.part == CellPart.BOTH })
    }

    @Test
    fun `many metrics never get dropped and font shrinks in small cells`() {
        val grid = WidgetLayoutPlanner.plan(80f, 160f, 9, 1f, true)
        assertEquals(9, grid.cells.size)
        val small = WidgetLayoutPlanner.plan(80f, 80f, 9, 1f, true)
        val one = WidgetLayoutPlanner.plan(80f, 80f, 1, 1f, true)
        assertTrue(small.bodySp < one.bodySp)
    }

    @Test
    fun `multiple metrics are never split when the widget grows`() {
        val grid = WidgetLayoutPlanner.plan(320f, 320f, 4, 1f, true)
        assertEquals(4, grid.cells.size)
        assertTrue(grid.cells.all { it.part == CellPart.BOTH })
    }

    @Test
    fun `longer values shrink the font so digits stay visible`() {
        val short = WidgetLayoutPlanner.plan(80f, 80f, 1, 1f, true, valueChars = 3)
        val long = WidgetLayoutPlanner.plan(80f, 80f, 1, 1f, true, valueChars = 10)
        assertTrue(long.bodySp < short.bodySp)
    }

    @Test
    fun `live render size maps back to the matching preset`() {
        assertEquals(WidgetSize.S1X1, WidgetSize.fromDpSizes(listOf(DpSize(80.dp, 80.dp))))
        assertEquals(WidgetSize.S2X2, WidgetSize.fromDpSizes(listOf(DpSize(160.dp, 160.dp))))
        assertEquals(WidgetSize.S1X4, WidgetSize.fromDpSizes(listOf(DpSize(80.dp, 320.dp))))
        assertEquals(WidgetSize.S4X5, WidgetSize.fromDpSizes(listOf(DpSize(320.dp, 400.dp))))
    }

    @Test
    fun `off-grid render size resolves to nearest preset without crashing`() {
        val resolved = WidgetSize.fromDpSizes(listOf(DpSize(150.dp, 245.dp)))
        assertTrue(resolved != null)
    }

    @Test
    fun `empty sizes yield no preset`() {
        assertEquals(null, WidgetSize.fromDpSizes(emptyList()))
    }

    @Test
    fun `draft round trip preserves image layout and all settings`() {
        val original = WidgetConfig(
            metrics = listOf(Metric.STEPS, Metric.SLEEP),
            layout = WidgetLayout.IMAGE,
            showChart = false,
            showLabels = false,
            showRefreshStrip = true,
            fontScale = 1.2f,
            fontSizeSp = 30,
            backgroundColor = Color(0xFF102030),
            backgroundOpacity = 70,
            cardColor = Color(0xFF405060),
            cardOpacity = 55,
            accentColor = Color(0xFF00FF00),
            textColor = null,
            tapPackage = "com.garmin.android.apps.connectmobile",
            tapActivity = "com.garmin.android.apps.connectmobile/.MainActivity",
            tapTargets = mapOf(Metric.SLEEP.key to TapTarget("com.example.app", "com.example.app/.Main")),
            imagePath = "/data/data/app/files/widget_images/x.jpg",
            imageOpacity = 25,
            showSource = true,
        )
        val decoded = WidgetConfigCodec.decodeDraft(WidgetConfigCodec.encodeDraft(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `malformed draft returns null`() {
        assertEquals(null, WidgetConfigCodec.decodeDraft("broken"))
        assertEquals(null, WidgetConfigCodec.decodeDraft(null))
    }

    @Test
    fun `default grid size grows with metric count`() {
        assertEquals(WidgetSize.S2X2, WidgetSize.forMetricCount(1))
        assertEquals(WidgetSize.S1X2, WidgetSize.forMetricCount(2))
        assertEquals(WidgetSize.S2X2, WidgetSize.forMetricCount(4))
        assertTrue(WidgetSize.forMetricCount(9).columns >= WidgetSize.forMetricCount(4).columns)
        assertEquals(WidgetSize.S4X5, WidgetSize.forMetricCount(20))
    }
}
