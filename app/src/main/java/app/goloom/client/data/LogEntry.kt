package app.goloom.client.data

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Уровень лога. Maps на стандартные goloomd / Android logcat уровни.
 * Порядок констант важен — больше = серьёзнее (используется в фильтрах).
 */
enum class LogLevel(val short: String) {
    DEBUG("DBG"),
    INFO("INF"),
    WARN("WRN"),
    ERROR("ERR");

    companion object {
        fun parse(text: String): LogLevel? = when (text.uppercase()) {
            "DEBUG", "DBG" -> DEBUG
            "INFO", "INF" -> INFO
            "WARN", "WRN", "WARNING" -> WARN
            "ERROR", "ERR" -> ERROR
            else -> null
        }
    }
}

/**
 * Источник лога — для фильтра по компоненту.
 * APP — события самого Android-приложения (UI, сервис).
 * SDK — что приходит из go-биндинга (LogSink).
 * WG — wireguard-android backend.
 */
enum class LogSource(val short: String) {
    APP("APP"),
    SDK("SDK"),
    WG("WG"),
}

data class LogEntry(
    val timestampMs: Long = System.currentTimeMillis(),
    val level: LogLevel,
    val source: LogSource,
    val message: String,
) {
    fun formatTime(): String = TIME_FMT.get()!!.format(timestampMs)

    /** Строковая форма для шеринга/копирования. */
    fun formatted(): String = "${formatTime()} ${level.short} ${source.short} $message"

    companion object {
        private val TIME_FMT: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("HH:mm:ss.SSS", Locale.US).apply {
                    timeZone = TimeZone.getDefault()
                }
        }
    }
}
