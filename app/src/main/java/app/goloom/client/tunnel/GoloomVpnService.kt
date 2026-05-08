package app.goloom.client.tunnel

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import app.goloom.bridge.mobile.CaptchaSolver
import app.goloom.bridge.mobile.LogSink
import app.goloom.bridge.mobile.Mobile
import app.goloom.bridge.mobile.PhaseListener
import app.goloom.bridge.mobile.SocketProtector
import app.goloom.client.MainActivity
import app.goloom.client.R
import app.goloom.client.data.ConnectionState
import app.goloom.client.data.LogLevel
import app.goloom.client.data.LogSource
import app.goloom.client.data.LogStore
import app.goloom.client.data.ProfileStore
import app.goloom.client.data.SettingsManager
import app.goloom.client.data.AppListStore
import app.goloom.client.data.TunnelStats
import app.goloom.client.data.applyAppRouting
import android.os.ParcelFileDescriptor
import com.wireguard.config.Config
import app.goloom.client.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * VpnService, который оркестрирует пару Goloom-relay (Go SDK) +
 * WireGuard (com.wireguard.android:tunnel).
 *
 * Поток выполнения CONNECT:
 *  1. Создаём foreground notification "Goloom is running" (как требует
 *     Android для VpnService с targetSdk 35).
 *  2. Парсим встроенный в connstr WG-конфиг → wg-quick текст.
 *  3. Через [Mobile.newClient] поднимаем relay: socket protector навешан
 *     на VpnService.protect(), log sink — на [LogStore]. Connect()
 *     блокирует до handshake; вернёт JSON со списком telemost-IP.
 *  4. Билдим [VpnService.Builder] из адресов WG-интерфейса +
 *     telemost-IP как `/32` исключений (чтобы наши же ICE/SFU-сокеты
 *     не пошли в туннель).
 *  5. Per-app routing применяется через [applyAppRouting].
 *  6. WireGuard поднимаем через GoBackend — он сам зовёт Builder.establish().
 *  7. Tick-loop читает StatsJSON каждую секунду и публикует в Controller.
 */
