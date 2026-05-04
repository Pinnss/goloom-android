package app.goloom.client.util

import android.content.Context
import android.os.Build
import app.goloom.client.BuildConfig
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Проверка релизов на GitHub. Дёргает `/releases/latest`, сравнивает
 * `tag_name` с текущим [BuildConfig.VERSION_NAME] (с префиксом `v` или без).
 *
 * Не делает фоновых периодических проверок — только по триггеру UI
 * (тап на "Check for updates" или версию в футере). Это укладывается в
 * ограничения Play / IOS-стиль UX и не требует BG-permissions.
 */
object UpdateChecker {

    sealed class Result {
        object UpToDate : Result()
        data class Available(
            val version: String,
            val downloadUrl: String,
            val sizeBytes: Long,
            val notes: String,
        ) : Result()
        data class Failed(val message: String) : Result()
    }

    /**
     * Блокирующий вызов, звать с IO-диспетчера.
     */
    fun check(context: Context): Result {
        val current = stripV(BuildConfig.VERSION_NAME)
        val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 10_000
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty(
                "User-Agent",
                "Goloom-Android/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})",
            )
        }
        return try {
            conn.inputStream.bufferedReader().use { it.readText() }.let { body ->
                val json = JSONObject(body)
                val latestTag = json.optString("tag_name").takeIf { it.isNotBlank() }
                    ?: return Result.Failed("no tag_name in response")
                val latest = stripV(latestTag)
                if (compareSemver(latest, current) <= 0) return Result.UpToDate

                val assets = json.optJSONArray("assets")
                val apk = (0 until (assets?.length() ?: 0))
                    .map { assets!!.getJSONObject(it) }
                    .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
                val downloadUrl = apk?.optString("browser_download_url").orEmpty()
                val size = apk?.optLong("size", 0L) ?: 0L
                val notes = json.optString("body", "")
                Result.Available(latest, downloadUrl, size, notes)
            }
        } catch (t: Throwable) {
            Result.Failed(t.message ?: t.javaClass.simpleName)
        } finally {
            conn.disconnect()
        }
    }

    private fun stripV(s: String): String =
        s.removePrefix("v").removePrefix("V").trim()

    /**
     * Простое semver-сравнение по точкам. Не строгое — fallback на
     * лексикографическое если структура неожиданная. Подходит для
     * коротких "1.2.3"-тегов, которыми мы релизимся.
     */
    private fun compareSemver(a: String, b: String): Int {
        val pa = a.split(".").mapNotNull { it.toIntOrNull() }
        val pb = b.split(".").mapNotNull { it.toIntOrNull() }
        if (pa.isEmpty() || pb.isEmpty()) return a.compareTo(b)
        val len = maxOf(pa.size, pb.size)
        for (i in 0 until len) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x - y
        }
        return 0
    }
}
