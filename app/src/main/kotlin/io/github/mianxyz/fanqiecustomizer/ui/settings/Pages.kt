package io.github.mianxyz.fanqiecustomizer.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import io.github.mianxyz.fanqiecustomizer.ConfigBridge
import io.github.mianxyz.fanqiecustomizer.Rules
import io.github.mianxyz.fanqiecustomizer.ui.components.ActionRow
import io.github.mianxyz.fanqiecustomizer.ui.components.CategoryRow
import io.github.mianxyz.fanqiecustomizer.ui.components.DashboardActions
import io.github.mianxyz.fanqiecustomizer.ui.components.GroupCard
import io.github.mianxyz.fanqiecustomizer.ui.components.PillSegmented
import io.github.mianxyz.fanqiecustomizer.ui.components.PillTag
import io.github.mianxyz.fanqiecustomizer.ui.components.RowDivider
import io.github.mianxyz.fanqiecustomizer.ui.components.ScallopedStatusBadge
import io.github.mianxyz.fanqiecustomizer.ui.components.SyncStatusBlock
import io.github.mianxyz.fanqiecustomizer.ui.components.ToggleRow
import io.github.mianxyz.fanqiecustomizer.ui.icons.AppIcons
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel
import kotlin.math.roundToInt

// ─────────────────────────── 仪表盘 ───────────────────────────

fun LazyListScope.DashboardPage(
    spec: PageSpec,
    bridge: ConfigBridge,
    glass: GlassLevel,
    hazeState: HazeState,
    synced: Boolean,
    statusText: String,
    launcherHidden: Boolean,
    onLauncherHiddenChange: (Boolean) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onStartupTab: (String?) -> Unit,
    onStatusClick: () -> Unit,
    onAppreciate: () -> Unit,
) {
    item {
        val enabled = bridge.get("enabled")
        val enabledCount = Catalog.featureKeys.count { bridge.get(it) }
        val totalCount = Catalog.featureKeys.size
        GroupCard(
            title = "模块状态",
            glassLevel = glass,
            hazeState = hazeState,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScallopedStatusBadge(synced = synced, enabled = enabled, diameter = 104.dp)
                Spacer(Modifier.width(18.dp))
                Box(modifier = Modifier.weight(1f)) {
                    SyncStatusBlock(
                        synced = synced,
                        enabled = enabled,
                        statusText = statusText,
                        modifier = Modifier.clickable(onClick = onStatusClick),
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            RowDivider()
            Spacer(Modifier.height(12.dp))
            // 动态状态摘要，替代原先的设计说明文案。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "已启用 ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "$enabledCount",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = " / $totalCount 项定制",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                PillTag(
                    text = if (enabled) "定制生效中" else "定制已停用",
                    emphasized = enabled,
                )
            }
        }
    }

    item {
        DashboardActions(
            enabled = bridge.get("enabled"),
            glassLevel = glass,
            hazeState = hazeState,
            onEnabledChange = { onToggle("enabled", it) },
            onAppreciate = onAppreciate,
        )
        Spacer(Modifier.height(12.dp))
    }

    item {
        val g = spec.groups[1]
        GroupCard(
            title = g.title,
            description = g.description,
            glassLevel = glass,
            hazeState = hazeState,

        ) {
            StartupTabSelector(bridge = bridge, onStartupTab = onStartupTab)
        }
    }

    item {
        val g = spec.groups[2]
        GroupCard(
            title = g.title,
            description = g.description,
            glassLevel = glass,
            hazeState = hazeState,
        ) {
            ToggleRow(
                title = "隐藏桌面图标",
                description = null,
                checked = launcherHidden,
                onCheckedChange = onLauncherHiddenChange,
            )
        }
    }
}

/**
 * 默认启动页选择器：单选，且被隐藏的入口不允许设为默认。
 */
@Composable
private fun StartupTabSelector(
    bridge: ConfigBridge,
    onStartupTab: (String?) -> Unit,
) {
    val current = bridge.startupTab()
    val labels = listOf("自动") + Rules.TAB_LABELS.toList()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        labels.forEachIndexed { index, label ->
            val startKey = if (index == 0) null else ConfigBridge.STARTUP_KEYS[index - 1]
            val tabKey = if (index == 0) null else Rules.TAB_KEYS[index - 1]
            val blocked = tabKey != null && bridge.get(tabKey)
            val selected = if (index == 0) current == null else current == startKey

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !blocked) { onStartupTab(startKey) }
                    .padding(vertical = 11.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (blocked) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                )
                if (blocked) {
                    PillTag("已隐藏")
                } else if (selected) {
                    PillTag("默认", emphasized = true)
                }
            }
            if (index < labels.lastIndex) RowDivider()
        }
    }
}

