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
import app.goloom.client.R
import app.goloom.client.data.ProfileStore
import app.goloom.client.design.G
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GSectionLabel
import app.goloom.client.design.GTopBar

/**
 * Простой редактор профиля. Только Name + connStr (на случай ручной коррекции).
 * Сохранение — onDispose: при уходе с экрана пишем в store, без подтверждения.
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

    var name by remember { mutableStateOf(profile.name) }
    var connStr by remember { mutableStateOf(profile.connStr) }
    var vkTargetMeeting by remember { mutableStateOf(profile.vkTargetMeeting.orEmpty()) }

    val currentName = rememberUpdatedState(name)
    val currentConnStr = rememberUpdatedState(connStr)
    val currentVKMeeting = rememberUpdatedState(vkTargetMeeting)

    DisposableEffect(profileId) {
        onDispose {
            val freshName = currentName.value.trim().ifEmpty { profile.name }
            val freshStr = currentConnStr.value.trim()
            val freshVKMeeting = currentVKMeeting.value.trim().ifEmpty { null }
            if (freshName != profile.name ||
                freshStr != profile.connStr ||
                freshVKMeeting != profile.vkTargetMeeting
            ) {
                store.update(
                    profile.copy(
                        name = freshName,
                        connStr = freshStr.ifEmpty { profile.connStr },
                        vkTargetMeeting = freshVKMeeting,
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
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.profile_edit_name)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    keyboardType = KeyboardType.Text,
                ),
                modifier = Modifier.fillMaxWidth(),
                colors = goloomTextFieldColors(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            GSectionLabel(text = stringResource(R.string.profile_edit_section_connstr))
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

            // VK Calls in-band lobby (S2/S3): meeting URL не зашит в
            // connstr (lobby_meeting_url только под server'а), а
            // вводится пользователем здесь. Поле появляется только
            // когда профиль действительно lobby-режима.
            val parsedSafe = runCatching { profile.parsed }.getOrNull()
            if (parsedSafe?.hasLobby == true) {
                Spacer(modifier = Modifier.height(8.dp))
                GSectionLabel(text = "VK Call link")
                OutlinedTextField(
                    value = vkTargetMeeting,
                    onValueChange = { vkTargetMeeting = it },
                    placeholder = {
                        Text(
                            "https://vk.com/call/join/...",
                            color = G.textMute,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                    colors = goloomTextFieldColors(),
                )
                Text(
                    text = "Сервер ленится — peer-join делает только когда " +
                        "ты введёшь свой VK call link и нажмёшь Connect.",
                    color = G.textMute,
                    style = TextStyle(fontSize = 12.sp),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
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
