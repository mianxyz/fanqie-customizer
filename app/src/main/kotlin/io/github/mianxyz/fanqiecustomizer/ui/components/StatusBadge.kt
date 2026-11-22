package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mianxyz.fanqiecustomizer.ui.icons.AppIcons

/**
 * 首页核心状态徽章：花瓣波浪圆（Scalloped / Cookie Shape）。
 *
 * 这是整个仪表盘唯一的异形轮廓，因此天然是视觉焦点，
 * 不需要靠额外颜色或尺寸去争夺注意力。
 *
 * 只有「框架已同步 且 总开关打开」才使用高饱和强调色，其余状态退到中性容器色。
 */
@Composable
fun ScallopedStatusBadge(
    synced: Boolean,
    enabled: Boolean,
    diameter: Dp = 104.dp,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val active = synced && enabled
    // 激活态也不铺满品牌色：参考图用的是「极淡色底 + 品牌色图标」。
    val container = if (active) {
        scheme.primary.copy(alpha = 0.12f)
    } else {
        scheme.surfaceContainerHigh
    }
    val content = if (active) scheme.primary else scheme.onSurfaceVariant
    val badgeShape = ScallopedShape(lobes = 12, depth = 0.085f)

    val paused = LocalEffectsPaused.current
    val spin = if (!paused && !active) {
        val transition = rememberInfiniteTransition(label = "badge")
        transition.animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing), RepeatMode.Restart),
            label = "spin",
        )
    } else null

    Box(
        modifier = modifier
            .size(diameter)
            .clip(badgeShape)
            .background(container, badgeShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (active) AppIcons.CheckCircle else AppIcons.Sync,
            contentDescription = null,
            tint = content,
            modifier = Modifier
                .size(diameter * 0.42f)
                .graphicsLayer { rotationZ = spin?.value ?: 0f },
        )
    }
}

/**
 * 同步状态区块。
 *
 * 未同步时用 M3 Expressive 的波浪形进度条 [LinearWavyProgressIndicator] 表达「正在等待框架连接」；
 * 已同步时用同一条波浪进度条固定在 100%，保持视觉语言统一，同时不再有永动动画。
 */
@Composable
fun SyncStatusBlock(
    synced: Boolean,
    enabled: Boolean,
    statusText: String,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val accent = if (synced && enabled) scheme.primary else scheme.onSurfaceVariant

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            Icon(
                imageVector = if (synced) AppIcons.CheckCircle else AppIcons.Sync,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (synced) "配置已同步" else "等待框架连接",
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(12.dp))
        if (LocalEffectsPaused.current) {
            LinearProgressIndicator(progress = { if (synced) 1f else 0.35f }, modifier = Modifier.fillMaxWidth())
        } else if (synced) {
            LinearWavyProgressIndicator(
                progress = { if (synced) 1f else 0.35f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearWavyProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
        )
    }
}