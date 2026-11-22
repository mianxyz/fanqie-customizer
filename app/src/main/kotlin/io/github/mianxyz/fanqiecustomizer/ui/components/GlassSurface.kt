package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel

/**
 * 转场期间允许调用方降级「非玻璃」动画（例如状态徽标的旋转/波形进度条）。
 *
 * 注意：它只影响动画降级，**不再影响任何玻璃层**。
 * 旧版把它喂给 liquidGlass，导致底栏在切页瞬间整层停画而变透明。
 */
val LocalEffectsPaused = compositionLocalOf { false }

/**
 * 三档液态玻璃的实心卡片材质。
 *
 * 内容卡片不建立 Haze 采样层（采样层只给底栏与环境底纹），
 * 这样长列表滚动时不会为每张卡片各跑一遍模糊。
 */
@Composable
fun GlassSurface(
    glassLevel: GlassLevel,
    @Suppress("UNUSED_PARAMETER") hazeState: HazeState?,
    shape: Shape,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    // 标准/极致都要求明显磨砂通透，因此半透明度比旧版更低（更透）。
    val alpha = when (glassLevel) {
        GlassLevel.None -> 1f
        GlassLevel.Standard -> 0.50f
        GlassLevel.Ultra -> 0.28f
    }
    var surface = modifier
        .clip(shape)
        .background(scheme.surfaceContainerLowest.copy(alpha = alpha), shape)

    if (highlight && glassLevel != GlassLevel.None) {
        surface = surface.border(
            width = if (glassLevel == GlassLevel.Ultra) 1.5.dp else 1.dp,
            color = if (glassLevel == GlassLevel.Ultra)
                scheme.onSurface.copy(alpha = 0.22f)
            else
                scheme.outlineVariant.copy(alpha = 0.55f),
            shape = shape,
        )
    }
    Box(modifier = surface, content = content)
}

/**
 * 导航层 / 底栏所用的实时液态玻璃。
 *
 * 关键修正（切换变透明的根因）：
 * 旧版在转场、拖拽、点击切页时把 paused 传进来，用
 * drawWithContent 直接停画整层，并把 HazeInput 换成 fallback = null，
 * 于是底栏在动起来的那一刻背景与模糊一起消失，看起来就是变透明。
 *
 * 现在三条全部移除：1) 不再有暂停开关，玻璃层永远画；
 * 2) 采样输入恒为 hazeState；3) blurRadius 恒定，不做转场降档。
 * 结果：静止、拖拽、点按切换、页面转场全过程中底栏完全一致。
 */
@Composable
fun liquidGlass(
    glassLevel: GlassLevel,
    hazeState: HazeState?,
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val glassOn = glassLevel != GlassLevel.None
    val ultra = glassLevel == GlassLevel.Ultra

    val scrimAlpha = when (glassLevel) {
        GlassLevel.None -> 1f
        GlassLevel.Standard -> 0.50f
        GlassLevel.Ultra -> 0.28f
    }
    val scrim = scheme.surfaceContainerLowest.copy(alpha = scrimAlpha)

    var container = modifier.clip(shape).background(scrim, shape)
    if (glassOn) {
        val (dispersion, specular) = glassRimIntensity(ultra, dark)
        container = container.liquidGlassRim(
            shape = shape,
            strokeWidth = if (ultra) 1.5.dp else 1.dp,
            dispersion = dispersion,
            specular = specular,
        )
    }

    Box(modifier = container) {
        if (glassOn && hazeState != null) {
            val radius = if (ultra) 36.dp else 20.dp
            val noise = if (ultra) 0.04f else 0.02f
            val style = remember(ultra, radius, noise, scrim) {
                HazeBlurStyle {
                    blurRadius(radius)
                    noiseFactor(noise)
                    backgroundColor(scrim)
                    colorEffects(listOf(HazeColorEffect.tint(scrim)))
                    fallbackColorEffect(HazeColorEffect.tint(scrim))
                }
            }
            val input = remember(hazeState) { HazeInput.Backdrop(hazeState) }
            Box(
                Modifier
                    .matchParentSize()
                    .hazeBlur(input = input, style = style),
            )
        }
        content()
    }
}

/** 供调用点区分的语义别名。 */
@Composable
fun ContentSurface(
    glassLevel: GlassLevel,
    shape: Shape,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) = GlassSurface(
    glassLevel = glassLevel,
    hazeState = null,
    shape = shape,
    modifier = modifier,
    highlight = highlight,
    content = content,
)
