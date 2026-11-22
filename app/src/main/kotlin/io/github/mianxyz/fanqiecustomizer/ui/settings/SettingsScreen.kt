package io.github.mianxyz.fanqiecustomizer.ui.settings

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.mianxyz.fanqiecustomizer.ConfigBridge
import io.github.mianxyz.fanqiecustomizer.ui.components.AppreciationDialog
import io.github.mianxyz.fanqiecustomizer.ui.components.FloatingPillNavBar
import io.github.mianxyz.fanqiecustomizer.ui.components.AmbientBackdrop
import io.github.mianxyz.fanqiecustomizer.ui.components.LocalEffectsPaused
import io.github.mianxyz.fanqiecustomizer.ui.components.NavItem
import io.github.mianxyz.fanqiecustomizer.ui.icons.AppIcons
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel
import kotlinx.coroutines.CancellationException

private val NAV = listOf(
    NavItem("仪表盘", AppIcons.Dashboard),
    NavItem("功能", AppIcons.Tune),
    NavItem("主题", AppIcons.Palette),
    NavItem("关于", AppIcons.Info),
)

@Composable
fun SettingsShell(
    bridge: ConfigBridge,
    glassLevel: GlassLevel,
    synced: Boolean,
    statusText: String,
    launcherHidden: Boolean,
    onLauncherHiddenChange: (Boolean) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onStartupTab: (String?) -> Unit,
    onGlassChange: (GlassLevel) -> Unit,
    onStatusClick: () -> Unit,
    onOpenTarget: () -> Unit,
    onLogGuide: () -> Unit,
    onResetAll: () -> Unit,
    onControlCatalog: () -> Unit,
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAppreciation by rememberSaveable { mutableStateOf(false) }
    val visited = remember { mutableStateListOf(page) }
    // Own every overview offset outside the detail AnimatedContent disposal boundary.
    val tabListStates = listOf(
        rememberLazyListState(), rememberLazyListState(),
        rememberLazyListState(), rememberLazyListState(),
    )
    val hazeState = rememberHazeState()
    val tabs = updateTransition(page, label = "tabs")
    val details = updateTransition(detailId, label = "details")
    // target 切换发生在 isRunning 第一次置位之前；首帧也必须停采样。
    val moving = page != tabs.currentState || detailId != details.currentState ||
        tabs.isRunning || details.isRunning
    var navMotion by remember { mutableStateOf(false) }
    // 注意：这里刻意不再维护 effectsPaused / settlingEffects。
    // 旧版把它们喂给 LiquidGlass，转场时整层停画 + 采样切空，
    // 表现为底栏在切换瞬间变透明、闪烁。现在玻璃层始终绘制。
    val scheme = MaterialTheme.colorScheme
    val closeDetail: () -> Unit = { detailId = null }

    // 已访问的四个一级页保留组合/列表状态；避免暖切页时整页重新组合与测量。
    LaunchedEffect(page) {
        if (page !in visited) visited.add(page)
    }
    PredictiveBackHandler(enabled = detailId != null) { progress ->
        try {
            progress.collect { }
            closeDetail()
        } catch (_: CancellationException) {
            // 取消系统返回预览时保留当前页。
        }
    }

    CompositionLocalProvider(
        // 只用于降级状态徽标的旋转动画；玻璃层不再读它。
        LocalEffectsPaused provides moving,
    ) {
        Box(Modifier.fillMaxSize().background(scheme.surfaceContainer)) {
            // 环境微光必须画在 windowInsetsPadding 之外：
            // 放进内层会让它被状态栏 inset 切掉，状态栏区域只剩纯色底，
            // 表现就是「顶部有一条白带」。
            AmbientBackdrop(
                glassLevel = glassLevel,
                modifier = Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                Box(
                    Modifier.fillMaxSize()
                        .pointerInput(moving) {
                            if (moving) awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                                }
                            }
                        }
                        .then(if (glassLevel != GlassLevel.None) Modifier.hazeSource(hazeState) else Modifier)
                        .background(scheme.surfaceContainer),
                ) {
                    details.AnimatedContent(
                        modifier = Modifier.fillMaxSize(),
                        // 一级页 key 固定为 null；只有进入/离开详情时才销毁该分支。
                        transitionSpec = { EnterTransition.None.togetherWith(ExitTransition.None).using(null) },
                    ) { categoryId ->
                        val enteringDetail = details.segment.targetState != null
                        val slide by transition.animateFloat(
                            transitionSpec = { tween(240, easing = FastOutSlowInEasing) },
                            label = "detailTranslation",
                        ) { phase ->
                            when (phase) {
                                EnterExitState.PreEnter -> if (enteringDetail) 1f else -0.5f
                                EnterExitState.Visible -> 0f
                                EnterExitState.PostExit -> if (enteringDetail) -0.5f else 1f
                            }
                        }
                        val opacity by transition.animateFloat(
                            transitionSpec = { tween(180) }, label = "detailOpacity",
                        ) { if (it == EnterExitState.Visible) 1f else 0f }
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                            translationX = size.width * 0.14f * slide
                            alpha = opacity
                        }) {
                            val category = categoryId?.let(Catalog::category)
                            if (category != null) {
                                FeatureDetailScreen(category, bridge, glassLevel, hazeState, onToggle, closeDetail)
                            } else {
                                // 只有当前页与正在退出的页有非零 alpha；其余页不绘制、不接收语义。
                                val retained = (visited + page).distinct()
                                retained.forEach { index ->
                                    key(index) {
                                        val visible = index == page
                                        val alpha = tabs.animateFloat(
                                            transitionSpec = { tween(180) }, label = "tabOpacity",
                                        ) { if (it == index) 1f else 0f }
                                        val offset = tabs.animateFloat(
                                            transitionSpec = { tween(240, easing = FastOutSlowInEasing) },
                                            label = "tabTranslation",
                                        ) { current ->
                                            when {
                                                current == index -> 0f
                                                current < index -> 1f
                                                else -> -0.5f
                                            }
                                        }
                                        run {
                                            Box(
                                                Modifier.fillMaxSize()
                                                    .layout { measurable, constraints ->
                                                        val measured = measurable.measure(constraints)
                                                        layout(measured.width, measured.height) {
                                                            if (index == tabs.currentState || index == tabs.targetState) {
                                                                measured.place(0, 0)
                                                            }
                                                        }
                                                    }
                                                    .zIndex(if (visible) 1f else 0f)
                                                    .then(if (!visible) Modifier.clearAndSetSemantics { } else Modifier)
                                                    .graphicsLayer {
                                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                                        translationX = size.width * 0.14f * offset.value
                                                        this.alpha = alpha.value
                                                    },
                                            ) {
                                                TabPageContent(
                                                    index, tabListStates[index], bridge, glassLevel, hazeState, synced, statusText,
                                                    launcherHidden, onLauncherHiddenChange, onToggle, onStartupTab,
                                                    onGlassChange, onStatusClick, onOpenTarget, onLogGuide, onResetAll,
                                                    onControlCatalog,
                                                    { detailId = it }, { showAppreciation = true },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                AnimatedVisibility(
                    visible = detailId == null,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(140)),
                    modifier = Modifier.align(Alignment.BottomCenter),
                ) {
                    FloatingPillNavBar(
                        items = NAV,
                        selectedIndex = page,
                        onSelect = { if (it != page) { navMotion = true; page = it } },
                        onMotionChanged = { navMotion = it },
                        glassLevel = glassLevel,
                        hazeState = hazeState,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    )
                }
            }
        }
    }
    if (showAppreciation) AppreciationDialog { showAppreciation = false }
}

/** Stable parameter boundary: animation state is deliberately owned by the outer page layer. */
@Composable
private fun TabPageContent(
    index: Int,
    listState: LazyListState,
    bridge: ConfigBridge,
    glassLevel: GlassLevel,
    hazeState: dev.chrisbanes.haze.HazeState,
    synced: Boolean,
    statusText: String,
    launcherHidden: Boolean,
    onLauncherHiddenChange: (Boolean) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onStartupTab: (String?) -> Unit,
    onGlassChange: (GlassLevel) -> Unit,
    onStatusClick: () -> Unit,
    onOpenTarget: () -> Unit,
    onLogGuide: () -> Unit,
    onResetAll: () -> Unit,
    onControlCatalog: () -> Unit,
    onOpenCategory: (String) -> Unit,
    onAppreciate: () -> Unit,
) {
    val spec = Catalog.pages[index]
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 108.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        item { PageHeader(spec) }
        when (index) {
            0 -> DashboardPage(
                spec, bridge, glassLevel, hazeState, synced, statusText,
                launcherHidden, onLauncherHiddenChange, onToggle,
                onStartupTab, onStatusClick, onAppreciate,
            )
            1 -> FeaturePage(bridge, glassLevel, hazeState, onOpenCategory)
            2 -> ThemePage(spec, bridge, glassLevel, hazeState, onToggle, onGlassChange)
            else -> AboutPage(
                spec, bridge, glassLevel, hazeState, onToggle,
                onOpenTarget, onLogGuide, onResetAll, onStatusClick, onControlCatalog,
            )
        }
    }
}

@Composable
private fun PageHeader(spec: PageSpec) {
    Column(Modifier.fillMaxWidth().padding(top = 22.dp, bottom = 18.dp)) {
        Text(spec.headline, style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.onSurface)
        if (spec.subhead.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(spec.subhead, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}