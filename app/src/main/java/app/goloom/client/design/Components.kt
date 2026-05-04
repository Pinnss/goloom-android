package app.goloom.client.design

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Дизайн-компоненты Goloom (palette 0). Имена с префиксом G — соответствуют
 * `goloom-system.jsx`. Менять только синхронно с дизайн-кодом.
 */

// ─────────────────────────────────────────────────────────────
// Buttons
// ─────────────────────────────────────────────────────────────

enum class GButtonVariant { Primary, Ghost, Subtle }
enum class GButtonSize { Sm, Md, Lg }

@Composable
fun GButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: GButtonVariant = GButtonVariant.Subtle,
    size: GButtonSize = GButtonSize.Md,
    fullWidth: Boolean = false,
    danger: Boolean = false,
    enabled: Boolean = true,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val (height, paddingH, fontSize, gap, corner) = when (size) {
        GButtonSize.Sm -> Quint(32.dp, 12.dp, 13.sp, 6.dp, 10.dp)
        GButtonSize.Md -> Quint(44.dp, 16.dp, 15.sp, 8.dp, 12.dp)
        GButtonSize.Lg -> Quint(56.dp, 22.dp, 16.sp, 10.dp, 14.dp)
    }

    val (bg, fg, borderColor) = when (variant) {
        GButtonVariant.Primary -> Triple(G.text, G.bg, Color.Transparent)
        GButtonVariant.Ghost -> Triple(Color.Transparent, G.text, G.borderStrong)
        GButtonVariant.Subtle -> Triple(G.bgElev2, G.text, G.border)
    }
    val effectiveFg = if (danger) G.err else fg
    val widthMod = if (fullWidth) Modifier.fillMaxWidth() else Modifier

    Box(
        modifier = modifier
            .then(widthMod)
            .height(height)
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(corner))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = paddingH),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            leading?.invoke()
            Text(
                text = text,
                color = effectiveFg.copy(alpha = if (enabled) 1f else 0.4f),
                style = TextStyle(
                    fontSize = fontSize,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = (-0.1).sp,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke()
        }
    }
}

private data class Quint(val a: Dp, val b: Dp, val c: androidx.compose.ui.unit.TextUnit, val d: Dp, val e: Dp)

// ─────────────────────────────────────────────────────────────
// Icon button (без фона, размер 36dp)
// ─────────────────────────────────────────────────────────────

@Composable
fun GIconBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    icon: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = Modifier.size(iconSize)) { icon() }
    }
}

// ─────────────────────────────────────────────────────────────
// Card / Section label / Row / Chevron / Switch / Dot
// ─────────────────────────────────────────────────────────────

@Composable
fun GCard(
    modifier: Modifier = Modifier,
    padding: PaddingValues = PaddingValues(16.dp),
    elev: Int = 1,
    content: @Composable () -> Unit,
) {
    val bg = if (elev >= 2) G.bgElev2 else G.bgElev1
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(GMetrics.cornerLg))
            .background(bg)
            .border(1.dp, G.border, RoundedCornerShape(GMetrics.cornerLg))
            .padding(padding),
    ) {
        content()
    }
}

@Composable
fun GSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = G.textMute,
        modifier = modifier.padding(horizontal = GMetrics.sectionLabelPadding, vertical = 8.dp),
        style = TextStyle(
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.2.sp,
        ),
    )
}

@Composable
fun GRow(
    title: String,
    modifier: Modifier = Modifier,
    detail: String? = null,
    last: Boolean = false,
    danger: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    Column {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = GMetrics.rowPaddingH, vertical = GMetrics.rowPaddingV),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(GMetrics.cornerSm))
                        .background(G.bgElev3),
                    contentAlignment = Alignment.Center,
                ) { icon() }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = if (danger) G.err else G.text,
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        color = G.textDim,
                        style = TextStyle(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            trailing?.invoke()
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
}

@Composable
fun GChev(modifier: Modifier = Modifier, color: Color = G.textMute) {
    Icon(
        imageVector = GIcons.ChevronRight,
        contentDescription = null,
        modifier = modifier.size(14.dp),
        tint = color,
    )
}

@Composable
fun GSwitch(
    on: Boolean,
    onToggle: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val bgColor = if (on) G.text else G.bgElev3
    val thumbColor = if (on) G.bg else G.text
    Box(
        modifier = modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(bgColor)
            .border(1.dp, if (on) G.text else G.border, RoundedCornerShape(13.dp))
            .let { if (onToggle != null) it.clickable { onToggle(!on) } else it },
    ) {
        val align = if (on) Alignment.CenterEnd else Alignment.CenterStart
        Box(
            modifier = Modifier
                .padding(2.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(thumbColor)
                .align(align),
        )
    }
}

@Composable
fun GDot(color: Color = G.text, size: Dp = 8.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(color),
    )
}

// ─────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────

