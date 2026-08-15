package org.quickping.app.core.design

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import org.quickping.app.R

object QuickPingColors {
    val Background = Color(0xFF05070A)
    val BackgroundRaised = Color(0xFF090B0F)
    val Surface = Color(0xFF101318)
    val SurfaceHigh = Color(0xFF171A20)
    val SurfacePressed = Color(0xFF1D222B)
    val Border = Color(0xFF242933)
    val BorderSoft = Color(0xFF181C23)
    val Primary = Color(0xFF2E78F7)
    val PrimaryLight = Color(0xFF8BB1FF)
    val TextPrimary = Color(0xFFDCE5F9)
    val TextSecondary = Color(0xFF9299A8)
    val TextMuted = Color(0xFF5D6574)
    val Success = Color(0xFF37C985)
    val Warning = Color(0xFFE4B75B)
    val Danger = Color(0xFFC12536)
    val Scrim = Color(0xB805070A)
}

val Peyda = FontFamily(
    Font(R.font.peyda_extra_light, FontWeight.ExtraLight),
    Font(R.font.peyda_light, FontWeight.Light),
    Font(R.font.peyda_regular, FontWeight.Normal),
    Font(R.font.peyda_medium, FontWeight.Medium),
    Font(R.font.peyda_semi_bold, FontWeight.SemiBold),
    Font(R.font.peyda_bold, FontWeight.Bold),
    Font(R.font.peyda_extra_bold, FontWeight.ExtraBold),
    Font(R.font.peyda_black, FontWeight.Black),
)

val MonaSans = FontFamily(
    Font(R.font.mona_sans_extra_light, FontWeight.ExtraLight),
    Font(R.font.mona_sans_light, FontWeight.Light),
    Font(R.font.mona_sans_regular, FontWeight.Normal),
    Font(R.font.mona_sans_medium, FontWeight.Medium),
    Font(R.font.mona_sans_semi_bold, FontWeight.SemiBold),
    Font(R.font.mona_sans_bold, FontWeight.Bold),
    Font(R.font.mona_sans_extra_bold, FontWeight.ExtraBold),
    Font(R.font.mona_sans_black, FontWeight.Black),
)

val Unbounded = FontFamily(Font(R.font.unbounded_regular, FontWeight.Normal))
val Bitcount = FontFamily(Font(R.font.bitcount_prop_single_light, FontWeight.Light))

val LocalQuickPingLanguage = staticCompositionLocalOf { "fa" }

@Composable
fun quickText(
    fa: String,
    en: String,
    nl: String = en,
    ar: String = en,
    tr: String = en,
    ru: String = en,
    hi: String = en,
    zh: String = en,
    ur: String = en,
): String = when (LocalQuickPingLanguage.current.lowercase(Locale.US)) {
    "fa" -> fa
    "nl" -> nl.takeUnless { it == en } ?: LoginTranslations.translate("nl", en) ?: QuickPingTranslations.translate("nl", en) ?: en
    "ar" -> ar.takeUnless { it == en } ?: LoginTranslations.translate("ar", en) ?: QuickPingTranslations.translate("ar", en) ?: en
    "tr" -> tr.takeUnless { it == en } ?: LoginTranslations.translate("tr", en) ?: QuickPingTranslations.translate("tr", en) ?: en
    "ru" -> ru.takeUnless { it == en } ?: LoginTranslations.translate("ru", en) ?: QuickPingTranslations.translate("ru", en) ?: en
    "hi" -> hi.takeUnless { it == en } ?: LoginTranslations.translate("hi", en) ?: QuickPingTranslations.translate("hi", en) ?: en
    "zh" -> zh.takeUnless { it == en } ?: LoginTranslations.translate("zh", en) ?: QuickPingTranslations.translate("zh", en) ?: en
    "ur" -> ur.takeUnless { it == en } ?: LoginTranslations.translate("ur", en) ?: QuickPingTranslations.translate("ur", en) ?: en
    else -> en
}

private fun quickPingTypography(font: FontFamily) = Typography(
    displayLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp),
    headlineLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Normal, fontSize = 11.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = font, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = font, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 15.sp),
)

private val colorScheme = darkColorScheme(
    primary = QuickPingColors.Primary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF173D7A),
    onPrimaryContainer = QuickPingColors.TextPrimary,
    background = QuickPingColors.Background,
    onBackground = QuickPingColors.TextPrimary,
    surface = QuickPingColors.Surface,
    onSurface = QuickPingColors.TextPrimary,
    surfaceVariant = QuickPingColors.SurfaceHigh,
    onSurfaceVariant = QuickPingColors.TextSecondary,
    outline = QuickPingColors.Border,
    error = QuickPingColors.Danger,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

@Composable
fun QuickPingTheme(
    languageCode: String = Locale.getDefault().language,
    content: @Composable () -> Unit,
) {
    val normalizedLanguage = languageCode.lowercase(Locale.US)
    val isRtlLanguage = remember(normalizedLanguage) {
        normalizedLanguage in setOf("fa", "فارسی", "ar", "العربية", "ur", "اردو")
    }
    CompositionLocalProvider(
        LocalLayoutDirection provides if (isRtlLanguage) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalQuickPingLanguage provides normalizedLanguage,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = quickPingTypography(if (isRtlLanguage) Peyda else MonaSans),
            shapes = shapes,
            content = content,
        )
    }
}
