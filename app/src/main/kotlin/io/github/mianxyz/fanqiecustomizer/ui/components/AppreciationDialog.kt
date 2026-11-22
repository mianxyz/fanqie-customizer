package io.github.mianxyz.fanqiecustomizer.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.mianxyz.fanqiecustomizer.R

/**
 * 赞赏弹窗：微信 / 支付宝两枚真实收款码，可存进系统相册。
 *
 * 保存按钮不加任何外观改造：沿用 Material3 的 TextButton，
 * 保持与「关闭」一致的排版，不破坏现有视觉风格。
 */
@Composable
fun AppreciationDialog(onDismiss: () -> Unit) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val context = LocalContext.current

    // 资源固定为两个分支，文件名与相册文件名一一对应。
    val current = if (selected == 0) {
        QrAsset(R.drawable.qr_wechat, "Pulp_WeChat_QR.png", "微信收款码")
    } else {
        QrAsset(R.drawable.qr_alipay, "Pulp_Alipay_QR.png", "支付宝收款码")
    }

    fun saveCurrent() {
        QrSaver.toast(context, QrSaver.save(context, current.resId, current.fileName))
    }

    // API 28 写公共相册需要存储权限；API 29+ 走 MediaStore 不需要。
    val needsLegacyPermission =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) saveCurrent()
        else QrSaver.toast(context, QrSaver.Result.Failed("未授予存储权限"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        title = { Text("赞赏") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PillSegmented(
                    options = listOf("微信", "支付宝"),
                    selectedIndex = selected,
                    onSelect = { selected = it },
                )
                Image(
                    painter = painterResource(current.resId),
                    contentDescription = current.contentDescription,
                    modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                )
                Text(
                    "感谢支持，收款码可直接保存到相册。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (needsLegacyPermission) {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } else {
                        saveCurrent()
                    }
                },
            ) { Text("保存收款码到相册") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

/** 一枚收款码的资源、相册文件名与无障碍描述。 */
private data class QrAsset(
    val resId: Int,
    val fileName: String,
    val contentDescription: String,
)
