package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 实际 Shape 的内缩镜面轮廓；轮廓、渐变和 Stroke 只在尺寸或参数改变时重建。 */
fun Modifier.liquidGlassRim(
    shape: Shape = PillShape,
    strokeWidth: Dp = 1.dp,
    dispersion: Float = 0.08f,
    specular: Float = 0.30f,
): Modifier = this.drawWithCache {
    val widthPx = strokeWidth.toPx().coerceAtLeast(0f)
    // Stroke 以路径为中心。半线宽再加半像素，给外侧抗锯齿留足空间。
    val inset = widthPx / 2f + 0.5f
    val innerSize = Size(
        (size.width - inset * 2f).coerceAtLeast(0f),
        (size.height - inset * 2f).coerceAtLeast(0f),
    )
    if (widthPx <= 0f || innerSize.width <= 0f || innerSize.height <= 0f) {
        onDrawWithContent { drawContent() }
    } else {
        // PillShape 使用百分比圆角；直接取得实际轮廓，不用固定 24dp 近似。
        val outline = shape.createOutline(innerSize, layoutDirection, this)
        val stroke = Stroke(width = widthPx)
        val highlight = specular.coerceIn(0f, 1f)
        val chroma = dispersion.coerceIn(0f, 1f)
        val specularBrush = Brush.verticalGradient(
            0.00f to Color.White.copy(alpha = highlight),
            0.28f to Color.White.copy(alpha = highlight * 0.35f),
            0.55f to Color.Transparent,
            0.86f to Color.White.copy(alpha = highlight * 0.16f),
            1.00f to Color.White.copy(alpha = highlight * 0.30f),
            endY = innerSize.height,
        )
        val dispersionBrush = Brush.linearGradient(
            0.00f to Color(0xFFFF8A5C).copy(alpha = chroma),
            0.50f to Color.Transparent,
            1.00f to Color(0xFF6FC3FF).copy(alpha = chroma),
            start = Offset.Zero,
            end = Offset(innerSize.width, innerSize.height),
        )
        onDrawWithContent {
            drawContent()
            translate(inset, inset) {
                drawOutline(outline, brush = specularBrush, style = stroke)
                if (chroma > 0f) {
                    drawOutline(outline, brush = dispersionBrush, style = stroke)
                }
            }
        }
    }
}

/** 暗色仅保留微弱反光，避免底栏出现亮白外框。 */
fun glassRimIntensity(ultra: Boolean, dark: Boolean = false): Pair<Float, Float> =
    when {
        dark && ultra -> 0.04f to 0.12f
        dark -> 0.02f to 0.06f
        ultra -> 0.16f to 0.42f
        else -> 0.08f to 0.30f
    }