// ─────────────────────────── 功能（一级：分类入口） ───────────────────────────

/**
 * 一级功能页：只渲染分组卡片，卡片内是分类入口行，不出现任何 Switch。
 */
fun LazyListScope.FeaturePage(
    bridge: ConfigBridge,
    glass: GlassLevel,
    hazeState: HazeState,
    onOpenCategory: (String) -> Unit,
) {
    Catalog.featureCards.forEach { card ->
        item {
            GroupCard(
                title = card.title,
                glassLevel = glass,
                hazeState = hazeState,
            ) {
                card.categories.forEachIndexed { i, cat ->
                    CategoryRow(
                        icon = cat.icon,
                        title = cat.title,
                        blurb = cat.blurb,
                        enabledCount = cat.toggles.count { bridge.get(it.key) },
                        totalCount = cat.toggles.size,
                        onClick = { onOpenCategory(cat.id) },
                        showDivider = i < card.categories.lastIndex,
                    )
                }
            }
        }
    }
}

// ─────────────────────────── 功能（二级：分类详情） ───────────────────────────

/**
 * 二级功能详情页：返回按钮 + 分类微衬线大标题 + 说明 + 开关列表 + note。
 */
@Composable
fun FeatureDetailScreen(
    category: CategorySpec,
    bridge: ConfigBridge,
    glass: GlassLevel,
    hazeState: HazeState,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enabledCount = category.toggles.count { bridge.get(it.key) }
    // 只渲染当前可见的滑块：悬浮参数在贴底模式下没有意义，隐藏掉更清楚。
    val visibleSliders = category.sliders
    val glassEnabled = bridge.get("glass_bottom_bar_floating") || bridge.get("glass_bottom_bar_docked")
    val floatingStyle = !bridge.get("glass_bottom_bar_docked") && bridge.getInt("glass_bar_style") != 1

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = 18.dp,
            end = 18.dp,
            top = 8.dp,
            bottom = 108.dp,
        ),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 16.dp)) {
                Row(
                    modifier = Modifier
                        .clickable(onClick = onBack)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = AppIcons.ArrowBack,
                        contentDescription = "返回",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(20.dp).height(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "功能",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = category.blurb,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                PillTag(
                    text = "$enabledCount / ${category.toggles.size} 已开启",
                    emphasized = enabledCount > 0,
                )
            }
        }

        item {
            GroupCard(
                title = category.title,
                glassLevel = glass,
                hazeState = hazeState,

            ) {
                category.toggles.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    ToggleRow(
                        title = t.title,
                        description = t.description,
                        checked = bridge.get(t.key),
                        enabled = if (t.enabledWhen == "glass_bar_enabled") glassEnabled else if (t.key == "hide_reader_live_popup") bridge.get("hide_reader_ads") else t.enabledWhen?.let { bridge.get(it) } ?: true,
                        onCheckedChange = { onToggle(t.key, it) },
                    )
                    if (t.key == "glass_global_rank_cover") RankImageActions(enabled = bridge.get("glass_global_enabled"))
                }
            }
        }

        if (visibleSliders.isNotEmpty()) {
            item {
                Spacer(Modifier.height(14.dp))
                GroupCard(
                    title = "底栏参数",
                    glassLevel = glass,
                    hazeState = hazeState,
                ) {
                    visibleSliders.forEachIndexed { i, s ->
                        if (i > 0) RowDivider()
                        SliderRow(
                            spec = s,
                            value = bridge.getInt(s.key),
                            onChange = { bridge.setInt(s.key, it) },
                            enabled = glassEnabled && (s.visibleWhen != "glass_bottom_bar_floating" || floatingStyle) && (s.key != "glass_bar_bounce" || (floatingStyle && bridge.getInt("glass_bar_style") == 2)),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 带数值气泡的滑块行。
 *
 * 拖动时用本地 state 立即反馈，松手才 commit 落盘并同步，
 * 避免每一帧都触发 SharedPreferences 提交与框架推送。
 */
@Composable
private fun SliderRow(
    spec: SliderSpec,
    value: Int,
    onChange: (Int) -> Unit,
    enabled: Boolean = true,
) {
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val shown = local.roundToInt()
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = spec.options.getOrNull(shown) ?: "$shown${spec.unit}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (spec.description != null) {
            Text(
                text = spec.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (spec.options.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                spec.options.forEachIndexed { index, label ->
                    androidx.compose.material3.FilterChip(selected = value == index, onClick = { onChange(index) }, enabled = enabled, label = { Text(label) }, modifier = Modifier.weight(1f))
                }
            }
        } else Slider(
            enabled = enabled,
            value = local,
            onValueChange = { local = it },
            onValueChangeFinished = { onChange(local.roundToInt()) },
            valueRange = spec.min.toFloat()..spec.max.toFloat(),
            steps = (spec.max - spec.min - 1).coerceAtLeast(0),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────── 主题 ───────────────────────────

fun LazyListScope.ThemePage(
    spec: PageSpec,
    bridge: ConfigBridge,
    glass: GlassLevel,
    hazeState: HazeState,
    onToggle: (String, Boolean) -> Unit,
    onGlassChange: (GlassLevel) -> Unit,
) {
    item {
        val g = spec.groups[0]
        GroupCard(
            title = g.title,
            description = g.description,
            glassLevel = glass,
            hazeState = hazeState,

        ) {
            PillSegmented(
                options = GlassLevel.entries.map { it.label },
                selectedIndex = glass.ordinal,
                onSelect = { onGlassChange(GlassLevel.entries[it]) },
                modifier = Modifier.padding(vertical = 8.dp),
            )

        }
    }

    spec.groups.drop(1).forEach { group ->
        item {
            GroupCard(
                title = group.title,
                description = group.description,
                glassLevel = glass,
                hazeState = hazeState,

            ) {
                group.toggles.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    ToggleRow(
                        title = t.title,
                        description = t.description,
                        checked = bridge.get(t.key),
                        onCheckedChange = { onToggle(t.key, it) },
                    )
                }
            }
        }
    }
}

// ─────────────────────────── 关于 ───────────────────────────

/**
 * 读取真实安装版本。
 *
 * 不用 ActivityThread.currentApplication()：那是隐藏 API，
 * 在部分 ROM 上会抛 NoSuchMethodError。改为从调用方传入 Context。
 */
private fun versionLabel(context: android.content.Context): String = try {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    info.versionName + " (" + info.longVersionCode + ")"
} catch (e: Throwable) {
    "未知"
}

fun LazyListScope.AboutPage(
    spec: PageSpec,
    bridge: ConfigBridge,
    glass: GlassLevel,
    hazeState: HazeState,
    onToggle: (String, Boolean) -> Unit,
    onOpenTarget: () -> Unit,
    onLogGuide: () -> Unit,
    onResetAll: () -> Unit,
    onStatusClick: () -> Unit,
    onControlCatalog: () -> Unit,
) {
    item {
        // LocalContext 只能在 composable 作用域里取，所以放在 item 内部。
        val context = androidx.compose.ui.platform.LocalContext.current
        val label = "当前版本 " + versionLabel(context)
        GroupCard(
            title = "版本信息",
            glassLevel = glass,
            hazeState = hazeState,
        ) {
            // 显示真实版本号：确认是否装上了新包，避免「功能没出现」被误判成代码问题。
            ActionRow(
                icon = AppIcons.Info,
                label = label,
                onClick = {},
            )
        }
    }

    item {
        GroupCard(
            title = "配置同步",
            glassLevel = glass,
            hazeState = hazeState,
        ) {
            ActionRow(
                icon = AppIcons.RestartAlt,
                label = "查看同步状态 / 重新同步",
                onClick = onStatusClick,
            )
        }
    }

    spec.groups.forEach { group ->
        item {
            GroupCard(
                title = group.title,
                description = group.description,
                glassLevel = glass,
                hazeState = hazeState,

            ) {
                group.toggles.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    ToggleRow(
                        title = t.title,
                        description = t.description,
                        checked = bridge.get(t.key),
                        onCheckedChange = { onToggle(t.key, it) },
                    )
                }
            }
        }
    }

    item {
        GroupCard(
            title = "操作",
            glassLevel = glass,
            hazeState = hazeState,
        ) {
            ActionRow(
                icon = AppIcons.OpenInNew,
                label = "打开番茄免费小说",
                onClick = onOpenTarget,
            )
            RowDivider()
            ActionRow(
                icon = AppIcons.Layers,
                label = "已识别控件清单",
                onClick = onControlCatalog,
            )
            RowDivider()
            ActionRow(
                icon = AppIcons.Article,
                label = "采集命中日志的步骤",
                onClick = onLogGuide,
            )
            RowDivider()
            ActionRow(
                icon = AppIcons.RestartAlt,
                label = "关闭全部定制",
                onClick = onResetAll,
            )
        }
    }


}
