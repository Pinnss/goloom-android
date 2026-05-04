package app.goloom.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import app.goloom.client.R
import app.goloom.client.data.AppListEntry
import app.goloom.client.data.AppListStore
import app.goloom.client.data.SettingsManager
import app.goloom.client.design.G
import app.goloom.client.design.GCard
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GSectionLabel
import app.goloom.client.design.GSwitch
import app.goloom.client.design.GTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun AppRoutingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.get(context) }
    val store = remember { AppListStore.get(context) }

    val mode by settings.routingMode.collectAsState()
    val selected by store.selected.collectAsState()

    var apps by remember { mutableStateOf<List<AppListEntry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) { store.loadInstalledApps() }
    }

    val filtered by remember(apps, query, showSystem) {
        derivedStateOf {
            apps
                .filter { showSystem || !it.isSystem }
                .filter { it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true) }
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
                    text = stringResource(R.string.approuting_title),
                    color = G.text,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            },
        )

        // Mode picker
        Column(modifier = Modifier.padding(horizontal = 18.dp)) {
            GSectionLabel(text = stringResource(R.string.settings_network))
            GCard(padding = PaddingValues(0.dp)) {
                Column {
                    ModeRow(
                        text = stringResource(R.string.approuting_mode_off),
                        selected = mode == SettingsManager.RoutingMode.All,
                        onClick = { settings.setRoutingMode(SettingsManager.RoutingMode.All) },
                    )
                    ModeRow(
                        text = stringResource(R.string.approuting_mode_whitelist),
                        selected = mode == SettingsManager.RoutingMode.Whitelist,
                        onClick = { settings.setRoutingMode(SettingsManager.RoutingMode.Whitelist) },
                    )
                    ModeRow(
                        text = stringResource(R.string.approuting_mode_blacklist),
                        selected = mode == SettingsManager.RoutingMode.Blacklist,
                        onClick = { settings.setRoutingMode(SettingsManager.RoutingMode.Blacklist) },
                        last = true,
                    )
                }
            }
        }

        // Search
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            placeholder = { Text(stringResource(R.string.approuting_search), color = G.textMute) },
            singleLine = true,
            colors = goloomTextFieldColors(),
        )

        // Show system toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.approuting_show_system),
                color = G.textDim,
                style = TextStyle(fontSize = 13.sp),
                modifier = Modifier.weight(1f),
            )
            GSwitch(on = showSystem, onToggle = { showSystem = it })
        }

        Spacer(modifier = Modifier.height(8.dp))

        // App list
        val listEnabled = mode != SettingsManager.RoutingMode.All
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        ) {
            items(filtered, key = { it.packageName }) { entry ->
                AppRow(
                    entry = entry,
                    selected = entry.packageName in selected,
                    enabled = listEnabled,
                    onToggle = { store.toggle(entry.packageName) },
                )
            }
        }
    }
}

@Composable
private fun ModeRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, color = G.text, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(GIcons.Check, null, tint = G.text, modifier = Modifier.size(18.dp))
        }
    }
    if (!last) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(G.border),
        )
    }
}

@Composable
private fun AppRow(
    entry: AppListEntry,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(G.bgElev1)
            .border(1.dp, G.border, RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val icon = entry.icon
        if (icon != null) {
            val bmp = remember(entry.packageName) {
                runCatching { icon.toBitmap(width = 64, height = 64) }.getOrNull()
            }
            if (bmp != null) {
                Box(modifier = Modifier.size(36.dp)) {
                    androidx.compose.foundation.Image(
                        painter = BitmapPainter(bmp.asImageBitmap()),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                    )
                }
            } else {
                Box(modifier = Modifier.size(36.dp).background(G.bgElev3))
            }
        } else {
            Box(modifier = Modifier.size(36.dp).background(G.bgElev3))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                color = if (enabled) G.text else G.textMute,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = entry.packageName,
                color = G.textMute,
                style = TextStyle(fontSize = 11.sp),
            )
        }
        GSwitch(on = selected, onToggle = if (enabled) { _ -> onToggle() } else null)
    }
}
