package app.goloom.client.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Хранит коллекцию [Profile] в SharedPreferences (single JSON-array под ключом
 * "profiles"). Singleton-экземпляр получается через [get] — глобальный, чтобы
 * UI-слой и VpnService видели одни и те же данные без DI-фреймворка.
 *
 * Источник истины — память (StateFlow). Запись идёт apply-only (асинхронно)
 * — для UX это допустимо, потеря последней записи при кpaшe приемлема.
 */
class ProfileStore private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)

    private val _profiles = MutableStateFlow(load())
    val profiles: StateFlow<List<Profile>> = _profiles.asStateFlow()

    private val _activeId = MutableStateFlow(prefs.getString(KEY_ACTIVE, null))
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    /** Текущий "выбранный" профиль (то, на что указывает Profile pill). */
    fun active(): Profile? {
        val id = _activeId.value ?: return _profiles.value.firstOrNull()
        return _profiles.value.firstOrNull { it.id == id } ?: _profiles.value.firstOrNull()
    }

    fun setActive(id: String?) {
        _activeId.value = id
        prefs.edit().putString(KEY_ACTIVE, id).apply()
    }

    fun add(profile: Profile) {
        val updated = _profiles.value + profile
        _profiles.value = updated
        save(updated)
        // Если активного нет — назначаем новоприбывший.
        if (_activeId.value == null) setActive(profile.id)
    }

    fun update(profile: Profile) {
        val updated = _profiles.value.map {
            if (it.id == profile.id) profile.copy(updatedAt = System.currentTimeMillis()) else it
        }
        _profiles.value = updated
        save(updated)
    }

    fun delete(id: String) {
        val updated = _profiles.value.filter { it.id != id }
        _profiles.value = updated
        save(updated)
        if (_activeId.value == id) {
            setActive(updated.firstOrNull()?.id)
        }
    }

    fun byId(id: String): Profile? = _profiles.value.firstOrNull { it.id == id }

    // ─── private ──────────────────────────────────────────────

    private fun load(): List<Profile> {
        val raw = prefs.getString(KEY_PROFILES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { Profile.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun save(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_PROFILES, arr.toString()).apply()
    }

    companion object {
        private const val PREF_FILE = "goloom_profiles"
        private const val KEY_PROFILES = "profiles"
        private const val KEY_ACTIVE = "active_id"

        @Volatile
        private var INSTANCE: ProfileStore? = null

        fun get(context: Context): ProfileStore =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProfileStore(context).also { INSTANCE = it }
            }
    }
}
