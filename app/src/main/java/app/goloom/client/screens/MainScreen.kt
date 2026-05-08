package app.goloom.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goloom.client.R
import app.goloom.client.data.ConnectionState
import app.goloom.client.data.Profile
import app.goloom.client.data.ProfileStore
import app.goloom.client.data.TunnelStats
import app.goloom.client.design.G
import app.goloom.client.design.GButton
import app.goloom.client.design.GButtonVariant
import app.goloom.client.design.GConnectButton
import app.goloom.client.design.GDot
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GUpdateBanner
import app.goloom.client.design.GoloomLogo
import app.goloom.client.design.GMetrics
import app.goloom.client.design.GProfilePill
import app.goloom.client.design.GTopBar
import app.goloom.client.tunnel.GoloomController
import app.goloom.client.util.UpdateState

@Composable
fun MainScreen(
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenProfiles: () -> Unit,
    onConnect: (Profile) -> Unit,
    onOpenUpdates: () -> Unit = onOpenSettings,
) {
    val context = LocalContext.current
    val store = remember { ProfileStore.get(context) }
    val controller = remember { GoloomController.get(context) }
    val updates = remember { UpdateState.get(context) }

    val profiles by store.profiles.collectAsState()
    val activeId by store.activeId.collectAsState()
    val state by controller.state.collectAsState()
    val stats by controller.stats.collectAsState()
    val pendingUpdate by updates.available.collectAsState()

    val active = remember(profiles, activeId) {
        profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
    }

    var showProfileSheet by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(G.bg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        GTopBar(
            left = { GoloomLogo(size = 16.dp) },
            right = {
                GIconBtn(
                    onClick = onOpenImport,
                    enabled = !state.isActive,
                    icon = { Icon(GIcons.Plus, null, tint = G.text) },
                )
                GIconBtn(
                    onClick = onOpenSettings,
                    icon = { Icon(GIcons.Settings, null, tint = G.text) },
                )
            },
        )

        // Notification slot — без баннера высота 0, не сдвигает контент.
        // Если UpdateChecker нашёл новую версию и юзер её ещё не скрывал —
        // показываем компактный баннер с тапом на экран обновлений.
        Box(modifier = Modifier.padding(horizontal = 18.dp)) {
            val pu = pendingUpdate
            GUpdateBanner(
                visible = pu != null,
                version = pu?.version.orEmpty(),
                title = stringResource(R.string.updates_banner_title),
                sub = stringResource(R.string.updates_banner_sub),
                actionText = stringResource(R.string.updates_banner_action),
                onAction = onOpenUpdates,
            )
        }

        // Profile pill — есть только если есть профили
        if (active != null) {
            Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp)) {
                GProfilePill(
                    name = active.name,
                    server = active.serverDisplay,
                    profileLabel = stringResource(R.string.main_profile_label),
                    enabled = !state.isActive,
                    onClick = { showProfileSheet = true },
                )
            }
        } else {
            Text(
                text = stringResource(R.string.main_no_profiles),
                color = G.textDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                textAlign = TextAlign.Center,
                style = TextStyle(fontSize = 14.sp),
            )
        }

        // Connect dial — вертикально центрирован в оставшемся пространстве.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GConnectButton(
                state = state.asEyeState,
                size = GMetrics.connectDial,
                onClick = {
                    if (state is ConnectionState.Off || state is ConnectionState.Error) {
                        active?.let(onConnect) ?: run {
                            // Пользователь без профилей — открыть импорт.
                            onOpenImport()
                        }
                    } else {
                        controller.requestDisconnect()
                    }
                },
            )
            Spacer(modifier = Modifier.height(28.dp))
            StatusCaption(state = state, stats = stats)
        }

        ExitIpCard(state = state)
    }

    if (showProfileSheet) {
        ProfileSwitcherSheet(
            profiles = profiles,
            activeId = activeId,
            onSelect = { id ->
                store.setActive(id)
                showProfileSheet = false
            },
            onAddNew = {
                showProfileSheet = false
                onOpenImport()
            },
            onManage = {
                showProfileSheet = false
                onOpenProfiles()
            },
            onDismiss = { showProfileSheet = false },
        )
    }
}

