package app.goloom.client.data

import android.util.Base64
import org.json.JSONObject

/**
 * Парсер `goloom://...` connection string. Формат — base64url(JSON).
 * Источник истины — `internal/connstr/connstr.go` в репо goloom-server.
 *
 * При расхождении формата — ВНАЧАЛЕ обновлять Go-сторону, потом этот файл,
 * чтобы старые ссылки оставались парсабельными (новые поля помечать как
 * optional с дефолтами).
 */
data class ConnStr(
    /** Meeting URL — обязателен для всех транспортов. */
    val meeting: String,
    val displayName: String? = null,
    val tag: String? = null,
    val psk: String? = null,
    val kcpMtu: Int = 0,
    val kcpSndWnd: Int = 0,
    val kcpRcvWnd: Int = 0,

    /** Transport: "telemost" (default), "vk-calls", "livekit-wb-stream". */
    val transport: String? = null,

    /** Codec hint для VK ("vp8" или "h264"; пусто = h264). */
    val codec: String? = null,

    // WG-конфиг, заданный inline. Все 4 ключевых поля должны присутствовать,
    // иначе [hasWireGuard] вернёт false и Connect не запустится.
    val wgClientPrivate: String? = null,
    val wgServerPublic: String? = null,
    val wgClientAddress: String? = null,        // CIDR, e.g. "10.66.1.2/24"
    val wgEndpoint: String? = null,             // e.g. "127.0.0.1:51820"
    val wgDns: String? = null,                  // comma-separated, e.g. "1.1.1.1,8.8.8.8"

    /** Полная исходная строка `goloom://...` для re-export. */
    val raw: String,
) {
    /** Connstr описывает VK Calls транспорт. */
    val isVKCalls: Boolean
        get() = transport == "vk-calls"

    /**
     * Connstr — это vkturnproxy:// link (vk-turn-srtp transport).
     * Go SDK сам декодирует payload на стороне Connect; Kotlin-сторона
     * хранит raw и при подключении вызывает Client.connectVKTurnSRTP.
     */
    val isVKTurnProxyLink: Boolean
        get() = raw.trim().startsWith("vkturnproxy://")

    /** Минимально валиден ли встроенный WG-конфиг. */
    val hasWireGuard: Boolean
        get() = !wgClientPrivate.isNullOrEmpty() &&
                !wgServerPublic.isNullOrEmpty() &&
                !wgClientAddress.isNullOrEmpty() &&
                !wgEndpoint.isNullOrEmpty()

    /**
     * Рендерит wg-quick-совместимый конфиг из встроенных WG-полей.
     * Возвращает `null`, если WG-полей нет.
     *
     * AllowedIPs выставляем split `0.0.0.0/1, 128.0.0.0/1` — это
     * full-tunnel без коллизии с дефолтным маршрутом, как делается в
     * большинстве WG-клиентов под Android.
     */
    fun toWireGuardConfig(): String? {
        if (!hasWireGuard) return null
        val dns = wgDns?.takeIf { it.isNotBlank() } ?: "1.1.1.1, 8.8.8.8"
        return buildString {
            appendLine("[Interface]")
            appendLine("PrivateKey = $wgClientPrivate")
            appendLine("Address = $wgClientAddress")
            appendLine("DNS = $dns")
            appendLine()
            appendLine("[Peer]")
            appendLine("PublicKey = $wgServerPublic")
            appendLine("Endpoint = $wgEndpoint")
            appendLine("AllowedIPs = 0.0.0.0/1, 128.0.0.0/1")
            appendLine("PersistentKeepalive = 25")
        }
    }

    /** Имя профиля по умолчанию — на основе тега или хоста встречи. */
    fun suggestedProfileName(): String {
        if (!tag.isNullOrBlank()) return tag.replaceFirstChar { it.uppercase() }
        if (isVKTurnProxyLink) return "VK TURN SRTP"
        val host = runCatching {
            java.net.URI(meeting).host?.takeIf { it.isNotBlank() }
        }.getOrNull()
        return host ?: "Goloom profile"
    }
}

object ConnStrParser {
    private const val SCHEME = "goloom://"

    sealed class Result {
        data class Ok(val value: ConnStr) : Result()
        data class Error(val reason: String) : Result()
    }

    /**
     * Парсит произвольную строку. Безопасно для пользовательского ввода —
     * не кидает исключений, возвращает [Result.Error] с человеческим
     * сообщением. Триммит whitespace, чувствителен к scheme.
     */
    private const val VK_TURN_PROXY_SCHEME = "vkturnproxy://"

    fun parse(input: String): Result {
        val trimmed = input.trim()
        // vkturnproxy://... links are decoded inside the Go SDK at
        // connect time (via Client.previewVKTurnProxyLink and
        // Client.connectVKTurnSRTP). On the Kotlin side we just
        // stash the raw string + flag the transport so the
        // profile store / VpnService dispatcher knows which path
        // to take.
        if (trimmed.startsWith(VK_TURN_PROXY_SCHEME)) {
            return Result.Ok(
                ConnStr(
                    meeting = "", // not used by the SRTP path
                    transport = "vk-turn-srtp",
                    raw = trimmed,
                )
            )
        }
        if (!trimmed.startsWith(SCHEME)) {
            return Result.Error("Expected $SCHEME or $VK_TURN_PROXY_SCHEME prefix")
        }
        val payload = trimmed.removePrefix(SCHEME)
        val jsonBytes = try {
            // base64.RawURLEncoding в Go — это URL_SAFE без padding.
            Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        } catch (e: IllegalArgumentException) {
            return Result.Error("Invalid base64: ${e.message}")
        }
        val json = try {
            JSONObject(String(jsonBytes, Charsets.UTF_8))
        } catch (e: Exception) {
            return Result.Error("Invalid JSON: ${e.message}")
        }
        val meeting = json.optString("m").takeIf { it.isNotBlank() } ?: ""
        if (meeting.isEmpty()) {
            return Result.Error("Missing 'm' (meeting URL)")
        }
        return Result.Ok(
            ConnStr(
                meeting = meeting,
                displayName = json.optString("n").takeIf { it.isNotBlank() },
                tag = json.optString("tag").takeIf { it.isNotBlank() },
                psk = json.optString("psk").takeIf { it.isNotBlank() },
                kcpMtu = json.optInt("km", 0),
                kcpSndWnd = json.optInt("ks", 0),
                kcpRcvWnd = json.optInt("kr", 0),
                transport = json.optString("t").takeIf { it.isNotBlank() },
                codec = json.optString("c").takeIf { it.isNotBlank() },
                wgClientPrivate = json.optString("wgcp").takeIf { it.isNotBlank() },
                wgServerPublic = json.optString("wgsp").takeIf { it.isNotBlank() },
                wgClientAddress = json.optString("wga").takeIf { it.isNotBlank() },
                wgEndpoint = json.optString("wge").takeIf { it.isNotBlank() },
                wgDns = json.optString("wgd").takeIf { it.isNotBlank() },
                raw = trimmed,
            )
        )
    }

    /**
     * Удобный wrapper, который возвращает null вместо [Result.Error].
     * Используется в местах, где причина ошибки не нужна (например,
     * быстрый probe буфера обмена).
     */
    fun parseOrNull(input: String): ConnStr? = (parse(input) as? Result.Ok)?.value
}
