package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import dev.chrisbanes.haze.HazeState
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel
import kotlin.math.abs
import kotlin.math.roundToInt

/** Main-thread request ownership: old animation cleanup must not finish newer input. */
internal class NavigationMotionOwner {
    private var epoch = 0L
    fun next(): Long = ++epoch
    fun owns(request: Long): Boolean = epoch == request
}

data class NavItem(
    val label: String,
    val icon: ImageVector,
)

/**
 * 悬浮胶囊导航栏（可拖拽的液态玻璃底栏）。
 *
 * 三条行为约束，对应「底栏不可拖动 / 切换零帧生硬」两个问题：
 *
 * 1. **指示器不瞬移**。选中胶囊是一个独立浮层，位置由 [Animatable] 驱动。
 *    点击换页时只改目标值，胶囊用低阻尼弹簧滑行过去。
 * 2. **可拖拽**。横向拖动时胶囊实时跟随手指，并按「离最近整格的偏移量」
 *    轻微拉伸；松手后吸附到位移最近的 tab 并通知外层切页。
 * 3. **拖拽途中图标就近点亮**。前景色按胶囊实时位置在相邻两个 tab 之间插值，
 *    所以拖到一半时两个图标都是半亮，不会出现「拖动时高亮不动」的断裂感。
 *
 * 手势分工：整条底栏的 pointerInput 只负责「横向拖拽」，
 * 每个 tab 槽位各自挂 clickable 负责「点击」。
 * 两者挂在不同层级上，由手势系统按移动阈值裁决——
 * 早先的写法是把 detectTapGestures 叠在拖拽层之上，两层抢同一串指针事件，
 * 结果拖拽识别器先消费了 down 事件，点击被吞掉（点底栏没反应）。
 */
