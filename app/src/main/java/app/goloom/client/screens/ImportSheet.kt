package app.goloom.client.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.goloom.client.R
import app.goloom.client.data.ConnStrParser
import app.goloom.client.data.Profile
import app.goloom.client.data.ProfileStore
import app.goloom.client.design.G
import app.goloom.client.design.GButton
import app.goloom.client.design.GButtonVariant
import app.goloom.client.design.GIconBtn
import app.goloom.client.design.GIcons
import app.goloom.client.util.Clipboard
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    onDismiss: () -> Unit,
    onImported: (String) -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val store = remember { ProfileStore.get(context) }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun handleScannedString(text: String) {
        when (val r = ConnStrParser.parse(text)) {
            is ConnStrParser.Result.Ok -> {
                val profile = Profile.fromConnStr(r.value)
                store.add(profile)
                store.setActive(profile.id)
                onImported(profile.name)
            }
            is ConnStrParser.Result.Error -> {
                onError(context.getString(R.string.import_invalid))
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = G.bgElev1,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 8.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(G.borderStrong),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.import_title),
                    color = G.text,
                    style = TextStyle(fontSize = 19.sp, fontWeight = FontWeight.SemiBold),
                )
                GIconBtn(
                    onClick = onDismiss,
                    icon = { Icon(GIcons.Close, null, tint = G.text) },
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(android.graphics.Color.BLACK.let { androidx.compose.ui.graphics.Color(it) })
                    .border(1.dp, G.border, RoundedCornerShape(18.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (hasCameraPermission) {
                    QrScannerView(onResult = ::handleScannedString)
                } else {
                    Text(
                        text = stringResource(R.string.import_camera_permission),
                        color = G.textDim,
                        modifier = Modifier.padding(24.dp),
                    )
                }
                // Углы рамки сверху
                Box(modifier = Modifier.fillMaxSize().padding(18.dp)) {
                    CornerBracket(Alignment.TopStart, rotation = 0f)
                    CornerBracket(Alignment.TopEnd, rotation = 90f)
                    CornerBracket(Alignment.BottomEnd, rotation = 180f)
                    CornerBracket(Alignment.BottomStart, rotation = 270f)
                }
                Text(
                    text = stringResource(R.string.import_qr_hint),
                    color = G.textDim,
                    style = TextStyle(fontSize = 12.sp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
                )
            }

            // Divider with label
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(modifier = Modifier
                    .height(1.dp)
                    .weight(1f)
                    .background(G.border))
                Text(
                    text = stringResource(R.string.import_or).uppercase(),
                    color = G.textMute,
                    style = TextStyle(
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                    ),
                )
                Box(modifier = Modifier
                    .height(1.dp)
                    .weight(1f)
                    .background(G.border))
            }

            GButton(
                text = stringResource(R.string.import_paste),
                onClick = {
                    val raw = Clipboard.read(context)
                    if (raw.isNullOrBlank()) {
                        onError(context.getString(R.string.import_clipboard_empty))
                    } else {
                        handleScannedString(raw)
                    }
                },
                variant = GButtonVariant.Ghost,
                fullWidth = true,
                leading = { Icon(GIcons.Clipboard, null, modifier = Modifier.size(16.dp)) },
            )
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CornerBracket(alignment: Alignment, rotation: Float) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(alignment)
                .size(28.dp),
        ) {
            // Простые «уголки»: рисуем две стороны буквой Г, поворачиваем.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
                    .border(
                        width = 2.dp,
                        color = G.text,
                        shape = when (rotation.toInt()) {
                            0 -> RoundedCornerShape(topStart = 4.dp)
                            90 -> RoundedCornerShape(topEnd = 4.dp)
                            180 -> RoundedCornerShape(bottomEnd = 4.dp)
                            else -> RoundedCornerShape(bottomStart = 4.dp)
                        },
                    ),
            )
        }
    }
}

@Composable
private fun QrScannerView(onResult: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isScanning by remember { mutableStateOf(true) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = androidx.camera.core.Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                val scanner = BarcodeScanning.getClient()
                analyzer.setAnalyzer(executor) { proxy: ImageProxy ->
                    if (!isScanning) {
                        proxy.close(); return@setAnalyzer
                    }
                    val mediaImage = proxy.image
                    if (mediaImage != null) {
                        val img = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
                        scanner.process(img)
                            .addOnSuccessListener { barcodes ->
                                val text = barcodes.firstOrNull { it.format == Barcode.FORMAT_QR_CODE }?.rawValue
                                if (!text.isNullOrBlank()) {
                                    isScanning = false
                                    onResult(text)
                                }
                            }
                            .addOnCompleteListener { proxy.close() }
                    } else {
                        proxy.close()
                    }
                }
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analyzer,
                )
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
    )
}
