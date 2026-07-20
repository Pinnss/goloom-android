package app.goloom.client.tunnel

import android.annotation.SuppressLint
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.goloom.client.data.LogSource
import app.goloom.client.data.LogStore
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
    val appContext = LocalContext.current
    val log = remember(appContext) { LogStore.get(appContext) }

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
                                    android.util.Log.i(TAG_DLG, "page started: ${url?.take(120)}")
                                    // Anti-bot маскировка ДО загрузки VK SDK.
                                    // Лифтнуто из tun/CaptchaWebViewDialog.kt:285.
                                    view?.evaluateJavascript(ANTI_BOT_JS) { _ -> }
                                    // Перехват success_token. Страница теперь
                                    // грузится с настоящего id.vk.ru, без нашего
                                    // прокси, поэтому ответ captchaNotRobot.check
                                    // видит только WebView — хук обязан встать
                                    // ДО того, как VK SDK создаст свои XHR.
                                    view?.evaluateJavascript(TOKEN_HOOK_JS) { _ -> }
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    // Тело страницы: пустой body здесь = VK отдал
                                    // blank-страницу (потраченная сессия и т.п.) —
                                    // главный симптом «белого экрана». Логируем
                                    // размер/число детей/первые символы текста.
                                    view?.evaluateJavascript(
                                        "JSON.stringify({len:document.documentElement.outerHTML.length," +
                                            "kids:document.body?document.body.children.length:-1," +
                                            "txt:((document.body&&document.body.innerText)||'').slice(0,80)})",
                                    ) { res ->
                                        val msg = "[Captcha WV] finished ${url?.take(80)} dom=${res?.take(200)}"
                                        android.util.Log.i(TAG_DLG, msg)
                                        log.info(LogSource.APP, msg)
                                    }
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?,
                                ) {
                                    super.onReceivedError(view, request, error)
                                    val main = request?.isForMainFrame == true
                                    val msg = "[Captcha WV] error mainFrame=$main code=${error?.errorCode} " +
                                        "desc=${error?.description} url=${request?.url}"
                                    android.util.Log.w(TAG_DLG, msg)
                                    log.warn(LogSource.APP, msg)
                                }

                                override fun onReceivedHttpError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    errorResponse: WebResourceResponse?,
                                ) {
                                    super.onReceivedHttpError(view, request, errorResponse)
                                    val msg = "[Captcha WV] http ${errorResponse?.statusCode} " +
                                        "${errorResponse?.reasonPhrase} url=${request?.url}"
                                    android.util.Log.w(TAG_DLG, msg)
                                    log.warn(LogSource.APP, msg)
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                                    val line = "[Captcha JS] [${msg?.messageLevel()}] ${msg?.message()} " +
                                        "(${msg?.sourceId()}:${msg?.lineNumber()})"
                                    android.util.Log.i(TAG_DLG, line)
                                    log.info(LogSource.APP, line)
                                    return true
                                }
                            }

                            addJavascriptInterface(CaptchaTokenBridge(log), "GoloomCaptcha")
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

/**
 * JS-мост: страница VK отдаёт сюда success_token, вытащенный из ответа
 * captchaNotRobot.check. Дальше [CaptchaController] передаёт его в Go и
 * гасит dialog.
 *
 * Раньше токен ловил Go-прокси на localhost, но именно этот прокси и
 * ломал капчу (CORS для adFp + чужой JA3), поэтому теперь страница
 * грузится напрямую с id.vk.ru, а токен возвращает native.
 */
private class CaptchaTokenBridge(private val log: LogStore) {
    @android.webkit.JavascriptInterface
    fun submitToken(token: String?) {
        val t = token.orEmpty()
        if (t.isBlank()) return
        android.util.Log.i(TAG_DLG, "[Captcha WV] success_token captured (${t.length} chars)")
        log.info(LogSource.APP, "[Captcha WV] success_token captured (${t.length} chars)")
        CaptchaController.submitToken(t)
    }

    /**
     * Отпечаток со страницы captcha: `device` из componentDone + `browser_fp`
     * из check. Уходит в пул на Go-стороне, чтобы следующий коннект прошёл
     * captcha автоматически, без показа WebView.
     */
    @android.webkit.JavascriptInterface
    fun submitProfile(device: String?, browserFp: String?, userAgent: String?) {
        val d = device.orEmpty()
        val b = browserFp.orEmpty()
        if (d.isBlank() || b.isBlank()) return
        log.info(LogSource.APP, "[Captcha WV] fingerprint captured (device=${d.length}B browser_fp=${b.length}B)")
        CaptchaController.submitProfile(d, b, userAgent.orEmpty())
    }
}

