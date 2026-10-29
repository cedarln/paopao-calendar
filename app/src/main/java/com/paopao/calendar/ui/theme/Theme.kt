package com.paopao.calendar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.paopao.calendar.data.settings.ThemeMode

/**
 * 配色。
 *
 * 主色刻意选了一个偏暖的青绿而不是日历类应用常见的蓝：应用商店里「蓝色日历」
 * 至少有二十个，而泡泡日历的产品主张是「干净、无广告」，视觉上需要一个记忆点。
 */
private val Seed = Color(0xFF2E8B7A)
private val SeedDark = Color(0xFF7FD3BE)
private val Accent = Color(0xFFE07B54)

/** 四象限各一格的强调色。Q1 用最刺激的红，Q4 用最安静的灰。 */
val QuadrantQ1 = Color(0xFFD9534F)
val QuadrantQ2 = Color(0xFF2E8B7A)
val QuadrantQ3 = Color(0xFFE0A52B)
val QuadrantQ4 = Color(0xFF8D9099)

val LunarText = Color(0xFF9AA0A6)
val HolidayBadge = Color(0xFFD9534F)
val FestivalBadge = Color(0xFFE07B54)
val SolarTermBadge = Color(0xFF2E8B7A)
val OutOfMonthText = Color(0xFFC7CBD1)

/**
 * 训练模块的强调色：月视图角标、打卡圆点、计时环都从这一路取。
 *
 * 之所以单开一色而不是复用 primary：日历格子上「今天有日程」已经是青绿点，
 * 训练再叠同一种颜色，两种信息就变成同一条信息了。
 */
val TrainingLight = Color(0xFF3F7FA8)
val TrainingDark = Color(0xFF8FC7DE)

/**
 * 习惯在日历格子里的标记色。
 *
 * 日程是青绿、事项是珊瑚、训练是蓝，剩下能一眼分开的只有紫这一路，而色板里习惯
 * 恰好也常挑紫色，语义对得上。刻意比前三档浅一点：一个格子最多同时挂四个标记，
 * 谁都比谁抢眼就不叫标记了。
 */
val HabitMarkLight = Color(0xFF7C5CBF)
val HabitMarkDark = Color(0xFFB9A0E8)

/**
 * 日历上的习惯标记色。
 *
 * 取的是当前配色方案的背景亮度而不是 `isSystemInDarkTheme()`：应用内可以强制浅色/深色，
 * 跟系统设置判断会错开 —— 用户在浅色系统里选了深色主题，标记就成了一团看不见的浅紫。
 */
@Composable
fun habitMarkColor(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) HabitMarkDark else HabitMarkLight

/** 表单里给日程/事项/习惯/纪念日挑颜色用的固定色板。 */
val SwatchPalette = listOf(
    0xFF2E8B7A.toInt(),
    0xFF4A6FA5.toInt(),
    0xFFD9534F.toInt(),
    0xFFE0A52B.toInt(),
    0xFF9B6BC4.toInt(),
    0xFF5C6B73.toInt(),
)

private val LightColors = lightColorScheme(
    primary = Seed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8EDE2),
    onPrimaryContainer = Color(0xFF00382E),
    secondary = Accent,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCCB),
    onSecondaryContainer = Color(0xFF3D1B0C),
    // 训练色占 tertiary 这一路：月视图角标、打卡圆点、计划条都从它取色。
    // M3 默认的 tertiary 是紫色，和这套青绿+珊瑚的配色没有半点关系，必须显式覆盖。
    tertiary = TrainingLight,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFCFE4F0),
    onTertiaryContainer = Color(0xFF06303F),
    surface = Color(0xFFFBFDFC),
    onSurface = Color(0xFF191C1B),
    surfaceVariant = Color(0xFFDCE5E1),
    onSurfaceVariant = Color(0xFF404947),
    background = Color(0xFFF6F9F8),
    onBackground = Color(0xFF191C1B),
    outline = Color(0xFF707977),
)

private val DarkColors = darkColorScheme(
    primary = SeedDark,
    onPrimary = Color(0xFF00382E),
    primaryContainer = Color(0xFF00513F),
    onPrimaryContainer = Color(0xFF7FD3BE),
    secondary = Color(0xFFFFB59A),
    onSecondary = Color(0xFF3D1B0C),
    tertiary = TrainingDark,
    onTertiary = Color(0xFF06303F),
    tertiaryContainer = Color(0xFF1B4A5F),
    onTertiaryContainer = Color(0xFFCFE4F0),
    surface = Color(0xFF191C1B),
    onSurface = Color(0xFFE1E8E5),
    surfaceVariant = Color(0xFF3F4946),
    onSurfaceVariant = Color(0xFFBFC9C6),
    background = Color(0xFF111413),
    onBackground = Color(0xFFE1E8E5),
)

private val PaopaoTypography = Typography()

@Composable
fun PaopaoTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = PaopaoTypography,
        content = content,
    )
}