@Composable
fun GTopBar(
    modifier: Modifier = Modifier,
    left: @Composable () -> Unit = {},
    right: @Composable () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(GMetrics.topBarHeight)
            .padding(horizontal = GMetrics.topBarPaddingH),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { left() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) { right() }
    }
}

// ─────────────────────────────────────────────────────────────
// Goloom logo (small wordmark)
// ─────────────────────────────────────────────────────────────

@Composable
fun GoloomLogo(
    modifier: Modifier = Modifier,
    showWord: Boolean = true,
    size: Dp = 18.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        // Маленькое око — упрощённый glyph для шапки
        Box(modifier = Modifier.size(size + 8.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = this.size.width
                val h = this.size.height
                val cx = w / 2f
                val cy = h / 2f
                // Внешний контур (миндалевидный)
                val brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to G.emberDeep,
                        0.6f to G.emberHot,
                        1f to G.emberCore,
                    ),
                    center = Offset(cx, cy),
                    radius = w * 0.45f,
                )
                drawCircle(brush = brush, radius = w * 0.32f, center = Offset(cx, cy))
                drawCircle(
                    color = G.borderStrong,
                    radius = w * 0.32f,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1f),
                )
                // Зрачок-щёлка
                drawOval(
                    color = Color.Black,
                    topLeft = Offset(cx - w * 0.04f, cy - w * 0.18f),
                    size = androidx.compose.ui.geometry.Size(w * 0.08f, w * 0.36f),
                )
            }
        }
        if (showWord) {
            Text(
                text = "goloom",
                color = G.text,
                style = TextStyle(
                    fontSize = (size.value * 0.95f).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.2.sp,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Profile pill (на главной)
// ─────────────────────────────────────────────────────────────

@Composable
fun GProfilePill(
    name: String,
    server: String,
    profileLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(G.bgElev1)
            .border(1.dp, G.border, RoundedCornerShape(14.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(G.bgElev3),
            contentAlignment = Alignment.Center,
        ) {
            Icon(GIcons.Server, null, modifier = Modifier.size(17.dp), tint = G.textDim)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = profileLabel.uppercase(),
                color = G.textMute,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                ),
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = name,
                color = G.text,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(GIcons.ChevronDown, null, modifier = Modifier.size(16.dp), tint = G.textDim)
    }
}

// ─────────────────────────────────────────────────────────────
// Update banner (в notification slot главного)
// ─────────────────────────────────────────────────────────────

@Composable
fun GUpdateBanner(
    visible: Boolean,
    version: String,
    title: String,
    sub: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(G.bgElev1)
                .border(1.dp, G.borderStrong, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(G.warnTint)
                    .border(1.dp, G.warnBorder, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(GIcons.Sparkle, null, modifier = Modifier.size(16.dp), tint = G.warn)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title.format(version),
                    color = G.text,
                    style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 16.sp),
                )
                Text(
                    text = sub,
                    color = G.textDim,
                    style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp),
                )
            }
            GButton(text = actionText, onClick = onAction, variant = GButtonVariant.Primary, size = GButtonSize.Sm)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Connect dial — главная кнопка с глазом внутри
// ─────────────────────────────────────────────────────────────

@Composable
fun GConnectButton(
    state: EyeState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = GMetrics.connectDial,
) {
    val ringStroke = when (state) {
        EyeState.Connecting -> 1.5.dp
        EyeState.On -> 1.5.dp
        EyeState.Idle -> 1.dp
    }
    val ringColor = when (state) {
        EyeState.Idle -> Color(0x38FFFFFF)
        EyeState.Connecting -> Color(0xFFFFFFFF)
        EyeState.On -> Color(0xF2FFFFFF)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // Тёмный градиентный фон диска
        Canvas(modifier = Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            drawCircle(
                brush = Brush.radialGradient(
                    colorStops = arrayOf(
                        0f to Color(0xFF14141A),
                        0.7f to Color(0xFF08080B),
                        1f to Color(0x80020203),
                    ),
                    center = Offset(this.size.width / 2f, this.size.height * 0.45f),
                    radius = r,
                ),
                radius = r,
            )
            // Внутренний ободок
            drawCircle(
                color = ringColor,
                radius = r - (ringStroke.toPx() / 2f),
                style = Stroke(width = ringStroke.toPx()),
            )
            // 60 рисок-граду шкалы
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val n = 60
            for (i in 0 until n) {
                val a = (i.toFloat() / n) * (2f * PI.toFloat()) - PI.toFloat() / 2f
                val r1 = r - 12f
                val r2 = r - if (i % 5 == 0) 18f else 15f
                val x1 = cx + cos(a) * r1
                val y1 = cy + sin(a) * r1
                val x2 = cx + cos(a) * r2
                val y2 = cy + sin(a) * r2
                val opacity = if (i % 5 == 0) 0.35f else 0.12f
                drawLine(
                    color = Color.White.copy(alpha = opacity),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = 1f,
                )
            }
        }
        // Глаз внутри диска
        GoloomEye(size = size * 0.62f, state = state)
    }
}
