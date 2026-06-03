package app.goloom.client.data

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Информация об установленном приложении для AppRoutingScreen.
 */
data class AppListEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
)

/**
 * Хранит список выбранных пакетов для per-app split tunnel.
 * Семантика выборки зависит от [SettingsManager.routingMode]:
 *   - Whitelist — только эти пакеты идут в туннель
 *   - Blacklist — эти пакеты исключаются из туннеля
 *   - All       — set игнорируется, все идут в туннель
 *
 * Хранение — JSON-массив строк в SharedPreferences `goloom_routing`.
 */
class AppListStore private constructor(context: Context) {
    private val appContext = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private val _selected = MutableStateFlow(loadSelected())
    val selected: StateFlow<Set<String>> = _selected.asStateFlow()

    fun isSelected(pkg: String): Boolean = pkg in _selected.value

    fun toggle(pkg: String) {
        val cur = _selected.value
        _selected.value = if (pkg in cur) cur - pkg else cur + pkg
        save()
    }

    fun replace(set: Set<String>) {
        _selected.value = set
        save()
    }

    fun clear() {
        _selected.value = emptySet()
        save()
    }

    /**
     * Сканирует PackageManager и возвращает список приложений — БЕЗ иконок.
     * Иконки грузятся лениво через [loadIcon] по мере отображения строк: eager-
     * загрузка сотен Drawable блокировала список на несколько секунд, из-за чего
     * он казался пустым, пока не дочитается (отсюда баг «список появляется только
     * после ввода в поиск»). Теперь метаданные строятся быстро и список виден сразу.
     * Это блокирующий вызов — звать только из IO/Default диспетчера.
     * Своё приложение исключаем — нет смысла туннелировать самих себя.
     */
    fun loadInstalledApps(): List<AppListEntry> {
        val pm = appContext.packageManager
        val ownPkg = appContext.packageName
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchable = pm.queryIntentActivities(intent, 0)
            .mapTo(HashSet()) { it.activityInfo.packageName }

        val all = pm.getInstalledApplications(0)
            .filter { it.packageName != ownPkg }
            .map { ai ->
                AppListEntry(
                    packageName = ai.packageName,
                    label = runCatching { pm.getApplicationLabel(ai).toString() }
                        .getOrDefault(ai.packageName),
                    isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                            && ai.packageName !in launchable,
                )
            }
        return all.sortedWith(compareBy({ it.isSystem }, { it.label.lowercase() }))
    }

    /**
     * Лениво грузит иконку одного приложения. Блокирующий вызов — звать из IO.
     * null, если иконка недоступна (пакет удалён и т.п.).
     */
    fun loadIcon(pkg: String): Drawable? = runCatching {
        appContext.packageManager.getApplicationIcon(pkg)
    }.getOrNull()

    // ─── private ──────────────────────────────────────────────

    private fun loadSelected(): Set<String> {
        val raw = prefs.getString(K_SELECTED, null) ?: return emptySet()
        return runCatching {
            val arr = org.json.JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    private fun save() {
        val arr = org.json.JSONArray()
        _selected.value.forEach { arr.put(it) }
        prefs.edit().putString(K_SELECTED, arr.toString()).apply()
    }

    companion object {
        private const val PREF_FILE = "goloom_routing"
        private const val K_SELECTED = "selected_packages"

        @Volatile
        private var INSTANCE: AppListStore? = null

        fun get(context: Context): AppListStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: AppListStore(context).also { INSTANCE = it }
            }
    }
}

/**
 * Применяет выбор к [android.net.VpnService.Builder]:
 * см. [AppListStore.selected] и [SettingsManager.routingMode].
 *
 * Возвращает true, если в билдере были изменения (для трассировки в логе).
 */
fun applyAppRouting(
    builder: android.net.VpnService.Builder,
    mode: SettingsManager.RoutingMode,
    selected: Set<String>,
    ownPackage: String,
): Boolean {
    if (mode == SettingsManager.RoutingMode.All || selected.isEmpty()) return false
    when (mode) {
        SettingsManager.RoutingMode.Whitelist -> {
            selected.forEach { pkg ->
                if (pkg == ownPackage) return@forEach
                runCatching { builder.addAllowedApplication(pkg) }
            }
        }
        SettingsManager.RoutingMode.Blacklist -> {
            // Своё приложение всегда не туннелируем — не было бы кольца
            // через VpnService.protect для входящих SDK-сокетов.
            runCatching { builder.addDisallowedApplication(ownPackage) }
            selected.forEach { pkg ->
                if (pkg == ownPackage) return@forEach
                runCatching { builder.addDisallowedApplication(pkg) }
            }
        }
        SettingsManager.RoutingMode.All -> Unit
    }
    return true
}
