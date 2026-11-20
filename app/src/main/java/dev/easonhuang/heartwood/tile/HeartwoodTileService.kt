package dev.easonhuang.heartwood.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.easonhuang.heartwood.HeartwoodApp
import dev.easonhuang.heartwood.MainActivity
import dev.easonhuang.heartwood.R
import dev.easonhuang.heartwood.data.LanguagePreferences
import dev.easonhuang.heartwood.data.Metric
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Quick Settings tile for the Android shade (the Wi-Fi / Bluetooth area). It shows today's
 * step count and opens Heartwood when tapped. Users add it from the Quick Settings edit screen.
 */
class HeartwoodTileService : TileService() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguagePreferences(newBase).applyTo(newBase))
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch {
            val app = application as HeartwoodApp
            val granted = runCatching { app.healthConnect.isGranted(Metric.STEPS) }.getOrDefault(false)
            val value = if (granted) {
                runCatching {
                    val system = app.units.system.first()
                    app.healthConnect.readDashboard(system)
                        .firstOrNull { it.metric == Metric.STEPS }
                        ?.value?.takeIf { it != "-" }
                }.getOrNull()
            } else null

            qsTile?.apply {
                label = value ?: getString(R.string.quick_tile_name)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    subtitle = getString(R.string.quick_tile_subtitle)
                }
                icon = Icon.createWithResource(this@HeartwoodTileService, R.mipmap.ic_launcher)
                state = Tile.STATE_ACTIVE
                updateTile()
            }
        }
    }

    @Suppress("DEPRECATION")
    @android.annotation.SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pending = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            startActivityAndCollapse(pending)
        } else {
            startActivityAndCollapse(intent)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
