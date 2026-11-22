package io.github.mianxyz.fanqiecustomizer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import io.github.mianxyz.fanqiecustomizer.ui.settings.Catalog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Compose 界面与既有 Java 配置层之间的唯一桥梁。
 *
 * 硬约束（务必保持）：
 * 1. 存储介质必须是 [Rules.PREFS]（"settings"）——settings 目录下同一个
 *    SharedPreferences 文件，Java 侧 SettingsApp / ModuleHook 读的就是它。
 * 2. 写入必须走 commit()，不能只写 apply()。SettingsApp.sync() 会立刻读取
 *    local().getAll() 并推送到 XposedService 的远程偏好；若异步落盘，
 *    推送的可能是旧快照。
 * 3. 每个 key 必须通过 [PrivacyRules.settingKey] 白名单，否则同步时被静默丢弃。
 *
 * 这里刻意不持有任何 Hook 状态：界面只负责读写布尔值，Hook 行为完全由
 * Java 侧的 ModuleHook / Rules 决定，因此重写界面不会改变任何已有功能。
 */
@Stable
class ConfigBridge(context: Context) {

    private val settingsApp = context.applicationContext as? SettingsApp

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(Rules.PREFS, Context.MODE_PRIVATE)

    /** 每个读取开关的组合直接订阅对应值，保存成功后才发布变更。 */
    private val state = mutableStateMapOf<String, Boolean>().apply {
        putAll(defaultState())
        for ((key, value) in prefs.all) {
            if (PrivacyRules.settingKey(key) && value is Boolean) put(key, value)
        }
    }

    private fun defaultState(): Map<String, Boolean> =
        (Catalog.allToggleKeys + STARTUP_KEYS).associateWith(Catalog::defaultOn)

    private val _glass = MutableStateFlow(
        io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel.from(
            readIntOrDefault(
                io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel.KEY_GLASS,
                0,
            )
        )
    )
    val glass: StateFlow<io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel> = _glass.asStateFlow()

    private fun readIntOrDefault(key: String, fallback: Int): Int =
        try {
            prefs.getInt(key, fallback)
        } catch (_: ClassCastException) {
            // 老版本可能把同名键存成了 String/Boolean——回退到默认值而不是崩溃。
            fallback
        }

    /** 整型参数（滑块）的响应式快照。key 必须在 Rules.INT_KEYS 内。 */
    private val ints = mutableStateMapOf<String, Int>().apply {
        for ((key, value) in prefs.all) {
            if (value is Int && PrivacyRules.isIntKey(key)) put(key, value)
        }
    }

    /** 读整型参数；没存过就用 Rules 里的缺省值。 */
    fun getInt(key: String): Int = ints[key] ?: Rules.intDefault(key)

    /**
     * 写整型参数。
     *
     * 与布尔一样走 commit()：SettingsApp.sync() 会立刻读 prefs 推送，
     * 用 apply() 可能把旧值推上去。
     * 越界值按 Rules 的区间钳制，避免滑块溢出写进宿主。
     */
    fun setInt(key: String, value: Int): Boolean {
        if (!PrivacyRules.isIntKey(key)) return false
        val clamped = Rules.clampInt(key, value)
        val editor = prefs.edit().putInt(key, clamped)
        val migrateDocked = key == "glass_bar_style" && get("glass_bottom_bar_docked")
        if (migrateDocked) editor.putBoolean("glass_bottom_bar_floating", true).putBoolean("glass_bottom_bar_docked", false)
        val ok = editor.commit()
        if (ok) Snapshot.withMutableSnapshot {
            ints[key] = clamped
            if (migrateDocked) { state["glass_bottom_bar_floating"] = true; state["glass_bottom_bar_docked"] = false }
        }
        if (ok) settingsApp?.sync()
        return ok
    }

    /** 读取布尔项，缺省值来自 [Catalog.defaultOn]（与 Java 侧 Rules.defaultOn 一致）。 */
    fun get(key: String): Boolean = state[key] ?: Catalog.defaultOn(key)

    /** 写入布尔项。返回是否成功落盘。 */
    fun set(key: String, value: Boolean): Boolean {
        // 桌面图标占位项由 LauncherIconBridge 管理，不写入模块偏好。
        if (!PrivacyRules.settingKey(key)) return false
        val ok = prefs.edit().putBoolean(key, value).commit()
        if (ok) Snapshot.withMutableSnapshot { state[key] = value }
        return ok
    }

    fun setGlass(level: io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel) {
        val ok = prefs.edit()
            .putInt(io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel.KEY_GLASS, level.stored)
            .commit()
        if (ok) _glass.value = level
    }

    /**
     * 默认启动页是单选：开启一项必须关闭其余项。
     * 空列表表示「自动（避开隐藏入口）」。
     */
    fun setStartupTab(preferredKey: String?): Boolean {
        if (preferredKey != null && preferredKey !in STARTUP_KEYS) return false
        val editor = prefs.edit()
        for (k in STARTUP_KEYS) editor.remove(k)
        if (preferredKey != null) editor.putBoolean(preferredKey, true)
        val ok = editor.commit()
        if (ok) Snapshot.withMutableSnapshot {
            for (key in STARTUP_KEYS) state[key] = key == preferredKey
        }
        return ok
    }

    fun startupTab(): String? = STARTUP_KEYS.firstOrNull { state[it] == true }

    /**
     * 把当前布尔项推送到框架。复用 Java 侧 [SettingsApp.sync]，不重复实现
     * XposedService 交互逻辑。
     */
    fun syncToFramework(app: SettingsApp) {
        app.sync()
    }

    /**
     * 关闭全部定制：清空偏好，显式关闭两个原本默认开启的键。
     * 行为与原 SettingsActivity 的「关闭全部定制」完全一致。
     */
    fun resetAll(): Boolean {
        val ok = prefs.edit()
            .clear()
            .putBoolean("in_app_entry", false)
            .putBoolean("hide_reader_live_popup", false)
            .commit()
        if (ok) {
            Snapshot.withMutableSnapshot {
                state.clear()
                state.putAll(defaultState())
                state["in_app_entry"] = false
                state["hide_reader_live_popup"] = false
            }
            _glass.value = io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel.None
        }
        return ok
    }

    /** 导出全部已保存键值，用于「配置同步状态」对话框。 */
    fun snapshot(): Map<String, Boolean> =
        prefs.all.entries
            .filter { PrivacyRules.settingKey(it.key) && it.value is Boolean }
            .associate { it.key to (it.value as Boolean) }

    companion object {
        val STARTUP_KEYS = listOf(
            "start_store", "start_series", "start_earn", "start_shelf", "start_mine",
        )
    }
}
