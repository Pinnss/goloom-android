package app.goloom.client.tunnel

import android.content.Context
import android.content.Intent
import android.net.VpnService
import androidx.core.content.ContextCompat
import app.goloom.client.data.ConnectionState
import app.goloom.client.data.LogSource
import app.goloom.client.data.LogStore
import app.goloom.client.data.Profile
import app.goloom.client.data.TunnelStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Единая точка управления туннелем для UI-слоя.
 *
 * Жизненный цикл:
 *   - UI вызывает [requestConnect] → controller отправляет Intent в [GoloomVpnService]
 *   - Service по ходу handshake обновляет [state] и [stats]
 *   - UI вызывает [requestDisconnect] → service stops
 *
 * Singleton — оба процесса (UI и Service) живут в одном app-процессе по
 * умолчанию, поэтому общий StateFlow безопасен. Если когда-нибудь Service
 * вынесем в отдельный процесс, переделать на bound-service IPC.
 */
class GoloomController private constructor(private val appContext: Context) {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Off)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val _stats = MutableStateFlow(TunnelStats())
    val stats: StateFlow<TunnelStats> = _stats.asStateFlow()

    /**
     * Возвращает Intent для системного запроса VPN-разрешения, если нужно,
     * иначе null. Caller (Activity) сам запускает его через ActivityResult API.
     */
    fun vpnPermissionIntent(): Intent? = VpnService.prepare(appContext)

    /**
     * Стартует туннель. ВАЖНО: вызывать только если [vpnPermissionIntent]
     * вернул null (т.е. разрешение уже выдано), иначе onStartCommand
     * у VpnService приведёт к SecurityException.
     */
    fun requestConnect(profile: Profile) {
        LogStore.get(appContext).info(
            LogSource.APP,
            "Connect requested: profile=${profile.name} (${profile.id})",
        )
        val intent = Intent(appContext, GoloomVpnService::class.java).apply {
            action = GoloomVpnService.ACTION_CONNECT
            putExtra(GoloomVpnService.EXTRA_PROFILE_ID, profile.id)
        }
        ContextCompat.startForegroundService(appContext, intent)
    }

    fun requestDisconnect() {
        LogStore.get(appContext).info(LogSource.APP, "Disconnect requested")
        val intent = Intent(appContext, GoloomVpnService::class.java).apply {
            action = GoloomVpnService.ACTION_DISCONNECT
        }
        // startService — нам важно, чтобы service получил intent даже
        // если он не в foreground. Stop через stopSelf он сделает сам.
        appContext.startService(intent)
    }

    // ── internal: обновление состояния из VpnService ───

    internal fun publishState(s: ConnectionState) {
        _state.value = s
    }

    internal fun publishStats(s: TunnelStats) {
        _stats.value = s
    }

    companion object {
        @Volatile
        private var INSTANCE: GoloomController? = null

        fun get(context: Context): GoloomController =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: GoloomController(context.applicationContext).also { INSTANCE = it }
            }
    }
}
