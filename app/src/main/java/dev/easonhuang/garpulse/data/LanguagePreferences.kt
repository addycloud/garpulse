package dev.easonhuang.garpulse.data

import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.LocaleList
import android.content.res.Configuration
import androidx.annotation.StringRes
import dev.easonhuang.garpulse.R
import java.util.Locale

/** Languages offered by the in-app switcher. SYSTEM follows the device locale. */
enum class AppLanguage(val key: String, val tag: String, @StringRes val labelRes: Int) {
    SYSTEM("system", "", R.string.language_system),
    ENGLISH("en", "en", R.string.language_english),
    CHINESE("zh", "zh-CN", R.string.language_chinese),
    ;

    companion object {
        fun fromKey(key: String?): AppLanguage = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/**
 * Small synchronous preference store so every entry point (activities, widget receivers and
 * Quick Settings services) can wrap its base context before resources are inflated.
 *
 * Android 13+ also gets the real per-app locale via LocaleManager, which is what the system
 * uses when it renders widgets and tiles outside our process. Older versions rely on the
 * context wrapper plus the process-wide default set in [applyAtStartup].
 */
class LanguagePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val current: AppLanguage get() = AppLanguage.fromKey(prefs.getString(KEY_LANGUAGE, null))

    /** Wrap [base] so Compose/resource lookups inside it use the chosen language. */
    fun applyTo(base: Context): Context {
        if (current == AppLanguage.SYSTEM) return base
        val locale = Locale.forLanguageTag(current.tag)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    fun set(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.key).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = if (language == AppLanguage.SYSTEM) LocaleList.getEmptyLocaleList()
            else LocaleList.forLanguageTags(language.tag)
            runCatching { localeManager.setApplicationLocales(locales) }
        } else {
            val locale = if (language == AppLanguage.SYSTEM) systemLocale
            else Locale.forLanguageTag(language.tag)
            Locale.setDefault(locale)
        }
    }

    @get:androidx.annotation.RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private val localeManager: LocaleManager
        get() = appContext.getSystemService(LocaleManager::class.java)

    private val appContext: Context = context.applicationContext

    companion object {
        private const val PREFS_NAME = "garpulse_language"
        private const val KEY_LANGUAGE = "language"

        /** Captured before any override, so "follow system" can restore the real default. */
        private val systemLocale: Locale = Locale.getDefault()

        fun applyAtStartup(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val selected = AppLanguage.fromKey(prefs.getString(KEY_LANGUAGE, null))
            if (selected != AppLanguage.SYSTEM) Locale.setDefault(Locale.forLanguageTag(selected.tag))
        }
    }
}
