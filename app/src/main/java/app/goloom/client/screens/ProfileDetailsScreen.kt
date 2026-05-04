package app.goloom.client.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goloom.client.R
import app.goloom.client.data.ProfileStore
import app.goloom.client.design.G
import app.goloom.client.design.GButton
import app.goloom.client.design.GButtonVariant
import app.goloom.client.design.GCard
import app.goloom.client.design.GDot
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GRow
import app.goloom.client.design.GSectionLabel
import app.goloom.client.design.GTopBar
import app.goloom.client.design.MonoBodySmall
import app.goloom.client.util.Clipboard
import app.goloom.client.util.QrUtils
import app.goloom.client.util.Sharing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ProfileDetailsScreen(
    profileId: String,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onDeleted: () -> Unit,
    onShareSnack: (String) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProfileStore.get(context) }
    val profile = remember(profileId) { store.byId(profileId) }

    if (profile == null) {
        // Профиль удалили — обратно в список.
        LaunchedEffect(Unit) { onBack() }
        return
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var qrBitmap by remember(profileId) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var showQr by remember { mutableStateOf(false) }

    LaunchedEffect(profileId) {
        qrBitmap = withContext(Dispatchers.IO) {
            QrUtils.encode(profile.connStr, size = 768)
        }
    }

    val parsed = profile.parsed

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
                    text = stringResource(R.string.profile_title),
                    color = G.text,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            },
            right = {
                GIconBtn(
                    onClick = { onEdit(profileId) },
                    icon = { Icon(GIcons.Edit, null, tint = G.text) },
                )
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
        ) {
            // Hero
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(G.bgElev1)
                    .border(1.dp, G.border, RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(G.bgElev3),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(GIcons.Server, null, tint = G.text, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        color = G.text,
                        style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                    )
                    Text(
                        text = profile.serverDisplay,
                        color = G.textDim,
                        style = MonoBodySmall,
                    )
                }
                GDot(color = G.ok)
            }

            GSectionLabel(text = stringResource(R.string.profile_section_config), modifier = Modifier.padding(top = 22.dp))
            GCard(padding = PaddingValues(0.dp)) {
                Column {
                    GRow(
                        title = stringResource(R.string.profile_endpoint),
                        detail = parsed.wgEndpoint ?: parsed.meeting,
                        icon = { Icon(GIcons.Globe, null, tint = G.textDim, modifier = Modifier.size(15.dp)) },
                        trailing = {
                            Icon(
                                GIcons.Copy, null, tint = G.textMute,
                                modifier = Modifier
                                    .size(15.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                            )
                        },
                        onClick = {
                            Clipboard.write(context, parsed.wgEndpoint ?: parsed.meeting)
                            Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                        },
                    )
                    GRow(
                        title = stringResource(R.string.profile_protocol),
                        detail = "Goloom v2",
                        icon = { Icon(GIcons.Lock, null, tint = G.textDim, modifier = Modifier.size(15.dp)) },
                    )
                    GRow(
                        title = stringResource(R.string.profile_mtu),
                        detail = "1380",
                        icon = { Icon(GIcons.Sliders, null, tint = G.textDim, modifier = Modifier.size(15.dp)) },
                    )
                    GRow(
                        title = stringResource(R.string.profile_allowed_ips),
                        detail = "0.0.0.0/1, 128.0.0.0/1",
                        icon = { Icon(GIcons.Server, null, tint = G.textDim, modifier = Modifier.size(15.dp)) },
                        last = true,
                    )
                }
            }

            GSectionLabel(text = stringResource(R.string.profile_section_export), modifier = Modifier.padding(top = 22.dp))
            GCard(padding = PaddingValues(12.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GButton(
                            text = stringResource(R.string.profile_export_qr),
                            onClick = { showQr = true },
                            variant = GButtonVariant.Ghost,
                            modifier = Modifier.weight(1f),
                            fullWidth = true,
                            leading = { Icon(GIcons.QR, null, modifier = Modifier.size(16.dp)) },
                        )
                        GButton(
                            text = stringResource(R.string.profile_export_copy),
                            onClick = {
                                Clipboard.write(context, profile.connStr)
                                onShareSnack("Copied")
                            },
                            variant = GButtonVariant.Ghost,
                            modifier = Modifier.weight(1f),
                            fullWidth = true,
                            leading = { Icon(GIcons.Copy, null, modifier = Modifier.size(16.dp)) },
                        )
                    }
                    GButton(
                        text = stringResource(R.string.profile_export_share),
                        onClick = {
                            val wg = parsed.toWireGuardConfig()
                            if (wg != null) {
                                val safeName = profile.name.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                                val file = Sharing.writeExport(context, "$safeName.conf", wg)
                                Sharing.shareFile(context, file, "application/x-wireguard-profile")
                            } else {
                                onShareSnack("No WG config in this profile")
                            }
                        },
                        variant = GButtonVariant.Subtle,
                        fullWidth = true,
                        leading = { Icon(GIcons.Share, null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            GButton(
                text = stringResource(R.string.profile_delete),
                onClick = { showDeleteDialog = true },
                variant = GButtonVariant.Ghost,
                danger = true,
                fullWidth = true,
                leading = { Icon(GIcons.Trash, null, tint = G.err, modifier = Modifier.size(15.dp)) },
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showQr) {
        AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) {
                    Text(stringResource(R.string.action_done), color = G.text)
                }
            },
            containerColor = G.bgElev1,
            title = { Text(profile.name, color = G.text) },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(G.bg),
                    contentAlignment = Alignment.Center,
                ) {
                    qrBitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(8.dp),
                        )
                    }
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(profileId)
                    showDeleteDialog = false
                    onDeleted()
                }) {
                    Text(stringResource(R.string.action_delete), color = G.err)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel), color = G.textDim)
                }
            },
            containerColor = G.bgElev1,
            title = { Text(stringResource(R.string.profile_delete), color = G.text) },
            text = {
                Text(
                    text = stringResource(R.string.profile_delete_confirm, profile.name),
                    color = G.textDim,
                )
            },
        )
    }
}
