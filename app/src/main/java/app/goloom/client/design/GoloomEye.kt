package app.goloom.client.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Состояния анимации ока. Соответствует ConnectionState на UI-слое:
 * - [Idle] — туннель не запущен, око приглушённое
 * - [Connecting] — handshake в процессе, корона активно вращается, зрачок пульсирует
 * - [On] — туннель установлен, ободок становится платиновым
 */
enum class EyeState { Idle, Connecting, On }

/**
 * Огненный глаз Goloom — главный визуальный элемент бренда.
 * Реализация повторяет SVG-прототип `FieryEye` из дизайн-канваса
 * (см. `goloom-system.jsx`), переписанный на Compose Canvas.
 *
 * Анимации запускаются только в [EyeState.Connecting]:
 *   - back-corona вращается на 360° за 28 сек
 *   - front-corona — против часовой за 18 сек
 *   - вертикальный зрачок пульсирует (sy 1↔0.86, sx 1↔1.08) каждые 1.8 сек
 *   - flicker (opacity + tiny scale) — встроен в front-corona, 1.6 сек
 *
 * 14 лепестков пламени строятся процедурно — детерминированный seed
 * (sin/cos смещения по индексу), без генерации случайных чисел в кадре.
 */
@Composable
fun GoloomEye(
    size: Dp,
    state: EyeState,
    modifier: Modifier = Modifier,
) {
    val petals = remember { buildPetals(numPetals = 14) }

    val transition = rememberInfiniteTransition(label = "goloom-eye")
    val animating = state == EyeState.Connecting

    val backRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animating) 360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(28_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "back-corona",
    )
    val frontRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animating) -360f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(18_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "front-corona",
    )
    val flicker by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = if (animating) 1f else 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flicker",
    )
    val pupilScaleY by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animating) 0.86f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pupil-y",
    )
    val pupilScaleX by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (animating) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pupil-x",
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(size)) {
            val s = this.size.minDimension
            val cx = this.size.width / 2f
            val cy = this.size.height / 2f
            val irisR = s * 0.26f
            val pupilHalfW = s * 0.045f
            val pupilHalfH = s * 0.32f

            // Halo — внешний размытый круг подсветки
            drawHalo(animating, state == EyeState.On, cx, cy, s)

            // Corona — огненная корона из лепестков
            if (animating || state == EyeState.On) {
                drawCorona(
                    petals = petals,
                    cx = cx,
                    cy = cy,
                    s = s,
                    irisR = irisR,
                    backRotation = backRotation,
                    frontRotation = frontRotation,
                    flickerOpacity = flicker,
                    animating = animating,
                )
            }

            // Iris — горящая сфера
            drawIris(
                state = state,
                animating = animating,
                cx = cx,
                cy = cy,
                irisR = irisR,
            )

            // Striations — радиальные линии-штрихи внутри радужки
            drawStriations(animating, cx, cy, irisR)

            // Pupil — вертикальный кошачий зрачок
            drawPupil(
                cx = cx,
                cy = cy,
                halfW = pupilHalfW * pupilScaleX,
                halfH = pupilHalfH * pupilScaleY,
            )
        }
    }
}

/** Один лепесток пламени, описывается полярным углом и длиной. */
private data class Petal(
    val angle: Float,         // полярный угол центра, радианы
    val lengthFactor: Float,  // 0.42..0.78 — относительно радиуса
    val wobble: Float,        // 0..1 — асимметрия / curl
    val curlPhase: Float,     // sin(i*3.1) для бокового изгиба
)

private fun buildPetals(numPetals: Int): List<Petal> = buildList(numPetals) {
    repeat(numPetals) { i ->
        val a = (i.toFloat() / numPetals) * (2f * PI.toFloat())
        val lenSeed = (sin(i * 1.7f) * 0.5f + 0.5f)
        val len = 0.42f + lenSeed * 0.36f
        val wobble = (sin(i * 2.3f) * 0.5f + 0.5f) * 0.4f
        val curl = sin(i * 3.1f)
        add(Petal(a, len, wobble, curl))
    }
}

/**
 * Path одного лепестка: квадратичные кривые от двух базовых точек на
 * окружности радужки к "кончику" лепестка через смещённые контрольные.
 */
