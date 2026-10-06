package com.bosang.search.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.bosang.search.R

/** 디자인 시안 v3의 색 이름을 그대로 쓴다 */
@Immutable
data class BColors(
    val dark: Boolean,
    val bg: Color,
    val card: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val chip: Color,
    val chip2: Color,
    val brand: Color,
    val brand2: Color,
    val brandTint: Color,
    val rec: Color,
    val recTint: Color,
    val ok: Color,
    val okTint: Color,
    val warn: Color,
    val warnTint: Color,
    /** 떠 있는 바 배경 (흐림 효과 대신 거의 불투명하게) */
    val glass: Color,
    val glassLine: Color,
    /** 그림자 색 */
    val shadow: Color,
)

val LightB = BColors(
    dark = false,
    bg = Color(0xFFECEEF4),
    card = Color(0xFFFFFFFF),
    ink = Color(0xFF0C1222),
    ink2 = Color(0xFF5A6276),
    ink3 = Color(0xFF9AA1B1),
    line = Color(0x120C1222),
    chip = Color(0xFFF1F3F8),
    chip2 = Color(0xFFE5E8F0),
    brand = Color(0xFF3360FF),
    brand2 = Color(0xFF6A8CFF),
    brandTint = Color(0x1A3360FF),
    rec = Color(0xFFFF4B5C),
    recTint = Color(0x1CFF4B5C),
    ok = Color(0xFF14AE7A),
    okTint = Color(0x1F14AE7A),
    warn = Color(0xFFE8862F),
    warnTint = Color(0x24E8862F),
    glass = Color(0xF2FFFFFF),
    glassLine = Color(0xCCFFFFFF),
    shadow = Color(0xFF0C1222),
)

val DarkB = BColors(
    dark = true,
    bg = Color(0xFF0B0D13),
    card = Color(0xFF171A23),
    ink = Color(0xFFF2F4FA),
    ink2 = Color(0xFF9AA1B3),
    ink3 = Color(0xFF5F6679),
    line = Color(0x0FFFFFFF),
    chip = Color(0xFF20242F),
    chip2 = Color(0xFF2A2F3C),
    brand = Color(0xFF4D7BFF),
    brand2 = Color(0xFF7096FF),
    brandTint = Color(0x294D7BFF),
    rec = Color(0xFFFF5A69),
    recTint = Color(0x29FF4B5C),
    ok = Color(0xFF22C08A),
    okTint = Color(0x2914AE7A),
    warn = Color(0xFFF0954A),
    warnTint = Color(0x2EE8862F),
    glass = Color(0xF21E222E),
    glassLine = Color(0x14FFFFFF),
    shadow = Color(0xFF000000),
)

val LocalB = staticCompositionLocalOf { LightB }

object B {
    val c: BColors
        @Composable @ReadOnlyComposable get() = LocalB.current
}

val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)

val W4 = FontWeight.Normal
val W6 = FontWeight.SemiBold
val W7 = FontWeight.Bold
val W8 = FontWeight.ExtraBold

/** 글자 스타일 한 줄로: ts(15f, W7) */
fun ts(
    size: Float,
    weight: FontWeight = W4,
    tracking: Float = -0.012f,
    lineHeight: Float? = null,
    num: Boolean = false,
): TextStyle = TextStyle(
    fontFamily = Pretendard,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = tracking.em,
    lineHeight = lineHeight?.let { (size * it).sp } ?: TextStyle.Default.lineHeight,
    fontFeatureSettings = if (num) "tnum" else null,
)

@Composable
fun BosangTheme(content: @Composable () -> Unit) {
    val c = if (isSystemInDarkTheme()) DarkB else LightB
    val scheme = if (c.dark) {
        darkColorScheme(
            primary = c.brand, onPrimary = Color.White,
            background = c.bg, onBackground = c.ink,
            surface = c.card, onSurface = c.ink, onSurfaceVariant = c.ink2,
            surfaceContainer = c.card, surfaceContainerHigh = c.card, surfaceContainerHighest = c.chip,
            error = c.rec, outline = c.chip2, outlineVariant = c.line,
        )
    } else {
        lightColorScheme(
            primary = c.brand, onPrimary = Color.White,
            background = c.bg, onBackground = c.ink,
            surface = c.card, onSurface = c.ink, onSurfaceVariant = c.ink2,
            surfaceContainer = c.card, surfaceContainerHigh = c.card, surfaceContainerHighest = c.chip,
            error = c.rec, outline = c.chip2, outlineVariant = c.line,
        )
    }
    val base = Typography()
    val typography = Typography(
        displayLarge = base.displayLarge.copy(fontFamily = Pretendard),
        displayMedium = base.displayMedium.copy(fontFamily = Pretendard),
        displaySmall = base.displaySmall.copy(fontFamily = Pretendard),
        headlineLarge = base.headlineLarge.copy(fontFamily = Pretendard),
        headlineMedium = base.headlineMedium.copy(fontFamily = Pretendard),
        headlineSmall = base.headlineSmall.copy(fontFamily = Pretendard, fontWeight = W8),
        titleLarge = base.titleLarge.copy(fontFamily = Pretendard, fontWeight = W8),
        titleMedium = base.titleMedium.copy(fontFamily = Pretendard, fontWeight = W7),
        titleSmall = base.titleSmall.copy(fontFamily = Pretendard, fontWeight = W7),
        bodyLarge = base.bodyLarge.copy(fontFamily = Pretendard),
        bodyMedium = base.bodyMedium.copy(fontFamily = Pretendard),
        bodySmall = base.bodySmall.copy(fontFamily = Pretendard),
        labelLarge = base.labelLarge.copy(fontFamily = Pretendard, fontWeight = W7),
        labelMedium = base.labelMedium.copy(fontFamily = Pretendard, fontWeight = W7),
        labelSmall = base.labelSmall.copy(fontFamily = Pretendard, fontWeight = W7),
    )
    CompositionLocalProvider(LocalB provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
