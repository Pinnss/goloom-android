package app.goloom.client.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
fun ParametersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = remember { SettingsManager.get(context) }
    val mtu by settings.mtu.collectAsState()
    val keepalive by settings.persistentKeepalive.collectAsState()
    val dns by settings.dnsServers.collectAsState()

    var mtuDialog by remember { mutableStateOf(false) }
    var keepaliveDialog by remember { mutableStateOf(false) }
    var dnsDialog by remember { mutableStateOf(false) }

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
                    text = stringResource(R.string.params_title),
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
            GSectionLabel(text = stringResource(R.string.params_section_protocol))
            GCard(padding = PaddingValues(0.dp)) {
                GRow(
                    title = stringResource(R.string.params_protocol_v2),
                    detail = stringResource(R.string.params_protocol_v2_detail),
                    trailing = {
                        Icon(GIcons.Check, null, tint = G.text, modifier = Modifier.size(16.dp))
                    },
                    last = true,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            GSectionLabel(text = stringResource(R.string.params_section_tunnel))
            GCard(padding = PaddingValues(0.dp)) {
                Column {
                    GRow(
                        title = stringResource(R.string.params_mtu),
                        detail = "$mtu",
                        trailing = { GChev() },
                        onClick = { mtuDialog = true },
                    )
                    GRow(
                        title = stringResource(R.string.params_keepalive),
                        detail = "${keepalive}s",
                        trailing = { GChev() },
                        onClick = { keepaliveDialog = true },
                        last = true,
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))
            GSectionLabel(text = stringResource(R.string.params_section_dns))
            GCard(padding = PaddingValues(0.dp)) {
                GRow(
                    title = stringResource(R.string.params_dns_mode),
                    detail = dns,
                    trailing = { GChev() },
                    onClick = { dnsDialog = true },
                    last = true,
                )
            }

            Spacer(modifier = Modifier.height(22.dp))
            GSectionLabel(text = stringResource(R.string.params_section_advanced))
            GCard(padding = PaddingValues(0.dp)) {
                Column {
                    GRow(
                        title = stringResource(R.string.profile_allowed_ips),
                        detail = "0.0.0.0/1, 128.0.0.0/1",
                        last = true,
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }

    if (mtuDialog) {
        IntInputDialog(
            title = stringResource(R.string.params_mtu),
            initial = mtu,
            range = 576..9000,
            onApply = { settings.setMtu(it); mtuDialog = false },
            onCancel = { mtuDialog = false },
        )
    }
    if (keepaliveDialog) {
        IntInputDialog(
            title = stringResource(R.string.params_keepalive),
            initial = keepalive,
            range = 5..600,
            suffix = "s",
            onApply = { settings.setPersistentKeepalive(it); keepaliveDialog = false },
            onCancel = { keepaliveDialog = false },
        )
    }
    if (dnsDialog) {
        TextInputDialog(
            title = stringResource(R.string.params_dns_mode),
            initial = dns,
            placeholder = "1.1.1.1, 8.8.8.8",
            onApply = { settings.setDnsServers(it); dnsDialog = false },
            onCancel = { dnsDialog = false },
        )
    }
}

@Composable
private fun IntInputDialog(
    title: String,
    initial: Int,
    range: IntRange,
    suffix: String = "",
    onApply: (Int) -> Unit,
    onCancel: () -> Unit,
) {
    var input by remember { mutableStateOf(initial.toString()) }
    val parsed = input.toIntOrNull()
    val valid = parsed != null && parsed in range
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(enabled = valid, onClick = { parsed?.let(onApply) }) {
                Text(stringResource(R.string.action_save), color = if (valid) G.text else G.textMute)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = G.textDim) }
        },
        containerColor = G.bgElev1,
        title = { Text(title, color = G.text) },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(5) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = goloomTextFieldColors(),
                    suffix = if (suffix.isNotEmpty()) {
                        { Text(suffix, color = G.textDim) }
                    } else null,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Range: ${range.first}–${range.last}${if (suffix.isNotEmpty()) " $suffix" else ""}",
                    color = G.textMute,
                    style = TextStyle(fontSize = 12.sp),
                )
            }
        },
    )
}

@Composable
private fun TextInputDialog(
    title: String,
    initial: String,
    placeholder: String,
    onApply: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var input by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onCancel,
        confirmButton = {
            TextButton(onClick = { onApply(input.trim()) }) {
                Text(stringResource(R.string.action_save), color = G.text)
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = G.textDim) }
        },
        containerColor = G.bgElev1,
        title = { Text(title, color = G.text) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                placeholder = { Text(placeholder, color = G.textMute) },
                modifier = Modifier.fillMaxWidth(),
                colors = goloomTextFieldColors(),
            )
        },
    )
}
