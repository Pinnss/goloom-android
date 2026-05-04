package app.goloom.client.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Поведенческие настройки приложения. Хранятся в отдельном SharedPreferences
 * `goloom_settings`. Каждое свойство — пара "getter из SP" + StateFlow для UI.
 *
 * Singleton как у [ProfileStore] / [LogStore].
 */
class SettingsManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    // ── Поведение ───
    private val _connectOnLaunch = MutableStateFlow(prefs.getBoolean(K_CONNECT_LAUNCH, false))
    val connectOnLaunch: StateFlow<Boolean> = _connectOnLaunch.asStateFlow()
    fun setConnectOnLaunch(v: Boolean) {
        _connectOnLaunch.value = v
        prefs.edit().putBoolean(K_CONNECT_LAUNCH, v).apply()
    }

    private val _autoReconnect = MutableStateFlow(prefs.getBoolean(K_AUTO_RECONNECT, true))
    val autoReconnect: StateFlow<Boolean> = _autoReconnect.asStateFlow()
    fun setAutoReconnect(v: Boolean) {
        _autoReconnect.value = v
        prefs.edit().putBoolean(K_AUTO_RECONNECT, v).apply()
    }

    private val _blockIPv6 = MutableStateFlow(prefs.getBoolean(K_BLOCK_IPV6, false))
    val blockIPv6: StateFlow<Boolean> = _blockIPv6.asStateFlow()
    fun setBlockIPv6(v: Boolean) {
        _blockIPv6.value = v
        prefs.edit().putBoolean(K_BLOCK_IPV6, v).apply()
    }

    // ── Параметры туннеля ───
    private val _mtu = MutableStateFlow(prefs.getInt(K_MTU, 1380))
    val mtu: StateFlow<Int> = _mtu.asStateFlow()
    fun setMtu(v: Int) {
        _mtu.value = v
        prefs.edit().putInt(K_MTU, v).apply()
    }

    private val _persistentKeepalive = MutableStateFlow(prefs.getInt(K_KEEPALIVE, 25))
    val persistentKeepalive: StateFlow<Int> = _persistentKeepalive.asStateFlow()
    fun setPersistentKeepalive(v: Int) {
        _persistentKeepalive.value = v
        prefs.edit().putInt(K_KEEPALIVE, v).apply()
    }

    // ── DNS ───
    private val _dnsServers = MutableStateFlow(prefs.getString(K_DNS, "1.1.1.1, 9.9.9.9") ?: "")
    val dnsServers: StateFlow<String> = _dnsServers.asStateFlow()
    fun setDnsServers(v: String) {
        _dnsServers.value = v
        prefs.edit().putString(K_DNS, v).apply()
    }

    // ── Язык ───
    enum class Language(val tag: String) { System(""), English("en"), Russian("ru") }
    private val _language = MutableStateFlow(loadLanguage())
    val language: StateFlow<Language> = _language.asStateFlow()
    fun setLanguage(lang: Language) {
        _language.value = lang
        prefs.edit().putString(K_LANG, lang.name).apply()
    }
    private fun loadLanguage(): Language =
        prefs.getString(K_LANG, Language.System.name)?.let {
            runCatching { Language.valueOf(it) }.getOrDefault(Language.System)
        } ?: Language.System

    // ── Routing mode ───
    enum class RoutingMode { All, Whitelist, Blacklist }
    private val _routingMode = MutableStateFlow(loadRoutingMode())
    val routingMode: StateFlow<RoutingMode> = _routingMode.asStateFlow()
    fun setRoutingMode(m: RoutingMode) {
        _routingMode.value = m
        prefs.edit().putString(K_ROUTING_MODE, m.name).apply()
    }
    private fun loadRoutingMode(): RoutingMode =
        prefs.getString(K_ROUTING_MODE, RoutingMode.All.name)?.let {
            runCatching { RoutingMode.valueOf(it) }.getOrDefault(RoutingMode.All)
        } ?: RoutingMode.All

    companion object {
        private const val PREF_FILE = "goloom_settings"
        private const val K_CONNECT_LAUNCH = "connect_on_launch"
        private const val K_AUTO_RECONNECT = "auto_reconnect"
        private const val K_BLOCK_IPV6 = "block_ipv6"
        private const val K_MTU = "mtu"
        private const val K_KEEPALIVE = "keepalive"
        private const val K_DNS = "dns_servers"
        private const val K_LANG = "language"
        private const val K_ROUTING_MODE = "routing_mode"

        @Volatile
        private var INSTANCE: SettingsManager? = null

        fun get(context: Context): SettingsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context).also { INSTANCE = it }
            }
    }
}
