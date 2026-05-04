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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import app.goloom.client.data.ProfileStore
import app.goloom.client.design.G
import app.goloom.client.design.GButton
import app.goloom.client.design.GButtonVariant
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GTopBar
import app.goloom.client.design.MonoBodySmall

@Composable
fun ProfilesScreen(
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenImport: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { ProfileStore.get(context) }
    val profiles by store.profiles.collectAsState()
    val activeId by store.activeId.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(G.bg)
            .windowInsetsPadding(WindowInsets.systemBars),
    ) {
        GTopBar(
            left = {
                GIconBtn(
                    onClick = onBack,
                    icon = { Icon(GIcons.Back, null, tint = G.text) },
                )
                Text(
                    text = stringResource(R.string.profiles_title),
                    color = G.text,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            },
        )
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(profiles, key = { it.id }) { profile ->
                ProfileListItem(
                    name = profile.name,
                    server = profile.serverDisplay,
                    isActive = profile.id == activeId,
                    onClick = { onOpenProfile(profile.id) },
                )
            }
            item {
                Spacer(modifier = Modifier.height(8.dp))
                GButton(
                    text = stringResource(R.string.profile_import_button),
                    onClick = onOpenImport,
                    variant = GButtonVariant.Ghost,
                    fullWidth = true,
                    leading = { Icon(GIcons.Plus, null, modifier = Modifier.size(16.dp)) },
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ProfileListItem(
    name: String,
    server: String,
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
                text = name,
                color = G.text,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = server,
                color = G.textDim,
                style = MonoBodySmall,
            )
        }
        Icon(GIcons.ChevronRight, null, tint = G.textMute, modifier = Modifier.size(14.dp))
    }
}
