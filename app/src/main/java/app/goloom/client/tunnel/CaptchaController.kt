package app.goloom.client.tunnel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Связь между Goloom Go-сторонним captcha solver'ом и UI WebView
 * dialog'ом.
 *
 * Когда Go запускает локальный reverse-proxy для captcha и зовёт
 * [app.goloom.bridge.mobile.BrowserLauncher.Open(url)] (в нашем
 * случае реализованном в [GoloomVpnService]), launcher просто
 * публикует URL в [pendingUrl]. UI наблюдает за этим StateFlow и
 * показывает [CaptchaWebViewDialog] на этом URL'е.
 *
 * Token capture происходит на Go-стороне через JS shim в proxy:
 * как только пользователь кликнул "I'm not a robot", proxy POST'ит
 * успех на свой `/local-captcha-result`, и solveCaptchaViaProxy
 * горутина возвращается с токеном. Native ничего не парсит — только
 * показывает WebView и реагирует на window.close()/dismiss.
 *
 * Singleton потому что Service и Activity живут в разных
 * lifecycle'ах но в одном процессе; общий StateFlow проще чем
 * bound IPC.
 */
object CaptchaController {

    private val _pendingUrl = MutableStateFlow<String?>(null)

    /** URL captcha-WebView, который надо показать. null = ничего активного. */
    val pendingUrl: StateFlow<String?> = _pendingUrl.asStateFlow()

    /**
     * Вызывается из Go BrowserLauncher.Open(). UI Composable собирает
     * [pendingUrl] через collectAsState и рендерит dialog.
     */
    fun present(url: String) {
        _pendingUrl.value = url
    }

    /**
     * Обработчик success_token'а. Ставит [GoloomVpnService] — он владеет
     * Go-клиентом и дёргает у него submitVKCaptchaToken().
     *
     * Нужен потому, что капча теперь грузится с настоящего id.vk.ru, а не
     * с localhost-прокси: Go больше не видит ответ captchaNotRobot.check и
     * узнаёт токен только от native.
     */
    @Volatile
    var onToken: ((token: String, pageUrl: String) -> Unit)? = null

    /**
     * Обработчик отпечатка (device + browser_fp + UA), снятого со страницы
     * captcha. Ставит [GoloomVpnService]; уходит в пул на Go-стороне, чтобы
     * следующий коннект прошёл captcha без UI.
     */
    @Volatile
    var onProfile: ((String, String, String) -> Unit)? = null

    /** Вызывается из JS-моста в [CaptchaWebViewDialog]. */
    fun submitProfile(device: String, browserFp: String, userAgent: String) {
        val handler = onProfile
        if (handler == null) {
            // Пропущенная регистрация здесь не ломает подключение (captcha всё
            // равно решается вручную), но пул молча остаётся пустым навсегда —
            // тот самый симптом «0 profiles после десятков решений». Без лога
            // это неотличимо от «VK не отдаёт отпечаток».
            android.util.Log.w("CaptchaCtl", "fingerprint dropped: no onProfile handler registered")
            return
        }
        handler(device, browserFp, userAgent)
    }

    /**
     * Вызывается из JS-моста в [CaptchaWebViewDialog], когда со страницы
     * VK прилетел success_token. Токен уходит в Go, dialog закрывается.
     */
    fun submitToken(token: String, pageUrl: String) {
        if (token.isBlank()) return
        val handler = onToken
        if (handler == null) {
            // Каждый connect-путь обязан выставить onToken рядом со своим
            // setBrowserLauncher. Если забыть — капча решается, токен ловится,
            // а Go молча ждёт до таймаута; лог делает промах очевидным.
            android.util.Log.w("CaptchaCtl", "success_token dropped: no onToken handler registered")
            dismiss()
            return
        }
        handler(token, pageUrl)
        dismiss()
    }

    /**
     * Вызывается когда WebView закрылся — либо пользователь решил
     * captcha (в этом случае Go-side proxy уже захватил token и
     * solveCaptchaViaProxy вернёт), либо пользователь свайпнул /
     * нажал Cancel. В обоих случаях UI просто гасит dialog.
     */
    fun dismiss() {
        _pendingUrl.value = null
    }
}
