package app.goloom.client.util

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Хранит результат последней проверки обновлений + dismiss-флаг (чтобы
 * после "Remind me later" не дёргать пользователя баннером каждый старт).
 *
 * Singleton — UI-слой и MainActivity цепляются за один [available] StateFlow.
 * Файла не пишем: при перезапуске процесса state теряется, что приемлемо —
 * следующий on-resume снова проверит сеть.
 */
class UpdateState private constructor(private val appContext: Context) {

    private val _available = MutableStateFlow<UpdateChecker.Result.Available?>(null)
    val available: StateFlow<UpdateChecker.Result.Available?> = _available.asStateFlow()

    private val _dismissed = MutableStateFlow<String?>(null)   // version-string
    val dismissed: StateFlow<String?> = _dismissed.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Не блокирующий fire-and-forget probe. Вызывать с
     * MainActivity.onResume — если в сети ничего нового, мы тихо
     * получаем UpToDate и баннер не появляется.
     */
    fun checkInBackground() {
        scope.launch {
            when (val r = UpdateChecker.check(appContext)) {
                is UpdateChecker.Result.Available -> {
                    if (r.version != _dismissed.value) {
                        _available.value = r
                    }
                }
                else -> Unit  // молча игнорируем UpToDate / Failed
            }
        }
    }

    fun dismiss(version: String) {
        _dismissed.value = version
        _available.value = null
    }

    companion object {
        @Volatile
        private var INSTANCE: UpdateState? = null

        fun get(context: Context): UpdateState =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: UpdateState(context.applicationContext).also { INSTANCE = it }
            }
    }
}