@Composable
fun FloatingPillNavBar(
    items: List<NavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    glassLevel: GlassLevel,
    hazeState: HazeState?,
    modifier: Modifier = Modifier,
    onMotionChanged: (Boolean) -> Unit = {},
) {
    if (items.isEmpty()) return

    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val hasGlass = glassLevel != GlassLevel.None

    val currentSelectedIndex by rememberUpdatedState(selectedIndex.coerceIn(items.indices))
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentOnMotionChanged by rememberUpdatedState(onMotionChanged)
    val indicatorPos = remember { Animatable(currentSelectedIndex.toFloat()) }
    val motionOwner = remember { NavigationMotionOwner() }
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(indicatorPos.value) }
    var releasedPosition by remember { mutableStateOf<Float?>(null) }
    var settleRequest by remember { mutableIntStateOf(0) }
    var dragStretch by remember { mutableFloatStateOf(0f) }

    // 位置只在各槽位绘制/派生阈值中读取，拖动每帧不再使父底栏重组。
    val foregroundPosition = remember {
        { if (dragging) dragPosition else currentSelectedIndex.toFloat() }
    }

    val settle = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    // 生命周期只发布动画起止，不把逐帧位置信息回传至页面组合。
    DisposableEffect(Unit) {
        onDispose { motionOwner.next(); currentOnMotionChanged(false) }
    }
    LaunchedEffect(selectedIndex, items.size, dragging, settleRequest) {
        val request = motionOwner.next()
        if (dragging) {
            currentOnMotionChanged(true)
            indicatorPos.stop()
        } else {
            val start = releasedPosition
            if (start != null || indicatorPos.value != currentSelectedIndex.toFloat()) {
                currentOnMotionChanged(true)
                try {
                    start?.let {
                        indicatorPos.snapTo(it)
                        releasedPosition = null
                    }
                    indicatorPos.animateTo(currentSelectedIndex.toFloat(), settle)
                } finally {
                    if (motionOwner.owns(request)) currentOnMotionChanged(false)
                }
            } else if (motionOwner.owns(request)) currentOnMotionChanged(false)
        }
    }

    // 导航层使用 Liquid Glass（HIG 明确允许的唯一位置）。
    liquidGlass(
        glassLevel = glassLevel,
        hazeState = hazeState,
        shape = PillShape,
        modifier = modifier
            .fillMaxWidth()
            .height(62.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            val count = items.size
            val slotWidth = maxWidth / count
            val slotPx = with(density) { slotWidth.toPx() }

            // 固定测量尺寸；每帧的位移和拉伸仅更新图层，不触发整条栏重组/重排。
            val indicatorWidth = slotWidth * 0.74f
            val slotCenterPx = with(density) { ((slotWidth - indicatorWidth) / 2f).toPx() }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(count, slotPx) {
                        // 父层识别横向拖拽；槽位 clickable 负责点击。
                        // 回调从 rememberUpdatedState 读取，手势闭包不持有旧选中项。
                        detectHorizontalDragGestures(
                            onDragStart = {
                                motionOwner.next()
                                dragPosition = (releasedPosition ?: indicatorPos.value)
                                    .coerceIn(0f, (count - 1).toFloat())
                                releasedPosition = null
                                dragging = true
                                currentOnMotionChanged(true)
                                dragStretch = 0f
                            },
                            onDragEnd = {
                                motionOwner.next()
                                dragStretch = 0f
                                val target = dragPosition.roundToInt().coerceIn(0, count - 1)
                                releasedPosition = dragPosition
                                dragging = false
                                settleRequest++
                                if (target != currentSelectedIndex) currentOnSelect(target)
                            },
                            onDragCancel = {
                                motionOwner.next()
                                dragStretch = 0f
                                releasedPosition = dragPosition
                                dragging = false
                                settleRequest++
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                if (slotPx > 0f) {
                                    val next = (dragPosition + dragAmount / slotPx)
                                        .coerceIn(0f, (count - 1).toFloat())
                                    dragPosition = next
                                    val frac = abs(next - next.roundToInt()).coerceIn(0f, 0.5f)
                                    dragStretch = frac * 2f
                                }
                            },
                        )
                    },
            ) {
                // ── 选中指示器 ──
                Box(
                    modifier = Modifier
                        .width(indicatorWidth)
                        .height(48.dp)
                        .graphicsLayer {
                            val position = if (dragging || releasedPosition != null) {
                                dragPosition
                            } else {
                                indicatorPos.value
                            }
                            translationX = slotPx * position + slotCenterPx
                            translationY = 8.dp.toPx()
                            scaleX = 1f + dragStretch * 0.10f
                        }
                        .clip(PillShape)
                        .then(
                            if (hasGlass) {
                                Modifier
                                    .background(
                                        // 玻璃档：胶囊只是「淡淡染上一点品牌色」，
                                        // 真正的层次交给模糊与镜面描边。
                                        // 0.52 会变成一块实心琥珀，盖过底下的内容——
                                        // HIG 对选中态的要求是安静着色，不是品牌色填充。
                                        scheme.primary.copy(
                                            alpha = if (glassLevel == GlassLevel.Ultra) 0.24f else 0.16f
                                        ),
                                        PillShape,
                                    )
                            } else {
                                // 扁平档：选中态用「很淡的品牌色 + 品牌色文字」，
                                // 而不是整块实心 primaryContainer —— 参考图里
                                // 选中项只是略微提亮，实心块会盖过内容。
                                Modifier.background(
                                    scheme.primary.copy(alpha = 0.14f),
                                    PillShape,
                                )
                            }
                        ),
                )

                // ── 图标行 ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    items.forEachIndexed { index, item ->
                        NavigationSlot(
                            item = item,
                            index = index,
                            selected = index == currentSelectedIndex,
                            slotWidth = slotWidth,
                            position = foregroundPosition,
                            normalColor = scheme.onSurfaceVariant,
                            activeColor = scheme.primary,
                            onSelect = {
                                if (index != currentSelectedIndex) {
                                    motionOwner.next()
                                    currentOnMotionChanged(true)
                                    currentOnSelect(index)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/** Per-slot composition only observes label-threshold changes; continuous tint is draw-phase state. */
@Composable
private fun NavigationSlot(
    item: NavItem,
    index: Int,
    selected: Boolean,
    slotWidth: Dp,
    position: () -> Float,
    normalColor: Color,
    activeColor: Color,
    onSelect: () -> Unit,
) {
    val showLabel by remember(index, position) {
        derivedStateOf(structuralEqualityPolicy()) {
            (1f - abs(position() - index)).coerceIn(0f, 1f) > 0.7f
        }
    }
    val labelAlpha = animateFloatAsState(if (showLabel) 1f else 0f, label = "navLabel")
    var labelWidthPx by remember { mutableFloatStateOf(0f) }
    val color = remember(index, position, normalColor, activeColor) {
        ColorProducer {
            lerp(normalColor, activeColor, (1f - abs(position() - index)).coerceIn(0f, 1f))
        }
    }
    val painter = rememberVectorPainter(item.icon)
    Box(
        modifier = Modifier.width(slotWidth).fillMaxHeight().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onSelect,
        ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter, color, item.label,
            Modifier.size(20.dp).graphicsLayer {
                translationX = -(7.dp.toPx() + labelWidthPx) * labelAlpha.value / 2f
            },
        )
        Text(
            item.label, color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .onSizeChanged { labelWidthPx = it.width.toFloat() }
                .then(if (!selected) Modifier.clearAndSetSemantics { } else Modifier)
                .graphicsLayer {
                    val extra = 7.dp.toPx() + labelWidthPx
                    translationX = 10.dp.toPx() + 7.dp.toPx() + labelWidthPx / 2f - extra * labelAlpha.value / 2f
                    alpha = labelAlpha.value
                },
        )
    }
}