private const val TAG_DLG = "CaptchaWV"

// Хук XHR/fetch: ждём ответ captchaNotRobot.check и выдёргиваем из него
// response.success_token. Ставится в onPageStarted, до скриптов VK.
private val TOKEN_HOOK_JS = """
(function() {
  if (window.__goloomTokenHook) return;
  window.__goloomTokenHook = true;
  function post(t) {
    try { if (t && window.GoloomCaptcha) window.GoloomCaptcha.submitToken(t); } catch (e) {}
  }
  function scan(txt) {
    try {
      var d = JSON.parse(txt);
      if (d && d.response && d.response.success_token) post(d.response.success_token);
    } catch (e) {}
  }
  function isCheck(u) { return typeof u === 'string' && u.indexOf('captchaNotRobot.check') !== -1; }
  function isFpCall(u) {
    return typeof u === 'string' &&
      (u.indexOf('captchaNotRobot.check') !== -1 || u.indexOf('captchaNotRobot.componentDone') !== -1);
  }

  // Отпечаток VK раскладывает по ДВУМ запросам: device приезжает в
  // componentDone, browser_fp — в check. Go-сторона отбрасывает профиль с
  // любым пустым полем, поэтому копим половинки и шлём одним вызовом.
  var fpDevice = '', fpBrowser = '', sentDevice = '', sentBrowser = '';
  function field(body, name) {
    if (typeof body !== 'string') return '';
    var m = body.match(new RegExp('(?:^|&)' + name + '=([^&]*)'));
    if (!m || !m[1]) return '';
    try { return decodeURIComponent(m[1].replace(/\+/g, ' ')); } catch (e) { return ''; }
  }
  function harvest(body) {
    var d = field(body, 'device');
    var b = field(body, 'browser_fp');
    if (d) fpDevice = d;
    if (b) fpBrowser = b;
    if (!fpDevice || !fpBrowser) return;
    // Накопители НЕ обнуляем: componentDone (с device) приходит один раз на
    // инициализацию виджета, а check (с browser_fp) — на каждую попытку.
    // Сбросив device после первой отправки, мы бы потеряли пару от повторной,
    // УСПЕШНОЙ попытки и оставили в пуле отпечаток той, что VK забраковал.
    // Вместо этого дедуплицируем по уже отправленной паре.
    if (fpDevice === sentDevice && fpBrowser === sentBrowser) return;
    try {
      if (window.GoloomCaptcha && window.GoloomCaptcha.submitProfile) {
        window.GoloomCaptcha.submitProfile(fpDevice, fpBrowser, navigator.userAgent);
        sentDevice = fpDevice; sentBrowser = fpBrowser;
      }
    } catch (e) {}
  }

  try {
    var ox = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(m, u) { this.__gu = u; return ox.apply(this, arguments); };
    var os = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(body) {
      var x = this;
      try {
        if (isFpCall(String(x.__gu))) harvest(body);
        x.addEventListener('load', function() {
          // try/catch ОБЯЗАН быть внутри обработчика: внешний уже завершился к
          // моменту события, а responseText кидает InvalidStateError, если
          // responseType не '' и не 'text'. Исключение из обработчика браузер
          // глотает — токен потерялся бы совершенно молча, и Go ждал бы его
          // до двухминутного таймаута.
          try {
            if (!isCheck(String(x.__gu))) return;
            var rt = x.responseType;
            var payload = (rt === '' || rt === 'text') ? x.responseText : x.response;
            if (typeof payload === 'string') {
              scan(payload);
            } else if (payload && payload.response && payload.response.success_token) {
              post(payload.response.success_token);
            }
          } catch (e) {}
        });
      } catch (e) {}
      return os.apply(this, arguments);
    };
  } catch (e) {}
  try {
    var of = window.fetch;
    if (of) {
      window.fetch = function() {
        var a = arguments[0];
        var u = (a && typeof a === 'object' && a.url) ? a.url : a;
        var init = arguments[1];
        try {
          if (isFpCall(u) && init && typeof init.body === 'string') harvest(init.body);
        } catch (e) {}
        var p = of.apply(this, arguments);
        if (isCheck(u)) {
          try { p.then(function(r) { r.clone().text().then(scan); }); } catch (e) {}
        }
        return p;
      };
    }
  } catch (e) {}
})();
""".trimIndent()

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