class GoloomVpnService : VpnService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var connectJob: Job? = null
    private var statsJob: Job? = null

    private var goloomClient: app.goloom.bridge.mobile.Client? = null
    /**
     * Локальная ссылка на TUN-ParcelFileDescriptor. Если AdoptTun успешно
     * передал fd в Go — обнуляем (fd принадлежит Go), и close() мы НЕ зовём.
     * При ошибке до AdoptTun — закрываем сами в teardown.
     */
    private var tunFd: ParcelFileDescriptor? = null
    private var startedAt: Long = 0L

    /**
     * Профиль, с которым последний раз стартовали — нужен для
     * авто-реконнекта при смене сети. Обнуляется при Disconnect.
     */
    private var activeProfileId: String? = null
    private var networkObserverJob: Job? = null

    /**
     * Timestamp последней успешной попытки connect. Используется как
     * грубый throttle для авто-реконнектов: если новый network-change
     * случился < 10 сек назад, скорее всего, это эхо нашего же VPN
     * (вторичный callback после establish), а не реальная смена WiFi
     * → mobile. Throttle гасит reconnect-loop'ы.
     */
    private var lastReconnectAt: Long = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                val profileId = intent.getStringExtra(EXTRA_PROFILE_ID)
                if (profileId == null) {
                    LogStore.get(this).error(LogSource.APP, "CONNECT without profile id")
                    stopSelf(); return START_NOT_STICKY
                }
                startForegroundNotification()
                activeProfileId = profileId
                lastReconnectAt = System.currentTimeMillis()
                ensureNetworkObserver()
                // Если уже идёт сессия (например, рестарт из-за смены сети) —
                // teardown сначала, потом новый connect.
                connectJob?.cancel()
                connectJob = scope.launch {
                    if (goloomClient != null || tunFd != null) {
                        teardown()
                    }
                    connect(profileId)
                }
            }
            ACTION_DISCONNECT -> {
                activeProfileId = null
                networkObserverJob?.cancel(); networkObserverJob = null
                scope.launch {
                    teardown()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_RECONNECT -> {
                val pid = activeProfileId ?: run {
                    LogStore.get(this).warn(LogSource.APP, "RECONNECT without active profile — ignoring")
                    return START_STICKY
                }
                LogStore.get(this).info(LogSource.APP, "Network changed — reconnecting profile $pid")
                lastReconnectAt = System.currentTimeMillis()
                connectJob?.cancel()
                connectJob = scope.launch {
                    teardown()
                    connect(pid)
                }
            }
        }
        return START_STICKY
    }

    /**
     * Подписывается на смену default-network и при событии шлёт сам
     * себе ACTION_RECONNECT, если активна сессия и юзер не отключил
     * autoReconnect в настройках. Debounce 1 сек гасит burst при handover.
     */
    private fun ensureNetworkObserver() {
        if (networkObserverJob != null) return
        networkObserverJob = NetworkMonitor.get(this).networkChange
            .debounce(1_000)
            .onEach {
                if (activeProfileId == null) return@onEach
                if (!SettingsManager.get(this).autoReconnect.value) {
                    LogStore.get(this).info(
                        LogSource.APP,
                        "Network changed but autoReconnect=off — staying as-is",
                    )
                    return@onEach
                }
                // Throttle: между reconnect'ами должно пройти > 10s.
                // Без этого NetworkMonitor дёргается на каждое
                // capabilities-changed (WiFi validate/unvalidate, capacities
                // upgrades) и устраивает рекоектный шторм, который сервер
                // видит как непрерывный re-handshake.
                val sinceLast = System.currentTimeMillis() - lastReconnectAt
                if (lastReconnectAt > 0 && sinceLast < 10_000) {
                    LogStore.get(this).info(
                        LogSource.APP,
                        "Skipping network-change reconnect (last was ${sinceLast}ms ago)",
                    )
                    return@onEach
                }
                val intent = Intent(this, GoloomVpnService::class.java).apply {
                    action = ACTION_RECONNECT
                }
                startService(intent)
            }
            .launchIn(scope)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onRevoke() {
        // Юзер выключил VPN из системного диалога. Гасим всё.
        LogStore.get(this).warn(LogSource.APP, "VpnService revoked by system")
        scope.launch {
            teardown()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    // ─── connect / teardown ───────────────────────────────────

    private suspend fun connect(profileId: String) {
        val log = LogStore.get(this)
        val controller = GoloomController.get(this)
        controller.publishState(ConnectionState.Connecting(phase = "init", detail = null))

        val profile = ProfileStore.get(this).byId(profileId) ?: run {
            log.error(LogSource.APP, "Profile $profileId not found")
            controller.publishState(ConnectionState.Error("Profile not found"))
            stopSelf(); return
        }

        val parsed = profile.parsed
        if (!parsed.hasWireGuard) {
            log.error(LogSource.APP, "Profile has no embedded WG config — refusing")
            controller.publishState(ConnectionState.Error("WG config missing"))
            stopSelf(); return
        }

        // 1. Поднимаем Goloom-relay через gomobile bridge.
        val client = try {
            Mobile.newClient().also { c ->
                c.setSocketProtector(object : SocketProtector {
                    override fun protect(fd: Long): Boolean =
                        this@GoloomVpnService.protect(fd.toInt())
                })
                c.setLogSink(object : LogSink {
                    override fun write(line: String) {
                        log.add(LogLevel.INFO, LogSource.SDK, line)
                    }
                })
                // Phase updates → ConnectionState.Connecting(phase, detail).
                // UI MainScreen рендерит сменяющиеся подписи во время
                // долгого подключения (lobby auth, captcha, target connect, ...).
                c.setPhaseListener(object : PhaseListener {
                    override fun onPhase(phase: String, detail: String?) {
                        controller.publishState(ConnectionState.Connecting(phase, detail))
                    }
                })
                // VK lobby режим — meeting URL вводится пользователем
                // отдельно (Profile.vkTargetMeeting). Captcha solver
                // пока заглушка: возвращает ошибку — TODO WebView dialog.
                if (parsed.hasLobby) {
                    val target = profile.vkTargetMeeting
                    if (target.isNullOrBlank()) {
                        log.error(LogSource.APP, "VK lobby connstr но профиль без vkTargetMeeting")
                        controller.publishState(
                            ConnectionState.Error("Введи VK call link в настройках профиля")
                        )
                        stopSelf(); return
                    }
                    c.setVKTargetMeeting(target)
                    c.setCaptchaSolver(object : CaptchaSolver {
                        override fun solve(challengeURL: String): String {
                            // TODO: WebView dialog (см. tun/CaptchaWebViewDialog.kt).
                            // На первой итерации просто логируем и
                            // фейлимся — серверной стороне auto-replay
                            // обычно достаточно через captcha pool.
                            log.error(LogSource.APP, "Captcha required but no UI yet: $challengeURL")
                            throw Exception("Captcha solver not yet implemented")
                        }
                    })
                }
            }
        } catch (t: Throwable) {
            log.error(LogSource.APP, "SDK init failed: ${t.message}")
            controller.publishState(ConnectionState.Error(t.message ?: "SDK init failed"))
            stopSelf(); return
        }
        goloomClient = client

        log.info(LogSource.APP, "stage=A about to call Mobile.connect")
        val resultJson = try {
            // Connect блокирует до завершения handshake (минуты) — IO-диспетчер
            // обязателен, иначе наш Default-pool забивается.
            withContext(Dispatchers.IO) { client.connect(profile.connStr, LISTEN_ADDR) }
        } catch (t: Throwable) {
            log.error(LogSource.APP, "Goloom connect failed: ${t.message}")
            controller.publishState(ConnectionState.Error(t.message ?: "connect failed"))
            teardown()
            stopSelf(); return
        }
        log.info(LogSource.APP, "stage=B Mobile.connect returned (${resultJson.length} bytes)")

        val telemostIps = parseTelemostIps(resultJson)
        log.info(LogSource.APP, "stage=C telemost ips: ${telemostIps.size}")

        // 2. Собираем wg-quick конфиг inline и парсим в WireGuardConfig.
        val wgText = parsed.toWireGuardConfig() ?: run {
            log.error(LogSource.APP, "WG render returned empty")
            teardown(); stopSelf(); return
        }
        val wgConfig: Config = try {
            Config.parse(wgText.byteInputStream())
        } catch (t: Throwable) {
            log.error(LogSource.APP, "WG parse failed: ${t.message}")
            teardown(); stopSelf(); return
        }
        log.info(LogSource.APP, "stage=D WG config parsed")

        // 3. Сами строим VpnService.Builder и устанавливаем туннель — НЕ через
        //    GoBackend.setState (он создаёт свой VpnService и зависает в
        //    конфликте с нашим). Запускаем wg-go libwg через прямой JNI-вызов
        //    GoBackend.wgTurnOn (private method, достаём через reflection).
        val tunFdLocal: ParcelFileDescriptor = try {
            val settings = SettingsManager.get(this)
            val builder = Builder()
                .setSession(getString(R.string.app_name))
                .setMtu(settings.mtu.value)
                // IPv4 default-route, разделённый /1+/1 — стандартный паттерн
                // VPN, не ломает собственный underlying default route.
                .addRoute("0.0.0.0", 1)
                .addRoute("128.0.0.0", 1)
                // IPv6 default-route в наш TUN. Голум-сервер сейчас только
                // IPv4, поэтому IPv6-пакеты из приложений будут drop'нуты
                // в wg-userspace (нет AllowedIPs для них). Без этой строки
                // Chrome/etc отправляют через IPv6 в обход TUN — пользователь
                // видит свой реальный IPv6-адрес на сайтах-проверялках.
                .addRoute("::", 0)

            wgConfig.`interface`.addresses.forEach { addr ->
                builder.addAddress(addr.address, addr.mask)
            }
            wgConfig.`interface`.dnsServers.forEach { dns ->
                runCatching { builder.addDnsServer(dns.hostAddress) }
            }
            // КРИТИЧЕСКИ ВАЖНО: исключаем наш собственный package из VPN.
            // Иначе сокеты Pion/WebRTC внутри Goloom-relay (Telemost ICE/SFU
            // candidates на десятки IP) перехватываются TUN, ICE падает,
            // сессия разваливается. excludeRoute по IP не работает —
            // candidates приходят с разных адресов и появляются в рантайме.
            // Через addDisallowedApplication наш процесс полностью ходит
            // мимо VPN (включая wg-userspace bind на 127.0.0.1 и pion-сокеты),
            // а трафик ВСЕХ остальных приложений идёт через TUN.
            runCatching {
                builder.addDisallowedApplication(packageName)
            }
            applyAppRouting(
                builder = builder,
                mode = settings.routingMode.value,
                selected = AppListStore.get(this).selected.value,
                ownPackage = packageName,
            )

            log.info(LogSource.APP, "stage=E about to establish() VpnService")
            builder.establish() ?: run {
                log.error(LogSource.APP, "VpnService.Builder.establish() returned null")
                teardown(); stopSelf(); return
            }
        } catch (t: Throwable) {
            log.error(LogSource.APP, "VPN setup failed: ${t.message}")
            controller.publishState(ConnectionState.Error(t.message ?: "VPN setup failed"))
            teardown(); stopSelf(); return
        }
        tunFd = tunFdLocal
        log.info(LogSource.APP, "stage=F TUN fd=${tunFdLocal.fd}")

        // 4. Поднимаем wg-userspace, ВСТРОЕННЫЙ в наш .aar. Раньше использовали
        //    GoBackend.wgTurnOn из com.wireguard.android:tunnel, но он
        //    хардкодит путь /data/data/com.wireguard.android для UAPI-сокета —
        //    наш package туда писать не может, и горутина uapi падает в
        //    SIGSEGV. Pure-go wireguard-userspace внутри SDK обходит проблему.
        //
        // ВАЖНО: detachFd() переносит ownership с ParcelFileDescriptor на
        //   raw int. Без detach() PFD остаётся владельцем; когда его
        //   финализатор отработает (в любой момент), он закроет fd, и
        //   VpnService сразу свернёт TUN — иконка VPN исчезнет, а пакеты
        //   из приложений потеряются. detachFd() обязателен.
        val rawFd: Int = try {
            tunFdLocal.detachFd()
        } catch (t: Throwable) {
            log.error(LogSource.APP, "detachFd failed: ${t.message}")
            teardown(); stopSelf(); return
        }
        // PFD освобождён — наш tunFd теперь не должен его помнить.
        tunFd = null

        try {
            val userspaceCfg = wgConfig.toWgUserspaceString()
            log.info(LogSource.APP, "stage=G calling AdoptTun (cfg ${userspaceCfg.length} bytes, fd=$rawFd)")
            client.adoptTun(rawFd.toLong(), userspaceCfg)
            log.info(LogSource.APP, "stage=H wg-userspace adopted")
        } catch (t: Throwable) {
            log.error(LogSource.APP, "AdoptTun failed: ${t.message}")
            // fd уже detached — закрыть его не можем, надеемся что Go
            // заберёт владение даже на failed call (внутри AdoptTun
            // на любой ошибке закрывает fd).
            controller.publishState(ConnectionState.Error(t.message ?: "AdoptTun failed"))
            teardown(); stopSelf(); return
        }

        // 4. Tick-loop публикует stats каждую секунду.
        startedAt = System.currentTimeMillis()
        controller.publishState(ConnectionState.On(startedAt))
        statsJob = scope.launch {
            while (isActive) {
                publishStats()
                delay(1_000)
            }
        }
    }

    private fun publishStats() {
        val client = goloomClient ?: return
        val raw = runCatching { client.statsJSON() }.getOrNull() ?: return
        val obj = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val tx = obj.optLong("tx_bytes", 0)
        val rx = obj.optLong("rx_bytes", 0)
        val durationMs = if (startedAt == 0L) 0L else System.currentTimeMillis() - startedAt
        GoloomController.get(this).publishStats(TunnelStats(tx, rx, durationMs))
    }

    private fun teardown() {
        statsJob?.cancel(); statsJob = null
        connectJob?.cancel(); connectJob = null

        runCatching { goloomClient?.disconnect() }
        goloomClient = null

        // tunFd закрываем ТОЛЬКО если Go ещё не забрал его (т.е. AdoptTun
        // не дошёл или упал). Иначе будет double-close с UB.
        runCatching { tunFd?.close() }
        tunFd = null

        startedAt = 0L
        GoloomController.get(this).publishState(ConnectionState.Off)
        GoloomController.get(this).publishStats(TunnelStats())
    }

    // ─── notification ─────────────────────────────────────────

    private fun startForegroundNotification() {
        ensureChannel()
        val openAppPI = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnectPI = PendingIntent.getService(
            this,
            1,
            Intent(this, GoloomVpnService::class.java).apply { action = ACTION_DISCONNECT },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notif: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_running))
            .setSmallIcon(R.drawable.ic_launcher_eye)
            .setOngoing(true)
            .setShowWhen(false)
            .setContentIntent(openAppPI)
            .addAction(
                /* icon = */ 0,
                getString(R.string.notif_action_disconnect),
                disconnectPI,
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        startForeground(NOTIF_ID, notif)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
        }
        nm.createNotificationChannel(ch)
    }

    private fun parseTelemostIps(json: String): List<String> = runCatching {
        val obj = JSONObject(json)
        val arr = obj.optJSONArray("telemost_ips") ?: return@runCatching emptyList<String>()
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    companion object {
        const val ACTION_CONNECT = "app.goloom.client.action.CONNECT"
        const val ACTION_DISCONNECT = "app.goloom.client.action.DISCONNECT"
        const val ACTION_RECONNECT = "app.goloom.client.action.RECONNECT"
        const val EXTRA_PROFILE_ID = "profile_id"

        // Локальный UDP-listener Goloom-relay. WG.endpoint в connstr указывает
        // именно на этот адрес — поменять одновременно с админкой.
        private const val LISTEN_ADDR = "127.0.0.1:51820"
        private const val TUNNEL_NAME = "goloom"
        private const val CHANNEL_ID = "goloom_status"
        private const val NOTIF_ID = 1001
    }
}
