package io.github.mianxyz.fanqiecustomizer.ui.theme

/**
 * 毛玻璃三档。默认「无」——保持干净扁平的明度分层质感。
 *
 * 数值持久化在同一个 SharedPreferences("settings") 里，
 * key = [KEY_GLASS]，取值 0/1/2，与其余开关共用一套同步逻辑。
 */
enum class GlassLevel(val stored: Int, val label: String, val blurb: String) {
    /** 无：纯扁平，明度差分层 + 无投影。 */
    None(0, "无", "干净的扁平质感，仅用明度差分层"),

    /** 标准：悬浮底栏实时模糊，卡片仅做半透明填充。 */
    Standard(1, "标准", "柔和磨砂底栏与半透明卡片"),

    /** 极致：更高模糊半径 + 更强的微弱高光描边。 */
    Ultra(2, "极致", "更高模糊半径与更明显的高光描边");

    companion object {
        const val KEY_GLASS = "glass_level"

        fun from(stored: Int?): GlassLevel =
            entries.firstOrNull { it.stored == stored } ?: None
    }
}