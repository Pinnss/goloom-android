package app.goloom.client.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Дизайн-токены Goloom — палитра 0 (моно с белым акцентом и ember-палитрой
 * для горящего ока). Источник истины — `goloom-system.jsx` из дизайн-бандла.
 *
 * Имя `G` намеренно короткое — повторяется на каждом экране, длинное имя
 * визуально мешает. Менять только синхронно с дизайн-кодом.
 */
object G {
    // Backgrounds — 4 уровня elevation на чёрной канве
    val bg = Color(0xFF0A0A0B)
    val bgElev1 = Color(0xFF121215)
    val bgElev2 = Color(0xFF1A1A1D)
    val bgElev3 = Color(0xFF222227)

    // Borders — полупрозрачный белый, два веса
    val border = Color(0x14FFFFFF)        // alpha 0.08
    val borderStrong = Color(0x24FFFFFF)  // alpha 0.14

    // Text — три веса по контрасту
    val text = Color(0xFFF2F2F4)
    val textDim = Color(0x9EF2F2F4)       // alpha 0.62
    val textMute = Color(0x61F2F2F4)      // alpha 0.38

    // Status — приглушённые, не неоновые
    val ok = Color(0xFF9FE6B5)
    val warn = Color(0xFFE8C36B)
    val err = Color(0xFFE58F8F)

    // Ring — белый акцент (на коннект-кнопке)
    val ring = Color(0xFFFFFFFF)
    val ringSoft = Color(0x8CFFFFFF)      // alpha 0.55

    // Ember palette — для огненного ока
    val emberCore = Color(0xFFFFE9B0)
    val emberMid = Color(0xFFF0A56A)
    val emberHot = Color(0xFFC8541C)
    val emberDeep = Color(0xFF5A1B0B)

    // Warm warn-tinted overlay для update-баннера
    val warnTint = Color(0x1FE8C36B)      // alpha ~0.12
    val warnBorder = Color(0x40E8C36B)    // alpha ~0.25
}

/**
 * Геометрические токены. dp везде, кроме шрифтов (sp).
 */
object GMetrics {
    val cornerSm = 8.dp
    val cornerMd = 12.dp
    val cornerLg = 16.dp
    val cornerXl = 24.dp

    // Высоты кнопок — sm/md/lg
    val btnHeightSm = 32.dp
    val btnHeightMd = 44.dp
    val btnHeightLg = 56.dp

    // Главная кнопка-око
    val connectDial = 248.dp
    val connectDialPadding = 12.dp

    // Top bar
    val topBarHeight = 48.dp
    val topBarPaddingH = 18.dp

    // Бордер карточки/строки
    val rowPaddingH = 16.dp
    val rowPaddingV = 14.dp
    val sectionLabelPadding = 4.dp
}
