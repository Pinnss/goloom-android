package app.goloom.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goloom.client.BuildConfig
import app.goloom.client.R
import app.goloom.client.design.G
import app.goloom.client.design.GButton
import app.goloom.client.design.GButtonVariant
import app.goloom.client.design.GCard
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GSectionLabel
import app.goloom.client.design.GTopBar
import app.goloom.client.util.Downloader
import app.goloom.client.util.Installer
import app.goloom.client.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UpdatesScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var result by remember { mutableStateOf<UpdateChecker.Result?>(null) }
    var dlState by remember { mutableStateOf<Downloader.State>(Downloader.State.Idle) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        result = withContext(Dispatchers.IO) { UpdateChecker.check(context) }
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
                    text = stringResource(R.string.updates_title),
                    color = G.text,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp),
        ) {
            when (val r = result) {
                null -> CenterStatus(text = stringResource(R.string.updates_checking), withSpinner = true)
                UpdateChecker.Result.UpToDate -> CenterStatus(text = stringResource(R.string.updates_up_to_date))
                is UpdateChecker.Result.Failed -> CenterStatus(
                    text = "${stringResource(R.string.updates_check_failed)} — ${r.message}",
                )
                is UpdateChecker.Result.Available -> AvailableContent(
                    available = r,
                    dlState = dlState,
                    onDownload = {
                        if (r.downloadUrl.isBlank()) {
                            dlState = Downloader.State.Failed(
                                context.getString(R.string.updates_no_download_url),
                            )
                            return@AvailableContent
                        }
                        scope.launch {
                            dlState = Downloader.State.Downloading(0, 0, 0)
                            try {
                                val file = Downloader.downloadApk(
                                    context, r.downloadUrl,
                                ) { pct, downloaded, total ->
                                    dlState = Downloader.State.Downloading(pct, downloaded, total)
                                }
                                dlState = Downloader.State.Ready(file)
                                // Сразу открываем installer, юзеру не нужно
                                // лишнее нажатие.
                                Installer.installApk(context, file)
                            } catch (t: Throwable) {
                                dlState = Downloader.State.Failed(
                                    t.message ?: t.javaClass.simpleName,
                                )
                            }
                        }
                    },
                    onOpenInstaller = {
                        val s = dlState
                        if (s is Downloader.State.Ready) {
                            Installer.installApk(context, s.file)
                        }
                    },
                    onLater = onBack,
                )
            }
        }
    }
}

@Composable
private fun CenterStatus(text: String, withSpinner: Boolean = false) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (withSpinner) {
                CircularProgressIndicator(color = G.text)
                Spacer(modifier = Modifier.height(12.dp))
            }
            Text(text = text, color = G.textDim)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.AvailableContent(
    available: UpdateChecker.Result.Available,
    dlState: Downloader.State,
    onDownload: () -> Unit,
    onOpenInstaller: () -> Unit,
    onLater: () -> Unit,
) {
    Spacer(modifier = Modifier.height(4.dp))
    // Version chunk
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(G.bgElev1)
            .border(1.dp, G.border, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(G.warnTint)
                    .border(1.dp, G.warnBorder, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GIcons.Sparkle, null, tint = G.warn, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.updates_new_version).uppercase(),
                    color = G.textMute,
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.4.sp),
                )
                Text(
                    text = available.version,
                    color = G.text,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            if (available.sizeBytes > 0) {
                Text(
                    text = humanSize(available.sizeBytes),
                    color = G.textDim,
                    style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.updates_current), color = G.textDim, style = TextStyle(fontSize = 12.sp))
            Text(BuildConfig.VERSION_NAME, color = G.text, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
            Icon(GIcons.Arrow, null, tint = G.textMute, modifier = Modifier.size(12.dp))
            Text(available.version, color = G.text, style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp))
        }
    }

    GSectionLabel(text = stringResource(R.string.updates_whats_new), modifier = Modifier.padding(top = 22.dp))
    GCard(padding = PaddingValues(16.dp)) {
        Text(
            text = available.notes.ifBlank { "—" },
            color = G.text,
            style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
        )
    }

    Spacer(modifier = Modifier.weight(1f))

    // Action area: меняется по dlState — Idle → Install button,
    // Downloading → progress bar, Ready → Open installer button,
    // Failed → red label + retry.
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (dlState) {
            Downloader.State.Idle -> {
                GButton(
                    text = stringResource(R.string.updates_install),
                    onClick = onDownload,
                    variant = GButtonVariant.Primary,
                    fullWidth = true,
                )
                GButton(
                    text = stringResource(R.string.updates_remind_later),
                    onClick = onLater,
                    variant = GButtonVariant.Ghost,
                    fullWidth = true,
                )
            }
            is Downloader.State.Downloading -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(R.string.updates_downloading, dlState.percent),
                            color = G.text,
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                        )
                        if (dlState.totalBytes > 0) {
                            Text(
                                text = "${humanSize(dlState.downloadedBytes)} / ${humanSize(dlState.totalBytes)}",
                                color = G.textDim,
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            )
                        }
                    }
                    LinearProgressIndicator(
                        progress = { dlState.percent / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = G.text,
                        trackColor = G.bgElev3,
                    )
                }
            }
            is Downloader.State.Ready -> {
                GButton(
                    text = stringResource(R.string.updates_open_installer),
                    onClick = onOpenInstaller,
                    variant = GButtonVariant.Primary,
                    fullWidth = true,
                )
            }
            is Downloader.State.Failed -> {
                Text(
                    text = stringResource(R.string.updates_download_failed, dlState.message),
                    color = G.err,
                    style = TextStyle(fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                GButton(
                    text = stringResource(R.string.action_retry),
                    onClick = onDownload,
                    variant = GButtonVariant.Primary,
                    fullWidth = true,
                )
                GButton(
                    text = stringResource(R.string.updates_remind_later),
                    onClick = onLater,
                    variant = GButtonVariant.Ghost,
                    fullWidth = true,
                )
            }
        }
    }
}

private fun humanSize(bytes: Long): String {
    if (bytes < 1024) return "${bytes} B"
    if (bytes < 1024 * 1024) return "%.1f KB".format(bytes / 1024.0)
    return "%.1f MB".format(bytes / (1024.0 * 1024))
}
