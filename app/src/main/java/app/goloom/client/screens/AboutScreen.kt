package app.goloom.client.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.goloom.client.BuildConfig
import app.goloom.client.R
import app.goloom.client.design.EyeState
import app.goloom.client.design.G
import app.goloom.client.design.GButton
import app.goloom.client.design.GButtonVariant
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.design.GTopBar
import app.goloom.client.design.GoloomEye

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

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
                    text = stringResource(R.string.about_title),
                    color = G.text,
                    style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                )
            },
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GoloomEye(size = 140.dp, state = EyeState.Idle)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.app_name),
                color = G.text,
                style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.Bold),
            )
            Text(
                text = "v${BuildConfig.VERSION_NAME}",
                color = G.textDim,
                style = TextStyle(fontSize = 14.sp),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.about_subtitle),
                color = G.textDim,
                style = TextStyle(fontSize = 14.sp),
            )
            Spacer(modifier = Modifier.height(28.dp))
            GButton(
                text = stringResource(R.string.about_open_github),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://github.com/${BuildConfig.GITHUB_REPO}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                variant = GButtonVariant.Ghost,
                fullWidth = true,
            )
            Spacer(modifier = Modifier.height(10.dp))
            GButton(
                text = stringResource(R.string.about_open_sdk),
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse("https://github.com/Pinnss/goloom-poc")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                },
                variant = GButtonVariant.Ghost,
                fullWidth = true,
            )
        }
    }
}
