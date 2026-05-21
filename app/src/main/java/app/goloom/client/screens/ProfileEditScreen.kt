package app.goloom.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goloom.bridge.mobile.Mobile
import app.goloom.client.R
import app.goloom.client.data.ProfileStore
import app.goloom.client.design.G
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GSectionLabel
import app.goloom.client.design.GTopBar
import org.json.JSONObject

/**
 * Profile editor with a structured form per transport — operator can
 * tweak the meeting URL / peer address / num_connections / WG keys /
 * etc. without touching the base64+JSON encoding of the link.
 *
 * Implementation goes round-trip through the Go bridge codecs:
 *
 *   1. On mount: Mobile.newClient().decodeProfileLink(profile.connStr)
 *      returns a flat JSON of every field the link carries plus a
 *      transport discriminator.
 *   2. UI shows fields scoped to the transport.
 *   3. On screen exit: Mobile.newClient().encodeProfileLink(JSON) →
 *      fresh link string, written back to ProfileStore.
 *
 * If the decode fails (corrupt link or unknown scheme) the editor
 * falls back to the legacy raw-text mode so the operator can still
 * salvage the profile by hand.
 */
@Composable
fun ProfileEditScreen(
    profileId: String,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProfileStore.get(context) }
    val profile = remember(profileId) { store.byId(profileId) }

    if (profile == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val client = remember { Mobile.newClient() }

    // Decode once on mount; keep the parsed JSON object as the
    // editor's source of truth.
    val initial = remember(profileId) {
        runCatching {
            JSONObject(client.decodeProfileLink(profile.connStr))
        }.getOrNull()
    }

    var name by remember { mutableStateOf(profile.name) }

    // Fall back to raw editor if decode failed (corrupt link /
    // unknown scheme) so operator can still fix the connStr.
    if (initial == null) {
        RawConnStrEditor(profile = profile, store = store, onBack = onBack)
        return
    }

    // Per-field state seeded from the decoded JSON. We let the user
    // edit any field regardless of transport — the encoder will
    // only serialize the ones relevant to the chosen transport.
    val transport = initial.optString("transport").ifBlank { "telemost" }
    var meeting by remember { mutableStateOf(initial.optString("meeting")) }
    var displayName by remember { mutableStateOf(initial.optString("display_name")) }
    var vkCodec by remember { mutableStateOf(initial.optString("vk_codec")) }
    var vkTurnPeerAddress by remember { mutableStateOf(initial.optString("vk_turn_peer_address")) }
    var vkTurnNumConnections by remember {
        mutableStateOf(initial.optInt("vk_turn_num_connections", 10).toString())
    }
    var vkTurnMTU by remember {
        mutableStateOf(initial.optInt("vk_turn_mtu", 0).let { if (it == 0) "" else it.toString() })
    }
    // Read-only WG identity surface — the operator typically gets
    // these from the admin panel and shouldn't be regenerating them
    // by hand. Shown for visibility but disabled to prevent typos.
    val wgClientAddr = initial.optString("wg_client_addr")
    val wgServerPublic = initial.optString("wg_server_public")

    val currentName = rememberUpdatedState(name)
    val currentMeeting = rememberUpdatedState(meeting)
    val currentDisplayName = rememberUpdatedState(displayName)
    val currentVkCodec = rememberUpdatedState(vkCodec)
    val currentVkTurnPeerAddress = rememberUpdatedState(vkTurnPeerAddress)
    val currentVkTurnNumConnections = rememberUpdatedState(vkTurnNumConnections)
    val currentVkTurnMTU = rememberUpdatedState(vkTurnMTU)

    DisposableEffect(profileId) {
        onDispose {
            val freshName = currentName.value.trim().ifEmpty { profile.name }

            // Rebuild JSON with edited values, send through encoder.
            // Unrelated transports leave their fields untouched (the
            // Go-side encoder ignores fields that don't apply).
            val mutated = JSONObject(initial.toString())
            mutated.put("meeting", currentMeeting.value.trim())
            mutated.put("display_name", currentDisplayName.value.trim())
            mutated.put("vk_codec", currentVkCodec.value.trim())
            mutated.put("vk_turn_peer_address", currentVkTurnPeerAddress.value.trim())
            mutated.put(
                "vk_turn_num_connections",
                currentVkTurnNumConnections.value.toIntOrNull() ?: 0,
            )
            mutated.put(
                "vk_turn_mtu",
                currentVkTurnMTU.value.toIntOrNull() ?: 0,
            )

            val newLink = runCatching { client.encodeProfileLink(mutated.toString()) }.getOrNull()
            if (newLink != null && (freshName != profile.name || newLink != profile.connStr)) {
                store.update(profile.copy(name = freshName, connStr = newLink))
            } else if (freshName != profile.name) {
                store.update(profile.copy(name = freshName))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(G.bg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        GTopBar(
            left = {
                GIconBtn(onClick = onBack, icon = { Icon(GIcons.Back, null, tint = G.text) })
                Text(
                    text = stringResource(R.string.profile_edit_title),
                    color = G.text,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            },
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // ── Profile name (universal) ──────────────────────────
            GSectionLabel(text = stringResource(R.string.profile_edit_section_profile))
            field(
                value = name,
                onChange = { name = it },
                label = stringResource(R.string.profile_edit_name),
                singleLine = true,
                capitalize = true,
            )

            // ── Transport indicator (read-only) ───────────────────
            Spacer(modifier = Modifier.height(8.dp))
            GSectionLabel(text = "Транспорт")
            Text(
                text = transportDisplayName(transport),
                color = G.text,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                modifier = Modifier.padding(start = 4.dp),
            )
            Text(
                text = "Чтобы поменять транспорт — удали профиль и импортируй ссылку заново.",
                color = G.textMute,
                style = TextStyle(fontSize = 11.sp),
                modifier = Modifier.padding(start = 4.dp),
            )

            // ── Transport-specific editable fields ────────────────
            Spacer(modifier = Modifier.height(8.dp))
            when (transport) {
                "telemost", "" -> {
                    GSectionLabel(text = "Telemost")
                    field(
                        value = meeting,
                        onChange = { meeting = it },
                        label = "Meeting URL",
                        placeholder = "https://telemost.yandex.ru/j/...",
                    )
                    field(
                        value = displayName,
                        onChange = { displayName = it },
                        label = "Display name (опц.)",
                        singleLine = true,
                    )
                }
                "vk-calls" -> {
                    GSectionLabel(text = "VK Calls")
                    field(
                        value = meeting,
                        onChange = { meeting = it },
                        label = "VK Call link",
                        placeholder = "https://vk.com/call/join/...",
                    )
                    field(
                        value = vkCodec,
                        onChange = { vkCodec = it },
                        label = "Codec (h264 / vp8 / пусто = h264)",
                        singleLine = true,
                    )
                    field(
                        value = displayName,
                        onChange = { displayName = it },
                        label = "Display name (опц.)",
                        singleLine = true,
                    )
                }
                "livekit-wb-stream" -> {
                    GSectionLabel(text = "WB Stream")
                    field(
                        value = meeting,
                        onChange = { meeting = it },
                        label = "Room URL",
                        placeholder = "https://stream.wb.ru/r/...",
                    )
                    Text(
                        text = "Access token и cookies через ссылку — редактируй raw connstr если нужно поменять.",
                        color = G.textMute,
                        style = TextStyle(fontSize = 11.sp),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                "vk-turn", "vk-turn-srtp" -> {
                    GSectionLabel(text = if (transport == "vk-turn-srtp") "VK TURN SRTP" else "VK TURN (legacy)")
                    field(
                        value = vkTurnPeerAddress,
                        onChange = { vkTurnPeerAddress = it },
                        label = "Адрес сервера (host:port)",
                        placeholder = "vps.example.com:56001",
                    )
                    field(
                        value = meeting,
                        onChange = { meeting = it },
                        label = "VK Call link (для TURN-credentials)",
                        placeholder = "https://vk.com/call/join/...",
                    )
                    field(
                        value = vkTurnNumConnections,
                        onChange = { vkTurnNumConnections = it.filter { ch -> ch.isDigit() } },
                        label = "Параллельных TURN-аллокаций (default 10)",
                        keyboardType = KeyboardType.Number,
                        singleLine = true,
                    )
                    Text(
                        text = "VK шейпит per-allocation; больше = выше скорость, ~30+ может ловить rate-limit от VK.",
                        color = G.textMute,
                        style = TextStyle(fontSize = 11.sp),
                        modifier = Modifier.padding(start = 4.dp),
                    )
                    field(
                        value = vkTurnMTU,
                        onChange = { vkTurnMTU = it.filter { ch -> ch.isDigit() } },
                        label = "MTU (пусто = 1280)",
                        keyboardType = KeyboardType.Number,
                        singleLine = true,
                    )
                }
            }

            // ── WG identity (read-only summary) ───────────────────
            if (wgClientAddr.isNotBlank() || wgServerPublic.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                GSectionLabel(text = "WG идентичность (только просмотр)")
                if (wgClientAddr.isNotBlank()) {
                    fieldReadOnly("Tunnel address", wgClientAddr)
                }
                if (wgServerPublic.isNotBlank()) {
                    fieldReadOnly("Server public key", wgServerPublic)
                }
                Text(
                    text = "Ключи берутся из admin-панели сервера. Поменять руками = пересоздать инбаунд и переимпортировать ссылку.",
                    color = G.textMute,
                    style = TextStyle(fontSize = 11.sp),
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

/**
 * RawConnStrEditor — legacy single-textarea editor. Surfaced only
 * when decodeProfileLink fails (corrupt link / unknown scheme), as
 * a fallback so a bad import doesn't lock the operator out of
 * editing the profile.
 */
@Composable
private fun RawConnStrEditor(
    profile: app.goloom.client.data.Profile,
    store: ProfileStore,
    onBack: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var connStr by remember { mutableStateOf(profile.connStr) }
    val currentName = rememberUpdatedState(name)
    val currentConnStr = rememberUpdatedState(connStr)

    DisposableEffect(profile.id) {
        onDispose {
            val freshName = currentName.value.trim().ifEmpty { profile.name }
            val freshStr = currentConnStr.value.trim()
            if (freshName != profile.name || freshStr != profile.connStr) {
                store.update(
                    profile.copy(
                        name = freshName,
                        connStr = freshStr.ifEmpty { profile.connStr },
                    ),
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(G.bg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        GTopBar(
            left = {
                GIconBtn(onClick = onBack, icon = { Icon(GIcons.Back, null, tint = G.text) })
                Text(
                    text = stringResource(R.string.profile_edit_title),
                    color = G.text,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            GSectionLabel(text = stringResource(R.string.profile_edit_section_profile))
            field(
                value = name,
                onChange = { name = it },
                label = stringResource(R.string.profile_edit_name),
                singleLine = true,
                capitalize = true,
            )
            Spacer(modifier = Modifier.height(8.dp))
            GSectionLabel(text = stringResource(R.string.profile_edit_section_connstr))
            Text(
                text = "Не удалось распарсить ссылку — открываю raw-режим для ручного исправления.",
                color = G.warn,
                style = TextStyle(fontSize = 12.sp),
                modifier = Modifier.padding(start = 4.dp),
            )
            OutlinedTextField(
                value = connStr,
                onValueChange = { connStr = it },
                placeholder = { Text(stringResource(R.string.profile_edit_connstr_placeholder), color = G.textMute) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 160.dp),
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                colors = goloomTextFieldColors(),
            )
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String? = null,
    singleLine: Boolean = false,
    capitalize: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = G.textMute) } },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            keyboardType = keyboardType,
        ),
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontSize = 14.sp),
        colors = goloomTextFieldColors(),
    )
}

@Composable
private fun fieldReadOnly(label: String, value: String) {
    OutlinedTextField(
        value = value,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
        colors = goloomTextFieldColors(),
    )
}

private fun transportDisplayName(transport: String): String = when (transport) {
    "telemost", "" -> "Telemost"
    "vk-calls" -> "VK Calls"
    "livekit-wb-stream" -> "WB Stream (LiveKit)"
    "vk-turn" -> "VK TURN (legacy DTLS)"
    "vk-turn-srtp" -> "VK TURN SRTP"
    else -> transport
}

@Composable
internal fun goloomTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = G.ring,
    unfocusedBorderColor = G.border,
    focusedTextColor = G.text,
    unfocusedTextColor = G.text,
    focusedContainerColor = G.bgElev1,
    unfocusedContainerColor = G.bgElev1,
    focusedLabelColor = G.textDim,
    unfocusedLabelColor = G.textMute,
    cursorColor = G.ring,
)
