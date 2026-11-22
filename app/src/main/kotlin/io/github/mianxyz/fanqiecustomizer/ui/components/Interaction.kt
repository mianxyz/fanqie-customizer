package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

/**
 * 按压缩放反馈。
 *
 * 指甲按下时缩到 [pressedScale]，松开后由低阻尼弹簧回弹——
 * 这是「物理感」的来源：不是线性补间，而是有轻微过冲的弹簧。
 *
 * 用 composed + interactionSource，所以 Row / Card 只要挂上这个 Modifier
 * 就能获得一致的触感，不需要每处各写一遍手势。
 */
fun Modifier.pressScale(
    pressedScale: Float = 0.97f,
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "pressScale",
    )
    this.scale(scale)
}

/** 记住一个只在按压反馈中使用的 interactionSource。 */
@Composable
fun rememberPressSource(): MutableInteractionSource = remember { MutableInteractionSource() }
