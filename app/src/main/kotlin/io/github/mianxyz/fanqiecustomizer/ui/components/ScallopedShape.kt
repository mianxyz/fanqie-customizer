package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 花瓣波浪圆（Scalloped / Cookie Shape）。
 *
 * Material 3 Expressive 的异形裁剪，用于首页核心状态徽章：
 * 圆周不是直线段，而是 [lobes] 个外凸花瓣，形成「饼干」边缘。
 *
 * 采样密度固定（每瓣 [SAMPLES_PER_LOBE] 点），因此线段数不随控件尺寸增长，
 * 大尺寸下仍然是常数级开销。
 */
class ScallopedShape(
    private val lobes: Int = 12,
    /** 花瓣深度占半径的比例；0.085 约为可见但不夸张的幅度。 */
    private val depth: Float = 0.085f,
) : Shape {

    private companion object {
        const val SAMPLES_PER_LOBE = 14
    }

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = min(size.width, size.height) / 2f
        if (radius <= 0f) return Outline.Rectangle(Rect(0f, 0f, size.width, size.height))

        val cx = size.width / 2f
        val cy = size.height / 2f
        val inner = radius * (1f - depth)
        val path = Path()
        val steps = lobes * SAMPLES_PER_LOBE
        val dTheta = 2.0 * PI / steps
        // 相位偏移 -90°：让第一个花瓣朝上，轮廓在视觉上左右对称。
        val phase = -PI / 2.0

        for (i in 0 until steps) {
            val theta = phase + i * dTheta
            // 每瓣一个完整余弦周期 -> 圆周呈现 lobes 个外凸。
            val wave = 1f + depth * cos(lobes * theta).toFloat()
            val t = ((wave - (1f - depth)) / depth).coerceIn(0f, 1f)
            val r = inner + (radius - inner) * t
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** 药丸 / 胶囊圆角：全圆角，用于标签、分段切换器与悬浮导航栏。 */
val PillShape: Shape = RoundedCornerShape(percent = 50)

/** 分组卡片统一 24dp 超大圆角。 */
val CardShape: Shape = RoundedCornerShape(24.dp)

/** 卡片内部子块（开关行、动作行）的次级圆角。 */
val InnerShape: Shape = RoundedCornerShape(16.dp)
