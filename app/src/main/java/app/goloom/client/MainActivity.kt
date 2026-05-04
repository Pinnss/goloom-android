package app.goloom.client

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.goloom.client.data.LogSource
import app.goloom.client.data.LogStore
import app.goloom.client.data.Profile
import app.goloom.client.data.ProfileStore
import app.goloom.client.design.G
import app.goloom.client.design.GoloomTheme
import app.goloom.client.screens.AboutScreen
import app.goloom.client.screens.AppRoutingScreen
import app.goloom.client.screens.ImportSheet
import app.goloom.client.screens.LogsScreen
import app.goloom.client.screens.MainScreen
import app.goloom.client.screens.ParametersScreen
import app.goloom.client.screens.ProfileDetailsScreen
import app.goloom.client.screens.ProfileEditScreen
import app.goloom.client.screens.ProfilesScreen
import app.goloom.client.screens.SettingsScreen
import app.goloom.client.screens.UpdatesScreen
import app.goloom.client.tunnel.GoloomController
import app.goloom.client.util.DeepLink
import app.goloom.client.util.UpdateState
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingProfileId: String? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val id = pendingProfileId ?: return@registerForActivityResult
        pendingProfileId = null
        if (result.resultCode == Activity.RESULT_OK) {
            val profile = ProfileStore.get(this).byId(id)
            if (profile != null) GoloomController.get(this).requestConnect(profile)
        } else {
            LogStore.get(this).warn(LogSource.APP, "VPN permission denied by user")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Сохраним deep-link для импорта при холодном старте.
        val deepImported = DeepLink.extractConnStr(intent)?.let { parsed ->
            val store = ProfileStore.get(this)
            val profile = Profile.fromConnStr(parsed)
            store.add(profile)
            store.setActive(profile.id)
            LogStore.get(this).info(LogSource.APP, "Deep-link imported profile ${profile.id}")
            profile
        }

        setContent {
            GoloomTheme {
                AppRoot(
                    deepImportName = deepImported?.name,
                    onConnect = ::tryConnect,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Тихая проверка обновлений в фоне. UpdateState сам решит,
        // показывать ли баннер (учитывает dismiss-флаг).
        UpdateState.get(this).checkInBackground()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLink.extractConnStr(intent)?.let { parsed ->
            val store = ProfileStore.get(this)
            val profile = Profile.fromConnStr(parsed)
            store.add(profile)
            store.setActive(profile.id)
            LogStore.get(this).info(LogSource.APP, "Deep-link imported profile ${profile.id}")
        }
    }

    /**
     * Запрашивает VPN-разрешение, если нужно, иначе сразу стартует.
     * Используется как из MainScreen, так и из обработчика deep-link.
     */
    fun tryConnect(profile: Profile) {
        val controller = GoloomController.get(this)
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            pendingProfileId = profile.id
            vpnPermissionLauncher.launch(prepare)
        } else {
            controller.requestConnect(profile)
        }
    }
}

/**
 * Корневая навигация. Стартовый экран — Main; back-кнопка возвращает на Main.
 */
@Composable
private fun AppRoot(
    deepImportName: String?,
    onConnect: (Profile) -> Unit,
) {
    val context = LocalContext.current
    var screen: Screen by remember { mutableStateOf(Screen.Main) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Snackbar для deep-link import (если есть).
    LaunchedEffect(deepImportName) {
        if (deepImportName != null) {
            snackbarHostState.showSnackbar(
                message = context.getString(R.string.import_success, deepImportName),
                duration = SnackbarDuration.Short,
            )
        }
    }

    // Системная кнопка "Назад" должна повторять навигацию из top-bar'а:
    // Settings → Main, Logs → Settings и т.д. Иначе на любом экране
    // back закрывает приложение, что для староверов с 3-button nav особенно
    // раздражает. Маппинг должен совпадать с `onBack` лямбдами в when-блоке
    // ниже — чтобы был один источник истины. Если screen=Main, отдаём
    // управление системе (она закроет app штатно).
    val goBack: (() -> Unit)? = when (val s = screen) {
        Screen.Main -> null
        Screen.Profiles -> { -> screen = Screen.Main }
        is Screen.ProfileDetails -> { -> screen = Screen.Profiles }
        is Screen.ProfileEdit -> { -> screen = Screen.ProfileDetails(s.profileId) }
        Screen.ImportSheet -> { -> screen = Screen.Main }
        Screen.Settings -> { -> screen = Screen.Main }
        Screen.Logs -> { -> screen = Screen.Settings }
        Screen.Parameters -> { -> screen = Screen.Settings }
        Screen.Updates -> { -> screen = Screen.Settings }
        Screen.About -> { -> screen = Screen.Settings }
        Screen.AppRouting -> { -> screen = Screen.Settings }
    }
    BackHandler(enabled = goBack != null) { goBack?.invoke() }

    Box(modifier = Modifier.fillMaxSize().background(G.bg)) {
        when (val s = screen) {
            Screen.Main -> MainScreen(
                onOpenSettings = { screen = Screen.Settings },
                onOpenImport = { screen = Screen.ImportSheet },
                onOpenProfiles = { screen = Screen.Profiles },
                onConnect = onConnect,
                onOpenUpdates = { screen = Screen.Updates },
            )
            Screen.Profiles -> ProfilesScreen(
                onBack = { screen = Screen.Main },
                onOpenProfile = { id -> screen = Screen.ProfileDetails(id) },
                onOpenImport = { screen = Screen.ImportSheet },
            )
            is Screen.ProfileDetails -> ProfileDetailsScreen(
                profileId = s.profileId,
                onBack = { screen = Screen.Profiles },
                onEdit = { id -> screen = Screen.ProfileEdit(id) },
                onDeleted = { screen = Screen.Profiles },
                onShareSnack = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
            )
            is Screen.ProfileEdit -> ProfileEditScreen(
                profileId = s.profileId,
                onBack = { screen = Screen.ProfileDetails(s.profileId) },
            )
            Screen.ImportSheet -> ImportSheet(
                onDismiss = { screen = Screen.Main },
                onImported = { profileName ->
                    screen = Screen.Main
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = context.getString(R.string.import_success, profileName),
                            duration = SnackbarDuration.Short,
                        )
                    }
                },
                onError = { msg ->
                    scope.launch { snackbarHostState.showSnackbar(msg) }
                },
            )
            Screen.Settings -> SettingsScreen(
                onBack = { screen = Screen.Main },
                onOpenLogs = { screen = Screen.Logs },
                onOpenParams = { screen = Screen.Parameters },
                onOpenAbout = { screen = Screen.About },
                onOpenUpdates = { screen = Screen.Updates },
                onOpenAppRouting = { screen = Screen.AppRouting },
            )
            Screen.Logs -> LogsScreen(onBack = { screen = Screen.Settings })
            Screen.Parameters -> ParametersScreen(onBack = { screen = Screen.Settings })
            Screen.Updates -> UpdatesScreen(onBack = { screen = Screen.Settings })
            Screen.About -> AboutScreen(onBack = { screen = Screen.Settings })
            Screen.AppRouting -> AppRoutingScreen(onBack = { screen = Screen.Settings })
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp),
        ) { data ->
            Snackbar(
                containerColor = G.bgElev2,
                contentColor = G.text,
            ) {
                Text(data.visuals.message)
            }
        }
    }
}
