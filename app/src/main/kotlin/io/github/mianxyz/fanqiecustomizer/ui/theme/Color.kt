package io.github.mianxyz.fanqiecustomizer.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * 配色来源：本地 Kole Jain 设计库（D:\FQMK\Kole Jain）。
 *
 * 从 Mobile App UI 的实图取样得到这套中性色骨架：
 *   底色 #1b1b1b（近黑，不是纯黑）  次级面 #2b2b2b  极细线 #2c2c2c
 *   正文 #ededed  次级文字 #9a9a9a  强调 #7f6f4b（低饱和琥珀）
 *
 * 三个刻意的决定：
 *
 * 1. **底色用 #1b1b1b 而不是 #000000**。纯黑在 OLED 上会让边界「消失」，
 *    卡片和背景糊成一片；近黑留出可辨识的层级差。
 * 2. **强调色是琥珀而不是番茄橙**。参考图里强调只出现在「时长/状态」这类
 *    信息高亮上，饱和度很低（#7f6f4b）。原来那个 #EF5B33 太抢眼，
 *    在密集列表里会变成一排红点。这里降到琥珀，并且只在真正需要
 *    提示的地方出现。
 * 3. **浅色主题不是简单反色**，而是同一套层级关系的明度镜像：
 *    纸白底 + 略深的面 + 同样的琥珀强调。
 */

// ── 深色（默认，贴合参考图）─────────────────────────────
private val InkBg = Color(0xFF1B1B1B)        // 页面底
private val InkCard = Color(0xFF212121)      // 卡片（比底色亮一档）
private val InkHigh = Color(0xFF2B2B2B)      // 更高的面：输入框/胶囊底
private val InkHairline = Color(0xFF2F2F2F)  // 1px 分隔线
private val InkText = Color(0xFFEDEDED)
private val InkTextDim = Color(0xFF9A9A9A)
private val Amber = Color(0xFFD9A441)        // 琥珀强调（提亮版，用于文字/图标）
private val AmberContainer = Color(0xFF3A3020)
private val AmberOn = Color(0xFF1B1B1B)

// ── 浅色（同一套层级语义）───────────────────────────────
private val PaperBg = Color(0xFFF7F6F4)
private val PaperCard = Color(0xFFFFFFFF)
private val PaperHigh = Color(0xFFEFEEEB)
private val PaperHairline = Color(0xFFE4E2DE)
private val PaperText = Color(0xFF1B1B1B)
private val PaperTextDim = Color(0xFF6E6E6E)
private val AmberDeep = Color(0xFF8A6D2F)    // 浅色下需要更深的琥珀才够对比

val DarkColors = darkColorScheme(
    primary = Amber,
    onPrimary = AmberOn,
    primaryContainer = AmberContainer,
    onPrimaryContainer = Color(0xFFF2D9A8),

    background = InkBg,
    onBackground = InkText,

    // 层级：背景 → 卡片 → 更高的面，逐级提亮
    surface = InkBg,
    surfaceContainerLowest = InkCard,
    surfaceContainerLow = InkBg,
    surfaceContainer = InkBg,
    surfaceContainerHigh = InkHigh,
    surfaceContainerHighest = Color(0xFF343434),
    surfaceVariant = InkHigh,

    onSurface = InkText,
    onSurfaceVariant = InkTextDim,

    outline = Color(0xFF454545),
    outlineVariant = InkHairline,

    secondaryContainer = Color(0xFF262626),
    onSecondaryContainer = Color(0xFFCFCFCF),

    error = Color(0xFFE5695B),
    onError = Color(0xFF2A0C08),
)

val LightColors = lightColorScheme(
    primary = AmberDeep,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF3E4C4),
    onPrimaryContainer = Color(0xFF2B2007),

    background = PaperBg,
    onBackground = PaperText,

    surface = PaperBg,
    surfaceContainerLowest = PaperCard,
    surfaceContainerLow = PaperBg,
    surfaceContainer = PaperBg,
    surfaceContainerHigh = PaperHigh,
    surfaceContainerHighest = Color(0xFFE7E5E1),
    surfaceVariant = PaperHigh,

    onSurface = PaperText,
    onSurfaceVariant = PaperTextDim,

    outline = Color(0xFFC4C1BB),
    outlineVariant = PaperHairline,

    secondaryContainer = Color(0xFFEDEBE7),
    onSecondaryContainer = Color(0xFF3A3A38),

    error = Color(0xFFB3261E),
    onError = Color.White,
)
