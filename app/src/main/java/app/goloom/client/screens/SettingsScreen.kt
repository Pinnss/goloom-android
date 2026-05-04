package app.goloom.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.appcompat.app.AppCompatDelegate
import app.goloom.client.BuildConfig
import app.goloom.client.R
import app.goloom.client.data.SettingsManager
import app.goloom.client.design.G
import app.goloom.client.design.GCard
import app.goloom.client.design.GChev
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GRow
import app.goloom.client.design.GSectionLabel
import app.goloom.client.design.GSwitch
import app.goloom.client.design.GTopBar

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLogs: () -> Unit,
    onOpenParams: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenUpdates: () -> Unit,
    onOpenAppRouting: () -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.get(context) }
    val connectOnLaunch by settings.connectOnLaunch.collectAsState()
    val autoReconnect by settings.autoReconnect.collectAsState()
    val blockIPv6 by settings.blockIPv6.collectAsState()
    val language by settings.language.collectAsState()

    var showLanguageDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(G.bg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        GTopBar(
            left = {
                GIconBtn(onClick = onBack, icon = { Icon(GIcons.Back, null, tint = G.text) })
                Text(
                    text = stringResource(R.string.settings_title),
                    color = G.text,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            GSectionLabel(text = stringResource(R.string.settings_section_general))
            GCard(padding = PaddingValues(0.dp)) {
                Column {
                    GRow(
                        title = stringResource(R.string.settings_parameters),
                        detail = stringResource(R.string.settings_parameters_detail),
                        icon = { Icon(GIcons.Sliders, null, tint = G.textDim, modifier = Modifier.size(15.dp)) },
                        trailing = { GChev() },
                        onClick = onOpenParams,
                    )
                    GRow(
                        title = stringResource(R.string.settings_logs),
                        detail = stringResource(R.string.settings_logs_detail),
                        icon = { Icon(GIcons.Logs, null, tint = G.textDim, modifier = Modifier.size(15.dp)) },
                        trailing = { GChev() },
                        onClick = onOpenLogs,
                    )
                    GRow(
                        title = stringResource(R.string.settings_network),
                        detail = stringResource(R.string.settings_network_detail),
                        icon = { Icon(GIcons.Globe, null, tint = G.textDim, modifier = Modifier.size(15.dp)) },
                        trailing = { GChev() },
                        onClick = onOpenAppRouting,
                        last = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            GSectionLabel(text = stringResource(R.string.settings_section_behavior))
            GCard(padding = PaddingValues(0.dp)) {
                Column {
                    GRow(
                        title = stringResource(R.string.settings_connect_on_launch),
                        trailing = { GSwitch(on = connectOnLaunch, onToggle = settings::setConnectOnLaunch) },
                    )
                    GRow(
                        title = stringResource(R.string.settings_reconnect),
                        trailing = { GSwitch(on = autoReconnect, onToggle = settings::setAutoReconnect) },
                    )
                    GRow(
                        title = stringResource(R.string.settings_block_ipv6),
                        trailing = { GSwitch(on = blockIPv6, onToggle = settings::setBlockIPv6) },
                    )
                    GRow(
                        title = stringResource(R.string.settings_language),
                        detail = languageLabel(language),
                        trailing = { GChev() },
                        onClick = { showLanguageDialog = true },
                        last = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            GSectionLabel(text = stringResource(R.string.settings_section_about))
            GCard(padding = PaddingValues(0.dp)) {
                Column {
                    GRow(
                        title = stringResource(R.string.settings_about_app),
                        icon = { Icon(GIcons.Info, null, tint = G.textDim, modifier = Modifier.size(15.dp)) },
                        trailing = { GChev() },
                        onClick = onOpenAbout,
                    )
                    GRow(
                        title = stringResource(R.string.settings_check_updates),
                        detail = stringResource(R.string.settings_version_current, BuildConfig.VERSION_NAME),
                        icon = { Icon(GIcons.Refresh, null, tint = G.textDim, modifier = Modifier.size(15.dp)) },
                        trailing = { GChev() },
                        onClick = onOpenUpdates,
                        last = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
            // Version footer — tap to check updates
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable(onClick = onOpenUpdates),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.settings_version_footer, BuildConfig.VERSION_NAME),
                    color = G.textMute,
                    style = TextStyle(fontSize = 12.sp),
                )
                Text(
                    text = "  ·  ",
                    color = G.textMute,
                    style = TextStyle(fontSize = 12.sp),
                )
                Text(
                    text = stringResource(R.string.settings_check_updates_inline),
                    color = G.textDim,
                    style = TextStyle(fontSize = 12.sp, textDecoration = TextDecoration.Underline),
                )
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (showLanguageDialog) {
        LanguageDialog(
            current = language,
            onSelect = { lang ->
                settings.setLanguage(lang)
                AppCompatDelegate.setApplicationLocales(
                    if (lang.tag.isBlank()) LocaleListCompat.getEmptyLocaleList()
                    else LocaleListCompat.forLanguageTags(lang.tag),
                )
                showLanguageDialog = false
            },
            onDismiss = { showLanguageDialog = false },
        )
    }
}

@Composable
private fun languageLabel(lang: SettingsManager.Language): String = when (lang) {
    SettingsManager.Language.System -> stringResource(R.string.settings_language_system)
    SettingsManager.Language.English -> stringResource(R.string.settings_language_en)
    SettingsManager.Language.Russian -> stringResource(R.string.settings_language_ru)
}

@Composable
private fun LanguageDialog(
    current: SettingsManager.Language,
    onSelect: (SettingsManager.Language) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_done), color = G.text)
            }
        },
        containerColor = G.bgElev1,
        title = { Text(stringResource(R.string.settings_language), color = G.text) },
        text = {
            Column {
                SettingsManager.Language.entries.forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(lang) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = lang == current,
                            onClick = { onSelect(lang) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = G.ring,
                                unselectedColor = G.textMute,
                            ),
                        )
                        Text(
                            text = languageLabel(lang),
                            color = G.text,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
    )
}
