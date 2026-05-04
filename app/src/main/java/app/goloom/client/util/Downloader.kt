package app.goloom.client.util

import android.content.Context
import app.goloom.client.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Скачивает APK обновления в `filesDir/updates/`. Прогресс отдаётся
 * через колбэк (вызывается на IO-потоке, UI слой должен сам перевести
 * на main).
 *
 * Стратегия:
 *   - один файл за раз (`goloom-update.apk`); старый удаляется перед
 *     новой загрузкой, чтобы не копить
 *   - кладём в `filesDir` (а не cache), потому что packageInstaller
 *     требует чтобы файл пережил после tap "Install" (cache может быть
 *     эвакуирован)
 *   - тонкая HTTP-логика — без resume/retry; в случае разрыва юзер
 *     повторит вручную
 */
object Downloader {

    sealed class State {
        object Idle : State()
        data class Downloading(val percent: Int, val downloadedBytes: Long, val totalBytes: Long) : State()
        data class Ready(val file: File) : State()
        data class Failed(val message: String) : State()
    }

    /**
     * Блокирует, скачивает APK по [url]. Возвращает [File] на готовый
     * apk или бросает исключение.
     */
    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (percent: Int, downloaded: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "updates").also { it.mkdirs() }
        // Чистим прошлые загрузки.
        dir.listFiles()?.forEach { it.delete() }

        val out = File(dir, "goloom-update.apk")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                "Goloom-Android/${BuildConfig.VERSION_NAME}",
            )
            setRequestProperty("Accept", "application/vnd.android.package-archive,application/octet-stream")
        }
        try {
            conn.connect()
            if (conn.responseCode !in 200..299) {
                throw RuntimeException("HTTP ${conn.responseCode}")
            }
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: -1L

            conn.inputStream.use { input ->
                FileOutputStream(out).use { output ->
                    val buf = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastPct = -1
                    onProgress(0, 0, total)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        val pct = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                        if (pct != lastPct) {
                            lastPct = pct
                            onProgress(pct, downloaded, total)
                        }
                    }
                    onProgress(100, downloaded, total.takeIf { it > 0 } ?: downloaded)
                }
            }
            out
        } finally {
            conn.disconnect()
        }
    }
}
