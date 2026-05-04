package app.goloom.client.data

import app.goloom.client.design.EyeState

/**
 * Состояние VPN-туннеля для UI.
 */
sealed class ConnectionState {
    object Off : ConnectionState()
    object Connecting : ConnectionState()
    data class On(val sinceMs: Long) : ConnectionState()
    data class Error(val message: String) : ConnectionState()

    val asEyeState: EyeState
        get() = when (this) {
            Off, is Error -> EyeState.Idle
            Connecting -> EyeState.Connecting
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
