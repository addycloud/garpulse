package dev.easonhuang.heartwood.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import dev.easonhuang.heartwood.data.LanguagePreferences

/** Base receiver that localises the context Glance uses to inflate widget resources. */
abstract class LocalizedWidgetReceiver : GlanceAppWidgetReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(LanguagePreferences(context).applyTo(context), intent)
    }
}