private fun petalPath(
    petal: Petal,
    cx: Float,
    cy: Float,
    s: Float,
    irisR: Float,
    lengthScale: Float,
): Path {
    val a = petal.angle
    val tipR = s * 0.5f * petal.lengthFactor * lengthScale
    val baseSpread = 0.18f + petal.wobble * 0.05f
    val innerR = irisR * 0.95f

    val tipX = cx + cos(a) * tipR
    val tipY = cy + sin(a) * tipR

    val lA = a - baseSpread
    val rA = a + baseSpread
    val lx = cx + cos(lA) * innerR
    val ly = cy + sin(lA) * innerR
    val rx = cx + cos(rA) * innerR
    val ry = cy + sin(rA) * innerR

    val curlMag = petal.curlPhase * (s * 0.04f)
    val cosPerp = cos(a + PI.toFloat() / 2f)
    val sinPerp = sin(a + PI.toFloat() / 2f)
    val ctlX = (lx + tipX) / 2f + cosPerp * curlMag
    val ctlY = (ly + tipY) / 2f + sinPerp * curlMag
    val ctrX = (rx + tipX) / 2f - cosPerp * curlMag
    val ctrY = (ry + tipY) / 2f - sinPerp * curlMag

    return Path().apply {
        moveTo(lx, ly)
        quadraticTo(ctlX, ctlY, tipX, tipY)
        quadraticTo(ctrX, ctrY, rx, ry)
        close()
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHalo(
    animating: Boolean,
    isOn: Boolean,
    cx: Float,
    cy: Float,
    s: Float,
) {
    val brush = if (animating) {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0x47FF9650),       // alpha 0.28
                0.32f to Color(0x1AC8541C),    // alpha 0.10
                0.6f to Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = s * 0.6f,
        )
    } else {
        Brush.radialGradient(
            colorStops = arrayOf(
                0f to if (isOn) Color(0x14FFFFFF) else Color(0x0FFFB478),
                0.55f to Color.Transparent,
            ),
            center = Offset(cx, cy),
            radius = s * 0.55f,
        )
    }
    drawCircle(brush = brush, radius = s * 0.55f, center = Offset(cx, cy))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCorona(
    petals: List<Petal>,
    cx: Float,
    cy: Float,
    s: Float,
    irisR: Float,
    backRotation: Float,
    frontRotation: Float,
    flickerOpacity: Float,
    animating: Boolean,
) {
    val flameBrush = Brush.radialGradient(
        colorStops = arrayOf(
            0f to Color(0xF2FFE9B0),      // ember core, alpha 0.95
            0.22f to Color(0xCCFFAA5A),    // alpha 0.80
            0.55f to Color(0x8CC8541C),    // alpha 0.55
            1f to Color(0x00782808),       // прозрачный спад
        ),
        center = Offset(cx, cy),
        radius = s * 0.55f,
    )

    // Back-layer — увеличенные лепестки, dim
    rotate(degrees = backRotation, pivot = Offset(cx, cy)) {
        petals.forEach { petal ->
            val path = petalPath(petal, cx, cy, s, irisR, lengthScale = 1.18f)
            drawPath(
                path = path,
                brush = flameBrush,
                alpha = if (animating) 0.55f else 0.3f,
            )
        }
    }

    // Front-layer — обычный размер, ярче, c flicker
    rotate(degrees = frontRotation, pivot = Offset(cx, cy)) {
        petals.forEach { petal ->
            val path = petalPath(petal, cx, cy, s, irisR, lengthScale = 1.0f)
            drawPath(
                path = path,
                brush = flameBrush,
                alpha = flickerOpacity,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawIris(
    state: EyeState,
    animating: Boolean,
    cx: Float,
    cy: Float,
    irisR: Float,
) {
    val brush = when {
        state == EyeState.On -> Brush.radialGradient(
            colorStops = arrayOf(
                0f to G.bg,
                0.55f to G.bgElev2,
                0.92f to G.ringSoft,
                1f to Color(0xD9FFFFFF),
            ),
            center = Offset(cx, cy),
            radius = irisR,
        )
        animating -> Brush.radialGradient(
            colorStops = arrayOf(
                0f to G.bg,
                0.36f to G.emberDeep.copy(alpha = 0.4f),
                0.62f to G.emberDeep,
                0.84f to G.emberHot,
                1f to G.emberCore,
            ),
            center = Offset(cx, cy),
            radius = irisR,
        )
        else -> Brush.radialGradient(
            colorStops = arrayOf(
                0f to Color(0xFF08080A),
                0.6f to Color(0xFF15100D),
                0.92f to Color(0xFF3A2218),
                1f to Color(0xFF6A4028),
            ),
            center = Offset(cx, cy),
            radius = irisR,
        )
    }
    drawCircle(brush = brush, radius = irisR, center = Offset(cx, cy))

    // Резкий внешний ободок радужки
    drawCircle(
        color = if (animating) Color(0x8CFF9650) else Color(0x1AFFFFFF),
        radius = irisR,
        center = Offset(cx, cy),
        style = Stroke(width = 0.8f),
    )
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStriations(
    animating: Boolean,
    cx: Float,
    cy: Float,
    irisR: Float,
) {
    val color = if (animating) Color(0x1FFFC88C) else Color(0x0AFFFFFF)
    val n = 36
    for (i in 0 until n) {
        val a = (i.toFloat() / n) * (2f * PI.toFloat())
        val x1 = cx + cos(a) * (irisR * 0.45f)
        val y1 = cy + sin(a) * (irisR * 0.45f)
        val x2 = cx + cos(a) * (irisR * 0.96f)
        val y2 = cy + sin(a) * (irisR * 0.96f)
        drawLine(color = color, start = Offset(x1, y1), end = Offset(x2, y2), strokeWidth = 0.6f)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawPupil(
    cx: Float,
    cy: Float,
    halfW: Float,
    halfH: Float,
) {
    // Soft outer ellipse — мягкий чёрный размытого края
    drawOval(
        color = Color(0xD9000000),
        topLeft = Offset(cx - halfW * 1.6f, cy - halfH * 1.05f),
        size = Size(halfW * 2f * 1.6f, halfH * 2f * 1.05f),
    )
    // Hard inner ellipse — собственно зрачок
    drawOval(
        color = Color.Black,
        topLeft = Offset(cx - halfW, cy - halfH),
        size = Size(halfW * 2f, halfH * 2f),
    )
    // Tiny highlight — блик в зрачке
    drawOval(
        color = Color(0x73FFDC96),
        topLeft = Offset(cx + halfW * 0.3f - halfW * 0.18f, cy - halfH * 0.4f - halfH * 0.06f),
        size = Size(halfW * 0.18f * 2f, halfH * 0.06f * 2f),
    )
}

