package app.goloom.client.data

import app.goloom.client.design.EyeState

/**
 * Состояние VPN-туннеля для UI.
 */
sealed class ConnectionState {
    object Off : ConnectionState()

    /**
     * Идёт подключение. [phase] — машинно-читаемый код (например
     * "lobby_dial", "auth", "target_connect"), [detail] — короткое
     * человеческое описание для UI subtext.
     *
     * Список phase'ов соответствует Go-стороне (mobile/api.go::emitPhase,
     * mobile/vk.go).
     */
    data class Connecting(val phase: String = "", val detail: String? = null) : ConnectionState() {
        fun humanLabel(): String = when (phase) {
            "init" -> "Запуск"
            "resolving" -> "Резолвим SFU edge IP"
            "lobby_join" -> "Заход в лобби-звонок"
            "lobby_auth" -> "VK auth ladder (лобби)"
            "lobby_wait_server" -> "Ждём сервер в лобби"
            "lobby_dial" -> "DIAL серверу"
            "lobby_done" -> "DIAL принят"
            "auth" -> "VK auth ladder"
            "captcha" -> "Решение captcha"
            "waiting_for_peer" -> "Ждём пира"
            "target_connect" -> "Peer-join в target"
            "handshake" -> "SDP handshake"
            "bridge_up" -> "WireGuard bridge"
            "ready" -> "Готово"
            "" -> "Подключение"
            else -> phase
        }
    }

    data class On(val sinceMs: Long) : ConnectionState()
    data class Error(val message: String) : ConnectionState()

    val asEyeState: EyeState
        get() = when (this) {
            Off, is Error -> EyeState.Idle
            is Connecting -> EyeState.Connecting
            is On -> EyeState.On
        }

    val isActive: Boolean
        get() = this is Connecting || this is On
}

/**
 * Снапшот метрик туннеля (обновляется ~раз в секунду из StatsJSON).
 */
data class TunnelStats(
    val txBytes: Long = 0,
    val rxBytes: Long = 0,
    val durationMs: Long = 0,
)
