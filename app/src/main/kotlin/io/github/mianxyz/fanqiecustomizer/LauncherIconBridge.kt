package io.github.mianxyz.fanqiecustomizer

import android.content.Context

/**
 * 桌面图标状态桥。
 *
 * 图标可见性由 PackageManager 的组件启用状态决定，不属于 SharedPreferences，
 * 因此不能走 [ConfigBridge]。Java 侧 [LauncherVisibility] 已经是 package-private
 * 且逻辑完整，这里只做一层 Kotlin 包装，不复制任何判断逻辑。
 *
 * 全部方法返回 Result：图标操作在部分 ROM 上会被系统拒绝，
 * 界面需要把失败原因显示出来而不是静默吞掉。
 */
object LauncherIconBridge {

    fun isHidden(context: Context): Result<Boolean> = runCatching {
        LauncherVisibility.isHidden(context)
    }

    fun setHidden(context: Context, hide: Boolean): Result<Boolean> = runCatching {
        val applied = LauncherVisibility.setHidden(context, hide)
        // setHidden 返回「系统已确认状态等于期望值」，false 表示系统没有接受更改。
        if (!applied) error("系统未确认桌面图标更改")
        true
    }
}
