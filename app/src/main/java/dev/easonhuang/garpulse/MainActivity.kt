package dev.easonhuang.garpulse

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import dev.easonhuang.garpulse.data.LanguagePreferences
import dev.easonhuang.garpulse.ui.GarPulseRoot
import dev.easonhuang.garpulse.ui.theme.GarPulseTheme
import dev.easonhuang.garpulse.widget.WidgetUpdateScheduler

class MainActivity : ComponentActivity() {

    // Deep-link target from a metric widget tap; consumed once navigation happens.
    private var deepLinkMetric by mutableStateOf<String?>(null)
    // Built-in tab destination ('@overview' etc.) requested from a widget tap shortcut.
    private var deepLinkDest by mutableStateOf<String?>(null)

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguagePreferences(newBase).applyTo(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        healLauncherComponents()
        deepLinkMetric = intent?.getStringExtra(EXTRA_METRIC)
        deepLinkDest = intent?.getStringExtra(EXTRA_DEST)
        val app = application as GarPulseApp
        setContent {
            GarPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    GarPulseRoot(
                        manager = app.healthConnect,
                        exporter = app.exporter,
                        dashboardPrefs = app.dashboardPrefs,
                        units = app.units,
                        language = app.language,
                        deepLinkMetric = deepLinkMetric,
                        deepLinkDest = deepLinkDest,
                        onDeepLinkConsumed = { deepLinkMetric = null; deepLinkDest = null },
                    )
                }
            }
        }
    }

    /**
     * One-time migration from the old icon-switch scheme, which disabled this activity itself
     * (breaking every explicit-component intent: widget taps, Health Connect's rationale alias).
     * Icons now live in aliases only; the base activity must always be at its manifest default.
     */
    private fun healLauncherComponents() {
        val pm = packageManager
        val base = ComponentName(this, "dev.easonhuang.garpulse.MainActivity")
        if (pm.getComponentEnabledSetting(base) != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT) {
            runCatching {
                pm.setComponentEnabledSetting(base, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Widgets read a cached snapshot; a fresh read on foreground keeps them in sync with
        // the app even when Doze has delayed the periodic worker.
        WidgetUpdateScheduler.refreshIfStale(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkMetric = intent.getStringExtra(EXTRA_METRIC)
        deepLinkDest = intent.getStringExtra(EXTRA_DEST)
    }

    companion object {
        const val EXTRA_METRIC = "dev.easonhuang.garpulse.extra.METRIC"
        const val EXTRA_DEST = "dev.easonhuang.garpulse.extra.DEST"
    }
}
