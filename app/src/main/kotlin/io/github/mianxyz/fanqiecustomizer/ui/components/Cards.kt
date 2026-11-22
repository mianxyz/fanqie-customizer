package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel

/**
 * 分组卡片。
 *
 * 参考图的关键：卡片不是一个「浮起来的圆角块」，而是**文档里的一段分区**——
 * 圆角很小（14dp），没有投影，边界靠 1px 发丝线而不是阴影。
 * 这样一屏能放下更多信息，也更接近参考视频讲的「内容优先」。
 */
@Composable
fun GroupCard(
    title: String,
    description: String? = null,
    glassLevel: GlassLevel,
    /** 玻璃档位下用于采样，使卡片本体也产生真实磨砂。 */
    hazeState: HazeState? = null,
    note: String? = null,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // 玻璃档位下卡片本身也参与实时采样：需求要求全模块统一液态玻璃，
        // 不再区分「导航层才允许玻璃」。None 档不建立采样层，保持扁平。
        if (glassLevel == GlassLevel.None) {
            ContentSurface(
                glassLevel = glassLevel,
                shape = cardShape(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CardBody(title, description, content)
            }
        } else {
            liquidGlass(
                glassLevel = glassLevel,
                hazeState = hazeState,
                shape = cardShape(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                CardBody(title, description, content)
            }
        }
        if (note != null) {
            Spacer(Modifier.height(7.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** 卡片内容体：标题 + 说明 + 自定义内容。抽出来供两种材质共用。 */
@Composable
private fun CardBody(
    title: String,
    description: String?,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (description != null) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

/** 参考图的圆角很小；这里统一 14dp，比原 24dp 更「文档感」。 */
private fun cardShape() = RoundedCornerShape(14.dp)

/**
 * 单个功能开关行。
 *
 * 行高比上一版收紧（vertical padding 12→10），配合更紧的字阶，
 * 一屏可见的开关数明显增加——这是参考视频「尺度」与「内容」两章的直接落地。
 */
@Composable
fun ToggleRow(
    title: String,
    description: String?,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    val titleColor by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "toggleTitle",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = titleColor,
                fontWeight = if (checked && enabled) FontWeight.Medium else FontWeight.Normal,
            )
            if (description != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title },
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

/**
 * 发丝分隔线。
 *
 * 参考图里分隔线是 1 个物理像素级别的极淡线，几乎只有「暗示」的作用。
 * 用 alpha 压到 0.55，在深色底上刚好可辨而不打断视线。
 */
@Composable
fun RowDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    )
}

/** 动作行（打开番茄 / 日志步骤 / 重置）。 */
@Composable
fun ActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(11.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}