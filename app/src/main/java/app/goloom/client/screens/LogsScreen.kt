package app.goloom.client.screens

import android.widget.Toast
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
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goloom.client.R
import app.goloom.client.data.LogEntry
import app.goloom.client.data.LogLevel
import app.goloom.client.data.LogStore
import app.goloom.client.design.G
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GTopBar
import app.goloom.client.design.MonoLogLine
import app.goloom.client.util.Clipboard
import app.goloom.client.util.Sharing

@Composable
fun LogsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { LogStore.get(context) }
    val all by store.entries.collectAsState()

    var filter by remember { mutableStateOf<LogLevel?>(null) }
    val filtered by remember(all, filter) {
        derivedStateOf {
            if (filter == null) all else all.filter { it.level == filter }
        }
    }

    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Auto-scroll к последней при новых entries
    LaunchedEffect(filtered.size) {
        if (filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.size - 1)
        }
    }

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
                    text = stringResource(R.string.logs_count, filtered.size, all.size),
                    color = G.text,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            },
            right = {
                GIconBtn(
                    onClick = {
                        Clipboard.write(context, store.snapshotFormatted())
                        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                    },
                    icon = { Icon(GIcons.Copy, null, tint = G.text) },
                )
                GIconBtn(
                    onClick = {
                        store.fileForShare()?.let {
                            Sharing.shareFile(context, it)
                        } ?: Sharing.shareText(context, store.snapshotFormatted())
                    },
                    icon = { Icon(GIcons.Share, null, tint = G.text) },
                )
                GIconBtn(
                    onClick = { showClearDialog = true },
                    icon = { Icon(GIcons.Trash, null, tint = G.err) },
                )
            },
        )

        // Фильтр-чипы
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(stringResource(R.string.logs_filter_all), selected = filter == null) { filter = null }
            FilterChip(stringResource(R.string.logs_filter_info), selected = filter == LogLevel.INFO) { filter = LogLevel.INFO }
            FilterChip(stringResource(R.string.logs_filter_warn), selected = filter == LogLevel.WARN) { filter = LogLevel.WARN }
            FilterChip(stringResource(R.string.logs_filter_debug), selected = filter == LogLevel.DEBUG) { filter = LogLevel.DEBUG }
            FilterChip(stringResource(R.string.logs_filter_error), selected = filter == LogLevel.ERROR) { filter = LogLevel.ERROR }
        }

        // Стрим логов
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(G.bgElev1)
                .border(1.dp, G.border, RoundedCornerShape(14.dp)),
        ) {
            if (filtered.isEmpty()) {
                Text(
                    text = if (all.isEmpty()) stringResource(R.string.logs_empty)
                           else stringResource(R.string.logs_empty_filtered),
                    color = G.textDim,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                ) {
                    itemsIndexed(filtered, key = { i, _ -> i }) { _, entry ->
                        LogRow(entry)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    store.clear()
                    showClearDialog = false
                }) { Text(stringResource(R.string.logs_clear), color = G.err) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = G.textDim)
                }
            },
            containerColor = G.bgElev1,
            title = { Text(stringResource(R.string.logs_clear_confirm), color = G.text) },
        )
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(if (selected) G.text else G.bgElev1)
            .border(
                1.dp,
                if (selected) G.text else G.border,
                RoundedCornerShape(100.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            color = if (selected) G.bg else G.textDim,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val levelColor = when (entry.level) {
        LogLevel.INFO -> G.textDim
        LogLevel.WARN -> G.warn
        LogLevel.DEBUG -> G.textMute
        LogLevel.ERROR -> G.err
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = entry.formatTime(),
            color = G.textMute,
            style = MonoLogLine,
        )
        Text(
            text = entry.level.short,
            color = levelColor,
            style = MonoLogLine.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.size(width = 28.dp, height = 17.dp),
        )
        Text(
            text = entry.source.short,
            color = G.textMute,
            style = MonoLogLine,
            modifier = Modifier.size(width = 22.dp, height = 17.dp),
        )
        Text(
            text = entry.message,
            color = G.text,
            style = MonoLogLine,
            modifier = Modifier.weight(1f),
        )
    }
}
