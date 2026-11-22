package io.github.mianxyz.fanqiecustomizer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.mianxyz.fanqiecustomizer.ui.icons.AppIcons

/**
 * 分类入口行。
 *
 * 参考图的密集文档风：图标容器从 38dp 收到 34dp、圆角 10dp，
 * 行内边距 13→11，让一屏能看到更多分类。
 * 「n/m」胶囊在全部开启时转琥珀实心——参考图里琥珀只用于这类信息高亮。
 */
@Composable
fun CategoryRow(
    icon: ImageVector,
    title: String,
    blurb: String,
    enabledCount: Int,
    totalCount: Int,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    val scheme = MaterialTheme.colorScheme
    val interaction = rememberPressSource()

    val allOn = totalCount > 0 && enabledCount == totalCount
    val anyOn = enabledCount > 0

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressScale(interactionSource = interaction)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = onClick,
                )
                .padding(vertical = 11.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (anyOn) scheme.primaryContainer else scheme.surfaceContainerHigh
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (anyOn) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurface,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(8.dp))

            // 计数胶囊：未开启时中性，部分开启时琥珀容器，全开时琥珀实心。
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        when {
                            allOn -> scheme.primary
                            anyOn -> scheme.primaryContainer
                            else -> scheme.surfaceContainerHigh
                        }
                    )
                    .padding(horizontal = 7.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$enabledCount/$totalCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        allOn -> scheme.onPrimary
                        anyOn -> scheme.onPrimaryContainer
                        else -> scheme.onSurfaceVariant
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(Modifier.width(4.dp))

            Icon(
                imageVector = AppIcons.ChevronRight,
                contentDescription = null,
                tint = scheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(16.dp),
            )
        }

        if (showDivider) RowDivider()
    }
}
