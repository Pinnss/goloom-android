package app.goloom.client.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Слушает default-network через ConnectivityManager и эмитит:
 *  - [available] — есть ли вообще активная сеть (для UI/диагностики)
 *  - [networkChange] — handle сменился (новая Network), это сигнал для
 *    Goloom-сервиса сделать reconnect
 *
 * Singleton. Регистрируем глобальный callback в [start] (вызывается из
 * GoloomApp) и держим его всё время жизни процесса. NetworkCallback
 * лёгкий, постоянная регистрация безопасна и не жжёт батарею.
 */
class NetworkMonitor private constructor(context: Context) {

    private val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
        as ConnectivityManager

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    /**
     * Эмитит на каждое существенное изменение default-network. Подписчики
     * (VpnService) сами решают как реагировать — обычно через debounce
     * + reconnect, чтобы не дёргаться от пачки callback'ов при handover.
     */
    private val _networkChange = MutableSharedFlow<Network>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val networkChange: SharedFlow<Network> = _networkChange.asSharedFlow()

    private var currentNetwork: Network? = null

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _available.value = true
            // Если default network сменился (например, WiFi → mobile при
            // выходе из дома), это новый Network handle — триггерим
            // reconnect.
            val prev = currentNetwork
            currentNetwork = network
            if (prev != null && prev != network) {
                _networkChange.tryEmit(network)
            }
        }

        override fun onLost(network: Network) {
            // Default network ушёл — но новый придёт через onAvailable
            // если он есть. Помечаем как unavailable до прихода нового.
            if (network == currentNetwork) {
                currentNetwork = null
                _available.value = false
            }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            // Например, WiFi потерял интернет (caps без NET_CAPABILITY_VALIDATED).
            // Не дёргаем reconnect здесь — система сама переключит default
            // на mobile и сработает onAvailable с новым handle.
        }
    }

    fun start() {
        // registerDefaultNetworkCallback — Android 7.0+ (API 24).
        // Минимум у нас 26, так что всегда доступно.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            cm.registerDefaultNetworkCallback(callback)
            // Если уже есть активная сеть на момент старта, отметим.
            cm.activeNetwork?.let {
                currentNetwork = it
                _available.value = true
            }
        }
    }

    fun stop() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }

    companion object {
        @Volatile
        private var INSTANCE: NetworkMonitor? = null

        fun get(context: Context): NetworkMonitor =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: NetworkMonitor(context).also { INSTANCE = it }
            }
    }
}
