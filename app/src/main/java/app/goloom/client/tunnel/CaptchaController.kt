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
     * Вызывается когда WebView закрылся — либо пользователь решил
     * captcha (в этом случае Go-side proxy уже захватил token и
     * solveCaptchaViaProxy вернёт), либо пользователь свайпнул /
     * нажал Cancel. В обоих случаях UI просто гасит dialog.
     */
    fun dismiss() {
        _pendingUrl.value = null
    }
}
