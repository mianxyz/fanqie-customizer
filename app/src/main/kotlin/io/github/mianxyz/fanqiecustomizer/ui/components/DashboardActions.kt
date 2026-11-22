package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.github.mianxyz.fanqiecustomizer.ui.icons.AppIcons
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel

private val AppreciationIcon by lazy {
    ImageVector.Builder("Appreciation", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = addPathNodes("M12,21 L3.4,12.5 C-2,7.3 5.5,-0.2 12,6.2 C18.5,-0.2 26,7.3 20.6,12.5 Z"),
            fill = SolidColor(Color.Black),
        )
    }.build()
}

/** 等宽正方形操作卡；Switch 的点击由子控件消费，整卡点击共享相同回调。 */
@Composable
fun DashboardActions(
    enabled: Boolean,
    glassLevel: GlassLevel,
    /** 玻璃档位下用于采样，让两张仪表盘卡片也有真实磨砂。 */
    hazeState: dev.chrisbanes.haze.HazeState? = null,
    onEnabledChange: (Boolean) -> Unit,
    onAppreciate: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val masterPress = rememberPressSource()
    val supportPress = rememberPressSource()
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        liquidGlass(
            glassLevel = glassLevel,
            hazeState = hazeState,
            shape = RoundedCornerShape(24.dp),
            // 不用 aspectRatio(1f)：平板宽度大时正方形会把卡片拉到过高。
            // 改为固定最大高度，窄屏仍然是接近正方形，宽屏不再失控。
            modifier = Modifier.weight(1f).heightIn(max = 196.dp).height(168.dp)
                .pressScale(interactionSource = masterPress)
                .clickable(interactionSource = masterPress, indication = null, role = Role.Switch) {
                    onEnabledChange(!enabled)
                }
                .semantics {
                    contentDescription = "总开关卡片"
                    toggleableState = if (enabled) ToggleableState.On else ToggleableState.Off
                },
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(AppIcons.Power, null, tint = if (enabled) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp))
                Text("总开关", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange,
                    modifier = Modifier.semantics { contentDescription = "启用界面定制" },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = scheme.onPrimary,
                        checkedTrackColor = scheme.primary,
                        uncheckedThumbColor = scheme.outline,
                        uncheckedTrackColor = scheme.surfaceContainerHigh,
                        uncheckedBorderColor = Color.Transparent,
                    ),
                )
            }
        }
        liquidGlass(
            glassLevel = glassLevel,
            hazeState = hazeState,
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f).heightIn(max = 196.dp).height(168.dp)
                .pressScale(interactionSource = supportPress)
                .clickable(interactionSource = supportPress, indication = null, role = Role.Button, onClick = onAppreciate),
        ) {
            Column(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Icon(AppreciationIcon, null, tint = scheme.primary, modifier = Modifier.size(28.dp))
                Text("赞赏", style = MaterialTheme.typography.titleLarge, color = scheme.onSurface)
                Box(Modifier.height(48.dp), contentAlignment = Alignment.CenterStart) {
                    Icon(AppIcons.ChevronRight, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}