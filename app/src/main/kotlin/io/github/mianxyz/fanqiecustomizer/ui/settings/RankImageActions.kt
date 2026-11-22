package io.github.mianxyz.fanqiecustomizer.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import io.github.mianxyz.fanqiecustomizer.RankImageStore
import io.github.mianxyz.fanqiecustomizer.SettingsApp

@Composable
fun RankImageActions(enabled: Boolean) {
    val context = LocalContext.current
    var custom by remember { mutableStateOf(RankImageStore.custom(context)) }
    var busy by remember { mutableStateOf(false) }
    val main = remember { Handler(Looper.getMainLooper()) }
    fun save(uri: android.net.Uri?) {
        if (busy) return
        busy = true
        Thread({
            var error: Throwable? = null
            try {
                if (uri == null) RankImageStore.clear(context) else RankImageStore.save(context, uri)
                (context.applicationContext as? SettingsApp)?.publishRankImage()
            } catch (e: Throwable) { error = e }
            main.post {
                custom = RankImageStore.custom(context)
                busy = false
                if (error != null) Toast.makeText(context, "底图保存失败，请重试", Toast.LENGTH_SHORT).show()
            }
        }, "Pulp-rank-select").start()
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) save(uri)
    }
    Column {
        Text(if (busy) "正在保存底图…" else if (custom) "已自定义底图" else "默认磨砂")
        TextButton(enabled = enabled && !busy, onClick = { picker.launch("image/*") }) { Text("选择推荐榜底图") }
        TextButton(enabled = enabled && custom && !busy, onClick = { save(null) }) { Text("恢复默认磨砂") }
    }
}
