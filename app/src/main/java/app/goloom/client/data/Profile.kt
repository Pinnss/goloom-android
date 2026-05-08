package app.goloom.client.data

import org.json.JSONObject
import java.util.UUID

/**
 * Один VPN-профиль. Соответствует одной inbound-выдаче из админки.
 *
 * Профиль хранит исходный `goloom://...` (поле [connStr]) — этого
 * достаточно для подключения и для экспорта обратно в QR/clipboard.
 * Все остальные поля (host, tag, displayName) распарсены лениво и
 * сохранены для быстрой отрисовки в списке без перепарсинга.
 *
 * [name] — пользовательское имя профиля. После импорта проставляется
 * автоматически на основе tag или host встречи (см. [ConnStr.suggestedProfileName]).
 */
data class Profile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val connStr: String,                  // полная строка goloom://...
    /**
     * VK Calls target meeting URL — заполняется юзером в UI когда
     * connstr в lobby режиме (нет 'm' field, есть 'lm'+'b'). Сервер
     * лениво peer-join'ится к этому meeting'у на DIAL.
     */
    val vkTargetMeeting: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Лениво парсит connStr. Бросает только если профиль фундаментально невалиден. */
    val parsed: ConnStr by lazy {
        when (val r = ConnStrParser.parse(connStr)) {
            is ConnStrParser.Result.Ok -> r.value
            is ConnStrParser.Result.Error -> error("Profile $id has invalid connStr: ${r.reason}")
        }
    }

    /** Хост сервера для отображения в Profile-pill. */
    val serverDisplay: String
        get() = runCatching {
            // Для lobby connstr'а (VK client-meeting) показываем lobby host
            // или target meeting (если уже задан пользователем).
            val ref = vkTargetMeeting
                ?: parsed.meeting.takeIf { it.isNotBlank() }
                ?: parsed.lobbyMeetingUrl
                ?: return@runCatching "—"
            val u = java.net.URI(ref)
            u.host ?: ref
        }.getOrDefault("—")

    /** True если это VK client-meeting профиль и пользователь ещё не ввёл meeting URL. */
    val needsVKMeetingInput: Boolean
        get() = parsed.hasLobby && vkTargetMeeting.isNullOrBlank()

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("connStr", connStr)
        if (!vkTargetMeeting.isNullOrBlank()) put("vkTargetMeeting", vkTargetMeeting)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
    }

    companion object {
        fun fromJson(o: JSONObject): Profile = Profile(
            id = o.optString("id", UUID.randomUUID().toString()),
            name = o.optString("name", "Goloom profile"),
            connStr = o.getString("connStr"),
            vkTargetMeeting = o.optString("vkTargetMeeting").takeIf { it.isNotBlank() },
            createdAt = o.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
        )

        /** Создаёт профиль из распарсенной connection string с автоимени. */
        fun fromConnStr(parsed: ConnStr, customName: String? = null): Profile {
            val name = customName?.takeIf { it.isNotBlank() } ?: parsed.suggestedProfileName()
            return Profile(name = name, connStr = parsed.raw)
        }
    }
}
