package io.github.mianxyz.fanqiecustomizer

import android.app.AlertDialog
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.mianxyz.fanqiecustomizer.ui.settings.Catalog
import io.github.mianxyz.fanqiecustomizer.ui.settings.SettingsShell
import io.github.mianxyz.fanqiecustomizer.ui.theme.ApplySystemBars
import io.github.mianxyz.fanqiecustomizer.ui.theme.FanqieCustomizerTheme

/**
 * 模块设置界面（Compose 承载）。
 *
 * 类名与包名保持不变：AndroidManifest 里的 LSPosed 入口、LauncherAlias，
 * 以及用户从 LSPosed 打开模块设置的习惯路径全部继续有效。
 *
 * 关键点——本类不包含任何 Hook 逻辑，只做三件事：
 *   1. 通过 [ConfigBridge] 读写既有 SharedPreferences（"settings"）
 *   2. 通过 [SettingsApp] 复用既有的框架同步通道
 *   3. 渲染 Compose 界面
 * 因此重写界面不会触及 ModuleHook / Rules 或任何既有 Hook 类的行为。
 */
class SettingsActivity : ComponentActivity() {

    private lateinit var bridge: ConfigBridge

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // 平板（尤其 API 29+）默认会给状态栏/导航栏强加一层半透明白底来保证
        // 图标对比度，结果是液态玻璃背景铺到顶部却still被那层白压住。
        // 这里显式关掉强制对比度，背景才能真正无缝延伸到状态栏下方。
        if (Build.VERSION.SDK_INT >= 29) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        bridge = ConfigBridge(this)

        val app = application as SettingsApp

        setContent {
            val glass by bridge.glass.collectAsStateWithLifecycle()
            val dark = isSystemInDarkTheme()

            var launcherHidden by remember { mutableStateOf(false) }
            var synced by remember { mutableStateOf(app.synced()) }
            var statusText by remember { mutableStateOf(app.status()) }

            // 同步结果由 Java listener 通知；配置开关由桥接层的快照状态直接驱动。
            DisposableEffect(app, lifecycle) {
                val listener = Runnable {
                    synced = app.synced()
                    statusText = app.status()
                }
                val observer = object : DefaultLifecycleObserver {
                    override fun onResume(owner: LifecycleOwner) {
                        app.sync()
                        launcherHidden = LauncherIconBridge.isHidden(this@SettingsActivity)
                            .getOrDefault(false)
                    }
                }
                app.setListener(listener)
                lifecycle.addObserver(observer)
                listener.run()
                launcherHidden = LauncherIconBridge.isHidden(this@SettingsActivity).getOrDefault(false)
                onDispose {
                    lifecycle.removeObserver(observer)
                    app.clearListener(listener)
                }
            }

            FanqieCustomizerTheme(darkTheme = dark, glassLevel = glass) {
                ApplySystemBars(darkTheme = dark)

                SettingsShell(
                    bridge = bridge,
                    glassLevel = glass,
                    synced = synced,
                    statusText = statusText,
                    launcherHidden = launcherHidden,
                    onLauncherHiddenChange = { want ->
                        LauncherIconBridge.setHidden(this@SettingsActivity, want)
                            .onSuccess { launcherHidden = want }
                            .onFailure { e ->
                                Toast.makeText(
                                    this@SettingsActivity,
                                    "图标设置失败：" + PrivacyRules.errorType(e),
                                    Toast.LENGTH_LONG,
                                ).show()
                                launcherHidden = LauncherIconBridge
                                    .isHidden(this@SettingsActivity)
                                    .getOrDefault(false)
                            }
                    },
                    onToggle = { key, value ->
                        var ok = bridge.set(key, value)
                        // 互斥组：开启一项就关掉同组其余项（底栏样式只能有一种）。
                        // 两个同时为真会让两次 Hook 互相覆盖背景。
                        if (ok && value) {
                            for (other in Catalog.mutexOf(key)) {
                                if (bridge.get(other)) ok = bridge.set(other, false) && ok
                            }
                        }
                        if (!ok) {
                            Toast.makeText(this@SettingsActivity, "保存失败，请重试", Toast.LENGTH_LONG)
                                .show()
                        } else {
                            app.sync()
                        }
                    },
                    onStartupTab = { key ->
                        if (bridge.setStartupTab(key)) {
                            app.sync()
                        } else {
                            Toast.makeText(this@SettingsActivity, "保存失败，请重试", Toast.LENGTH_LONG)
                                .show()
                        }
                    },
                    onGlassChange = { level -> bridge.setGlass(level) },
                    onStatusClick = { showStatusDialog(app) },
                    onOpenTarget = { openTarget() },
                    onLogGuide = { showLogGuide() },
                    onResetAll = { confirmReset(app) },
                    onControlCatalog = { showControlCatalog() },
                )
            }
        }
    }

    private fun showStatusDialog(app: SettingsApp) {
        AlertDialog.Builder(this)
            .setTitle("配置同步状态")
            .setMessage(app.status())
            .setPositiveButton("重新同步") { _, _ -> app.sync() }
            .setNegativeButton("关闭", null)
            .show()
    }

    /**
     * 已识别控件清单。
     *
     * 明确区分「已适配」与「运行期命中过」：前者是模块确认支持的控件，
     * 后者是当前设备实际触发过的。未适配的宿主控件不在此列，
     * 所以这份清单不是「整页所有控件」，文案里必须讲清楚。
     */
    private fun showControlCatalog() {
        val all = ControlCatalog.all() + ControlCatalog.grouped()
        val hits = ControlCatalog.observed(this)

        val body = buildString {
            appendLine("模块已适配 " + all.size + " 个控件，其中本机命中过 " + hits.size + " 个。")
            appendLine("未适配的宿主控件不在清单内。")
            appendLine()
            var page = ""
            all.forEach { entry ->
                if (entry.page != page) {
                    page = entry.page
                    appendLine("【" + page + "】")
                }
                val mark = if (hits.contains(entry.id)) "✓" else "·"
                appendLine(mark + " " + entry.label)
            }
        }

        android.app.AlertDialog.Builder(this)
            .setTitle("已识别控件")
            .setMessage(body)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun showLogGuide() {
        AlertDialog.Builder(this)
            .setTitle("采集命中日志")
            .setMessage(Rules.LOG_GUIDE)
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun confirmReset(app: SettingsApp) {
        AlertDialog.Builder(this)
            .setTitle("关闭全部定制？")
            .setMessage(
                "将关闭番茄定制选项和书籍编号工具入口，不改变桌面图标设置。重新打开番茄后生效。"
            )
            .setPositiveButton("关闭全部") { _, _ ->
                if (bridge.resetAll()) {
                    app.sync()
                } else {
                    Toast.makeText(this, "保存失败，请重试", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun openTarget() {
        val intent = Rules.TARGET_PACKAGES.firstNotNullOfOrNull { target ->
            packageManager.getLaunchIntentForPackage(target)
        }
        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "未找到番茄免费小说", Toast.LENGTH_SHORT).show()
        }
    }
}