@Composable
private fun StatusCaption(state: ConnectionState, stats: TunnelStats) {
    val (kicker, label, sub, color) = when (state) {
        is ConnectionState.Off -> Quad(
            stringResource(R.string.main_kicker_status),
            stringResource(R.string.main_status_off),
            stringResource(R.string.main_sub_off),
            G.textDim,
        )
        is ConnectionState.Connecting -> Quad(
            stringResource(R.string.main_kicker_status),
            state.humanLabel(),
            state.detail ?: stringResource(R.string.main_sub_connecting),
            G.warn,
        )
        is ConnectionState.On -> Quad(
            stringResource(R.string.main_kicker_connected),
            formatDuration(stats.durationMs),
            stringResource(R.string.main_sub_on),
            G.text,
        )
        is ConnectionState.Error -> Quad(
            stringResource(R.string.main_kicker_status),
            stringResource(R.string.main_status_error),
            state.message,
            G.err,
        )
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = kicker,
            color = G.textMute,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
            ),
        )
        Spacer(modifier = Modifier.height(4.dp))
        val labelStyle = if (state is ConnectionState.On) {
            app.goloom.client.design.MonoTimer.copy(color = color)
        } else {
            TextStyle(
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = (-0.2).sp,
                color = color,
            )
        }
        Text(text = label, style = labelStyle)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = sub, color = G.textDim, style = TextStyle(fontSize = 13.sp))
    }
}

private data class Quad(val kicker: String, val label: String, val sub: String, val color: androidx.compose.ui.graphics.Color)

private fun formatDuration(ms: Long): String {
    if (ms <= 0) return "00:00:00"
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return "%02d:%02d:%02d".format(h, m, s)
}

@Composable
private fun ExitIpCard(state: ConnectionState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 28.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(G.bgElev1)
            .border(1.dp, G.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(G.bgElev3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GIcons.Globe, null, tint = G.textDim, modifier = Modifier.size(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.main_exit_ip),
                color = G.textDim,
                style = TextStyle(fontSize = 13.sp),
            )
            Text(
                text = stringResource(R.string.main_exit_ip_masked),
                color = G.text,
                style = app.goloom.client.design.MonoBodyMedium,
            )
        }
        val dot = when (state) {
            is ConnectionState.On -> G.ok
            is ConnectionState.Connecting -> G.warn
            else -> G.textMute
        }
        GDot(color = dot)
    }
}

// ─────────────────────────────────────────────────────────────
// Profile switcher — bottom sheet
// ─────────────────────────────────────────────────────────────

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ProfileSwitcherSheet(
    profiles: List<Profile>,
    activeId: String?,
    onSelect: (String) -> Unit,
    onAddNew: () -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = G.bgElev1,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(G.borderStrong),
            )
        },
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.profiles_title),
                color = G.text,
                style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(bottom = 14.dp),
            )
            profiles.forEach { p ->
                ProfileRow(
                    profile = p,
                    isActive = p.id == activeId,
                    onClick = { onSelect(p.id) },
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            GButton(
                text = stringResource(R.string.profile_import_button),
                onClick = onAddNew,
                variant = GButtonVariant.Ghost,
                fullWidth = true,
                leading = { Icon(GIcons.Plus, null, modifier = Modifier.size(16.dp)) },
            )
            Spacer(modifier = Modifier.height(8.dp))
            GButton(
                // На сheet'е разводим импорт и переход к управлению профилями
                // отдельной кнопкой, чтобы юзер не путал.
                text = stringResource(R.string.profiles_manage_button),
                onClick = onManage,
                variant = GButtonVariant.Subtle,
                fullWidth = true,
                leading = { Icon(GIcons.Sliders, null, modifier = Modifier.size(16.dp)) },
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ProfileRow(
    profile: Profile,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isActive) G.bgElev2 else G.bgElev1)
            .border(
                1.dp,
                if (isActive) G.borderStrong else G.border,
                RoundedCornerShape(14.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (isActive) G.text else G.bgElev3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isActive) GIcons.Check else GIcons.Server,
                contentDescription = null,
                tint = if (isActive) G.bg else G.textDim,
                modifier = Modifier.size(17.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profile.name,
                color = G.text,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = profile.serverDisplay,
                color = G.textDim,
                style = app.goloom.client.design.MonoBodySmall,
            )
        }
        Icon(GIcons.ChevronRight, null, tint = G.textMute, modifier = Modifier.size(14.dp))
    }
}
