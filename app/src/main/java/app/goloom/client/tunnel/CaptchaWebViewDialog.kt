package app.goloom.client.tunnel

import android.annotation.SuppressLint
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay

/**
 * Compose dialog с WebView для прохождения VK captcha.
 *
 * URL — это localhost-адрес reverse-proxy, поднятого на Go-стороне
 * через [vkcalls.AutoProxyCaptchaSolverWithOpener]. Proxy уже:
 *   - проксирует запросы на id.vk.com
 *   - инжектит свой JS shim, который захватывает success_token из
 *     captchaNotRobot.check ответа и POST'ит его обратно proxy-серверу
 *   - после успеха меняет body на "Готово!" + window.close()
 *
 * Поэтому здесь нам остаётся:
 *   - показать WebView с правильным мобильным UA + anti-bot JS
 *     (иначе VK SDK пометит браузер как bot и капча не пройдёт)
 *   - детектить "Готово" / "Done" в body → закрыть dialog
 *   - дать пользователю кнопку Cancel
 *
 * Реализация anti-bot маскировки лифтнута из tun/CaptchaWebViewDialog.kt
 * (который у юзера уже работал в проде).
 *
 * Когда dialog закрывается, Go-сторона уже видела success_token (или
 * не видела — тогда auth ladder таймаутится и фейлится). Native не
 * передаёт токены в Go — это всё внутри Go.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWebViewDialog(
    url: String,
    onDismiss: () -> Unit,
) {
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var dismissed by remember { mutableStateOf(false) }

    fun dismissOnce() {
        if (!dismissed) {
            dismissed = true
            onDismiss()
        }
    }

    // Polling: смотрим body innerText, ловим "Готово/Done/closing" →
    // dismiss. Те же эвристики что в tun/.
    LaunchedEffect(Unit) {
        while (!dismissed) {
            delay(250)
            webViewRef?.evaluateJavascript(
                "(document.body && document.body.innerText) || ''",
            ) { result ->
                val cleaned = result?.trim('"').orEmpty()
                if (cleaned.contains("Готово") ||
                    cleaned.contains("Done!") ||
                    cleaned.contains("close the page", ignoreCase = true)
                ) {
                    dismissOnce()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { dismissOnce() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF1A1A1F))
                .padding(12.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Решите captcha VK",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Start,
                )
                OutlinedButton(onClick = { dismissOnce() }) {
                    Text("Отмена", fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White),
            ) {
                AndroidView(
                    factory = { context ->
                        WebView.setWebContentsDebuggingEnabled(true)
                        WebView(context).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.userAgentString = MOBILE_UA
                            // Identity rotation: WebView должен выглядеть как
                            // СВЕЖАЯ установка для каждого dialog'а. Без этого
                            // VK помнит cookies (id.vk.com session, captcha
                            // tokens) с прошлой попытки и палит как 'тот же
                            // user только что прошёл — bot'? Симптом — почти
                            // мгновенный 'Не удалось пройти проверку'.
                            // (Так же пофиксило в tun/ via v1.2.0
                            // 'identity rotation in manual captcha').
                            clearCache(true)
                            clearHistory()
                            clearFormData()
                            val cm = android.webkit.CookieManager.getInstance()
                            cm.removeAllCookies(null)
                            cm.flush()
                            cm.setAcceptCookie(true)
                            cm.setAcceptThirdPartyCookies(this, true)
                            // DOM storage / cache (IndexedDB / Web SQL etc).
                            android.webkit.WebStorage.getInstance().deleteAllData()

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    super.onPageStarted(view, url, favicon)
                                    // Anti-bot маскировка ДО загрузки VK SDK.
                                    // Лифтнуто из tun/CaptchaWebViewDialog.kt:285.
                                    view?.evaluateJavascript(ANTI_BOT_JS) { _ -> }
                                }
                            }

                            loadUrl(url)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.fillMaxFromInstance(),
                )
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { webViewRef?.destroy() }
    }
}

private fun Modifier.fillMaxFromInstance(): Modifier = this.fillMaxWidth().fillMaxHeight()

// Мобильный UA — на мобильной сети VK ожидает мобильное устройство;
// desktop UA + IP мобильного оператора был сильным bot-маркером в
// tun/. Pixel 8 / Chrome 146 — стабильный набор.
private const val MOBILE_UA =
    "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/146.0.0.0 Mobile Safari/537.36"

// Anti-bot JS-инъекция — то же что tun/CaptchaWebViewDialog.kt:285-325.
// VK SDK при загрузке проверяет navigator.webdriver/plugins/etc; без
// маскировки немедленно ставит флаг bot и captcha не проходит.
private val ANTI_BOT_JS = """
(function() {
  try { Object.defineProperty(navigator, 'webdriver', { get: () => false, configurable: true }); } catch(e) {}
  try {
    Object.defineProperty(navigator, 'platform', { get: () => 'Linux armv8l' });
    Object.defineProperty(navigator, 'maxTouchPoints', { get: () => 5 });
    Object.defineProperty(navigator, 'plugins', {
      get: () => [{name:'PDF Viewer'},{name:'Chrome PDF Viewer'},{name:'Chromium PDF Viewer'},{name:'Microsoft Edge PDF Viewer'},{name:'WebKit built-in PDF'}]
    });
    Object.defineProperty(navigator, 'languages', { get: () => ['ru-RU','ru','en-US','en'] });
    if (navigator.userAgentData) {
      try {
        Object.defineProperty(navigator.userAgentData, 'mobile', { get: () => true });
        Object.defineProperty(navigator.userAgentData, 'platform', { get: () => 'Android' });
      } catch(e) {}
    }
  } catch(e) {}
  try {
    if (!window.chrome) {
      window.chrome = { runtime: {}, app: { isInstalled: false }, csi: function(){return {}}, loadTimes: function(){return {}} };
    }
  } catch(e) {}
  try {
    var origQuery = navigator.permissions && navigator.permissions.query;
    if (origQuery) {
      navigator.permissions.query = function(p) {
        if (p && p.name === 'notifications') {
          return Promise.resolve({ state: 'prompt' });
        }
        return origQuery.apply(navigator.permissions, arguments);
      };
    }
  } catch(e) {}
})();
""".trimIndent()
