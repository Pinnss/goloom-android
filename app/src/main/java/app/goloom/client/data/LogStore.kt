package app.goloom.client.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter

/**
 * In-memory + disk лог-стор. Держит последние N entries в [entries] для UI,
 * параллельно пишет в файл `goloom_tunnel.log` в filesDir с ротацией:
 * когда размер файла превышает MAX_FILE_BYTES, файл усекается до 60% хвоста.
 *
 * Ротация — НЕ через переименование/архивы (это требует locks и плохо
 * комбинируется с открытым writer): просто читаем хвост и перезаписываем.
 * Лимит размера ~500KB — после этого скриншоты-логов всё равно бесполезны
 * для пользователя, а старшие записи не нужны.
 */
class LogStore private constructor(context: Context) {

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val logFile: File = File(context.cacheDir, LOG_DIR).also { it.mkdirs() }
        .let { File(it, LOG_FILE) }

    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Добавляет entry. Старые усекаются до MAX_MEM. Параллельно дублируется
     * в logcat (с тегом "Goloom") и в файл.
     */
    fun add(entry: LogEntry) {
        // 1. memory ring
        val cur = _entries.value
        val next = if (cur.size >= MAX_MEM) cur.drop(cur.size - MAX_MEM + 1) + entry
                   else cur + entry
        _entries.value = next

        // 2. logcat
        val tag = "Goloom"
        val full = "${entry.source.short} ${entry.message}"
        when (entry.level) {
            LogLevel.DEBUG -> Log.d(tag, full)
            LogLevel.INFO -> Log.i(tag, full)
            LogLevel.WARN -> Log.w(tag, full)
            LogLevel.ERROR -> Log.e(tag, full)
        }

        // 3. file (async)
        writeScope.launch { appendToFile(entry) }
    }

    fun add(level: LogLevel, source: LogSource, message: String) {
        add(LogEntry(level = level, source = source, message = message))
    }

    fun debug(source: LogSource, message: String) = add(LogLevel.DEBUG, source, message)
    fun info(source: LogSource, message: String) = add(LogLevel.INFO, source, message)
    fun warn(source: LogSource, message: String) = add(LogLevel.WARN, source, message)
    fun error(source: LogSource, message: String) = add(LogLevel.ERROR, source, message)

    fun clear() {
        _entries.value = emptyList()
        writeScope.launch {
            runCatching { logFile.writeText("") }
        }
    }

    fun snapshotFormatted(): String =
        _entries.value.joinToString(separator = "\n") { it.formatted() }

    /** Копия активного лог-файла для шеринга (cache; FileProvider шарит cache-path). */
    fun fileForShare(): File? = if (logFile.exists() && logFile.length() > 0) logFile else null

    // ─── private ──────────────────────────────────────────────

    private fun appendToFile(entry: LogEntry) {
        runCatching {
            PrintWriter(FileWriter(logFile, /* append = */ true)).use { w ->
                w.println(entry.formatted())
            }
            if (logFile.length() > MAX_FILE_BYTES) {
                rotate()
            }
        }
    }

    private fun rotate() {
        runCatching {
            val text = logFile.readText()
            val keepFrom = (text.length * 0.4).toInt()
            // Усекаем до начала ближайшей строки, чтобы не оставлять кусок.
            val newlineIdx = text.indexOf('\n', keepFrom).takeIf { it >= 0 } ?: keepFrom
            logFile.writeText(text.substring(newlineIdx + 1))
        }
    }

    companion object {
        private const val LOG_DIR = "logs"
        private const val LOG_FILE = "goloom_tunnel.log"
        private const val MAX_MEM = 2000
        private const val MAX_FILE_BYTES = 500_000L

        @Volatile
        private var INSTANCE: LogStore? = null

        fun get(context: Context): LogStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: LogStore(context).also { INSTANCE = it }
            }
    }
}
