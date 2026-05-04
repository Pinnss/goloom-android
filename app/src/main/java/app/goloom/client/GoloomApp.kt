package app.goloom.client

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import app.goloom.client.data.LogSource
import app.goloom.client.data.LogStore
import app.goloom.client.data.SettingsManager

/**
 * Application-class. Делает три вещи:
 *  1. Применяет сохранённый язык на старте (см. [SettingsManager.Language])
 *  2. Инициализирует stores (singleton-warm-up — иначе первый getInstance
 *     может прилететь из background-VpnService).
 *  3. Логирует факт старта в LogStore — удобно при дебаге.
 */
class GoloomApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // Language
        val lang = SettingsManager.get(this).language.value
        applyLanguage(lang)

        // Warm up stores (Profile / Log / Settings)
        LogStore.get(this).info(
            LogSource.APP,
            "App start · v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        )
    }

    private fun applyLanguage(lang: SettingsManager.Language) {
        val tag = lang.tag
        val locales = if (tag.isBlank()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }
        AppCompatDelegate.setApplicationLocales(locales)
    }
}
