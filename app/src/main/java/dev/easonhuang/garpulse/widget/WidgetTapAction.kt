package dev.easonhuang.garpulse.widget

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import dev.easonhuang.garpulse.MainActivity
import dev.easonhuang.garpulse.R

/**
 * Resolves the tap target when the widget is actually clicked, instead of baking an intent into
 * the RemoteViews at render time. Storing an explicit component (not just a package) also works
 * for apps whose launch intent cannot be reconstructed from a package name alone.
 *
 * Tap resolution order: deep-link URI → explicit component → package launch intent → the metric's
 * own detail page in GarPulse (metric cells only) → nothing. A tap that carries no metric — the
 * widget background or a picture tile — opens nothing when no destination is configured, so
 * "don't open any app" stays configurable.
 * A component that is not exported (most in-app pages, e.g. Mi Home's device screens) throws a
 * SecurityException; we surface a toast instead of opening an app the user never picked.
 */
object WidgetTapAction : ActionCallback {

    val KEY_PACKAGE = ActionParameters.Key<String>("tap_package")
    val KEY_COMPONENT = ActionParameters.Key<String>("tap_component")
    val KEY_URI = ActionParameters.Key<String>("tap_uri")
    val KEY_METRIC = ActionParameters.Key<String>("tap_metric")

    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val component = parameters[KEY_COMPONENT]?.takeIf { it.isNotBlank() }
            ?.let(ComponentName::unflattenFromString)
        val pkg = parameters[KEY_PACKAGE]?.takeIf { it.isNotBlank() }
        val uri = parameters[KEY_URI]?.takeIf { it.isNotBlank() }
        // Carried by metric cells only: the fallback destination when no app is configured.
        val metric = parameters[KEY_METRIC]?.takeIf { it.isNotBlank() }

        // Built-in targets (@refresh/@overview/…) never open another app.
        if (pkg != null && TapShortcuts.isShortcut(pkg)) {
            TapShortcuts.launch(context, pkg, glanceId)
            return
        }

        val intent = when {
            uri != null -> Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            component != null -> Intent().setComponent(component)
            pkg != null -> launchIntent(context, pkg)
            else -> null
        }
        if (intent == null) {
            // No destination configured. A metric cell still knows which metric it belongs to, so
            // it opens that metric's detail page inside GarPulse — the historical default. A tap
            // on the widget's background / picture carries no metric, and that really does
            // nothing (so "don't open any app" stays configurable).
            if (metric.isNullOrBlank()) return
            val detail = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_METRIC, metric)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(detail) }
            return
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!runCatching { context.startActivity(intent); true }.getOrDefault(false)) {
            // Deep links can be unresolvable and components unexported — say so instead of
            // silently swapping in an app the user never chose.
            Toast.makeText(context, R.string.widget_tap_open_failed, Toast.LENGTH_SHORT).show()
        }
    }

    internal fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        // Explicit category-main intent first: it works with the launcher <queries> declaration.
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg)
        val resolved = runCatching {
            pm.resolveActivity(launcher, 0)?.activityInfo?.let { ComponentName(it.packageName, it.name) }
        }.getOrNull()
        if (resolved != null) return Intent().setComponent(resolved)
        return runCatching { pm.getLaunchIntentForPackage(pkg) }.getOrNull()
    }
}

/**
 * Built-in tap destinations stored as special "package" values starting with '@'. Besides third
 * party apps, a metric can now jump to GarPulse's own screens or trigger a refresh.
 */
object TapShortcuts {
    const val REFRESH = "@refresh"
    const val OVERVIEW = "@overview"
    const val INSIGHTS = "@insights"
    const val WIDGETS = "@widgets"
    const val SETTINGS = "@settings"

    /** The list shown in the widget editor; @insights no longer has a destination page. */
    val all = listOf(REFRESH, OVERVIEW, WIDGETS, SETTINGS)

    fun isShortcut(pkg: String): Boolean = pkg.startsWith("@")

    fun labelOf(pkg: String): Int = when (pkg) {
        REFRESH -> R.string.tap_shortcut_refresh
        OVERVIEW -> R.string.tap_shortcut_overview
        INSIGHTS -> R.string.tap_shortcut_insights
        WIDGETS -> R.string.tap_shortcut_widgets
        SETTINGS -> R.string.tap_shortcut_settings
        else -> R.string.widget_tap_default
    }

    /** Executes the shortcut; [glanceId] lets the refresh target animate only this widget. */
    suspend fun launch(context: Context, pkg: String, glanceId: GlanceId?) {
        when (pkg) {
            REFRESH -> WidgetRefreshStrip.performRefresh(context, glanceId)
            OVERVIEW, INSIGHTS, WIDGETS, SETTINGS -> {
                val intent = Intent(context, MainActivity::class.java).apply {
                    putExtra(MainActivity.EXTRA_DEST, destRoute(pkg))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            }
        }
    }

    /** Maps a shortcut to MainNav's internal route names. The removed Insights tab falls back
     *  to the Overview so a config saved before the tab's removal still opens something sane. */
    private fun destRoute(pkg: String): String = when (pkg) {
        OVERVIEW -> "today"
        INSIGHTS -> "today"
        WIDGETS -> "widgets"
        SETTINGS -> "settings"
        else -> "today"
    }
}

/** Shared with the editor's "test" button so the preview behaves exactly like a widget tap. */
internal fun testTapIntent(context: Context, config: WidgetConfig): Intent? {
    val uri = config.tapUri?.takeIf { it.isNotBlank() }
    if (uri != null) {
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val component = config.tapActivity?.takeIf { it.isNotBlank() }?.let(ComponentName::unflattenFromString)
    if (component != null) {
        return Intent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val pkg = config.tapPackage?.takeIf { it.isNotBlank() } ?: return null
    return WidgetTapAction.launchIntent(context, pkg)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/**
 * True when the configured target will most likely fail (unexported component) so callers can
 * warn the user before saving. Only a hint — export status can change and may be unresolvable.
 */
internal fun tapTargetLikelyBlocked(context: Context, config: WidgetConfig): Boolean {
    val component = config.tapActivity?.takeIf { it.isNotBlank() }?.let(ComponentName::unflattenFromString)
        ?: return false
    val pm = context.packageManager
    return runCatching {
        pm.getActivityInfo(component, 0).exported == false
    }.getOrDefault(false)
}
