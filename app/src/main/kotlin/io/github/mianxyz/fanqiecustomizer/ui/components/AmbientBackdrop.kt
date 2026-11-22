package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel

/**
 * 环境微光底纹（Ambient Soft Gradient）。
 *
 * 为什么必须有它：实时高斯模糊在纯单色底上采样，输出的仍是同一个颜色，
 * 肉眼完全看不出「磨砂」。之前 253→243 几乎无感的根因就在这里——
 * 不是模糊没生效，而是底是死的单色，模糊无从体现。
 *
 * 这里铺几团低饱和、大半径的柔和色块（浅色暖米/淡橙，深色炭黑/微暖暗金），
 * 底栏与卡片采样时就能在人眼可辨的层次上做折射，磨砂感才真正成立。
 *
 * 只在玻璃档位非 None 时绘制，且用 Canvas 一次性画完，不参与重组。
 */
@Composable
fun AmbientBackdrop(
    glassLevel: GlassLevel,
    modifier: Modifier = Modifier,
) {
    if (glassLevel == GlassLevel.None) return
    val dark = isSystemInDarkTheme()
    val ultra = glassLevel == GlassLevel.Ultra

    // 极致档把光晕提得更明显，标准档保持很克制。
    val strength = if (ultra) 1f else 0.55f

    // 浅色：暖米 + 极淡橙 + 冷灰蓝，形成冷暖交错，模糊后有明显色相层次。
    // 深色：深炭 + 微暖暗金 + 冷蓝，避免纯黑糊成一片。
    val c1 = if (dark) Color(0xFF3A3020) else Color(0xFFFFF1DD)
    val c2 = if (dark) Color(0xFF2A2620) else Color(0xFFFFE4C4)
    val c3 = if (dark) Color(0xFF1E2430) else Color(0xFFE8EEF7)

    Canvas(modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // 三团径向光晕，半径取屏幕长边的 0.7~1.0，保证过渡极缓。
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    c1.copy(alpha = 0.85f * strength),
                    Color.Transparent,
                ),
                center = Offset(w * 0.18f, h * 0.12f),
                radius = w * 0.95f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    c2.copy(alpha = 0.70f * strength),
                    Color.Transparent,
                ),
                center = Offset(w * 0.92f, h * 0.42f),
                radius = w * 0.85f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    c3.copy(alpha = 0.60f * strength),
                    Color.Transparent,
                ),
                center = Offset(w * 0.45f, h * 0.98f),
                radius = w * 1.0f,
            ),
        )
    }
}
