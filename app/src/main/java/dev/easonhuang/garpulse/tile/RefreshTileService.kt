package dev.easonhuang.garpulse.tile

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.easonhuang.garpulse.R
import dev.easonhuang.garpulse.data.LanguagePreferences
import dev.easonhuang.garpulse.widget.WidgetUpdateScheduler

/** Refresh tile: forces all home-screen widgets to re-read Health Connect right now. */
class RefreshTileService : TileService() {

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(LanguagePreferences(newBase).applyTo(newBase))
    }

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = getString(R.string.quick_tile_refresh_name)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.quick_tile_refresh_subtitle)
            }
            icon = Icon.createWithResource(this@RefreshTileService, android.R.drawable.ic_popup_sync)
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        WidgetUpdateScheduler.refreshNow(applicationContext)
        qsTile?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = getString(R.string.quick_tile_refresh_done)
            }
            updateTile()
        }
    }
}
