package app.goloom.client.util

import android.content.Intent
import app.goloom.client.data.ConnStr
import app.goloom.client.data.ConnStrParser

/**
 * Извлекает `goloom://...` из системного VIEW-Intent (deep link).
 * Возвращает null, если intent не deep link или ссылка невалидна.
 */
object DeepLink {
    fun extractConnStr(intent: Intent?): ConnStr? {
        if (intent == null) return null
        if (intent.action != Intent.ACTION_VIEW) return null
        val data = intent.dataString ?: return null
        return ConnStrParser.parseOrNull(data)
    }
}
