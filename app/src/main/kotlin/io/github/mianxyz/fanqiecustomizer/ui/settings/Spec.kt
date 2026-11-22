package io.github.mianxyz.fanqiecustomizer.ui.settings

import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mianxyz.fanqiecustomizer.Rules
import io.github.mianxyz.fanqiecustomizer.ui.icons.AppIcons

/**
 * 声明式配置清单。
 *
 * 唯一的硬约束：这里出现的每个 key 必须与 Java 侧 [io.github.mianxyz.fanqiecustomizer.Rules]
 * 以及 [io.github.mianxyz.fanqiecustomizer.PrivacyRules.settingKey] 的集合完全一致。
 * 否则 SettingsApp.sync() 会把该键过滤掉，开关就会「点了不生效」。
 * 这条契约由 tests/SettingsKeyContractTest.java 守护。
 */
data class ToggleSpec(
    val key: String,
    val title: String,
    val description: String? = null,
    val enabledWhen: String? = null,
)

/**
 * 整型参数滑块。
 *
 * 与 [ToggleSpec] 共用同一条同步通道，但存的是 Int：
 * SettingsApp.sync() 会用 putInt 写进远程偏好，ModuleHook 用 intValue 读回。
 * 因此这里的 key 必须出现在 Java 侧 Rules.INT_KEYS 里。
 */
data class SliderSpec(
    val key: String,
    val title: String,
    val description: String? = null,
    val min: Int,
    val max: Int,
    val default: Int,
    /** 单位后缀，例如 "%" 或 "dp"。 */
    val unit: String = "",
    /** 仅当这个布尔项开启时才显示该滑块。null 表示始终显示。 */
    val visibleWhen: String? = null,
    val options: List<String> = emptyList(),
)

data class GroupSpec(
    val title: String,
    val description: String? = null,
    val toggles: List<ToggleSpec> = emptyList(),
    val note: String? = null,
)

/** 页面顶部说明性备注。 */
data class PageSpec(
    val label: String,
    val headline: String,
    val subhead: String,
    val groups: List<GroupSpec>,
)

/**
 * 功能页二级分类。
 *
 * 一级页只渲染分类入口（图标 + 标题 + 已开启数 / 总数 + 箭头），
 * 点进去才是开关列表。这样 61 个键不会平铺成一屏几十行。
 */
data class CategorySpec(
    val id: String,
    val title: String,
    val blurb: String,
    val icon: ImageVector,
    val toggles: List<ToggleSpec>,
    val note: String? = null,
    /** 该分类下的整型参数滑块，渲染在开关列表之后。 */
    val sliders: List<SliderSpec> = emptyList(),
)

/**
 * 一级页的一张分组卡片：把相关分类聚到一起。
 * 例如「界面精简」下挂底部导航 / 书城频道 / 悬浮入口 / 书架展示。
 */
data class CardSpec(
    val title: String,
    val categories: List<CategorySpec>,
)

object Catalog {

    /** 与宿主共享同一默认值来源；全局玻璃主开关默认关闭，两个子项默认开启。 */
    fun defaultOn(key: String): Boolean = Rules.defaultOn(key)

    /** 底部导航五个入口——顺序与 Rules.TAB_KEYS / TAB_LABELS 严格一致。 */
    private val tabs = Rules.TAB_KEYS.mapIndexed { i, k ->
        ToggleSpec(k, "隐藏" + Rules.TAB_LABELS[i], null)
    }

    private val channels = Rules.CHANNEL_KEYS.mapIndexed { i, k ->
        ToggleSpec(k, "隐藏" + Rules.CHANNEL_LABELS[i], null)
    }

    private val mineEntries = Rules.MINE_KEYS.mapIndexed { i, k ->
        ToggleSpec(k, "隐藏" + Rules.MINE_LABELS[i], if (k == "mine_vip") "隐藏会员中心入口，保留我的书币。" else null)
    }

    private val tabletEntries = TabletEntry.KEYS.mapIndexed { i, k ->
        ToggleSpec(k, "隐藏" + TabletEntry.LABELS[i], null)
    }

    val dashboard = PageSpec(
        label = "仪表盘",
        headline = "Pulp",
        subhead = "",
        groups = listOf(
            GroupSpec(
                title = "总开关",
                toggles = listOf(
                    ToggleSpec("enabled", "启用界面定制", null),
                ),
            ),
            GroupSpec(
                title = "默认打开的底部导航",
                toggles = StartupEntry.KEYS.mapIndexed { i, k ->
                    ToggleSpec(k, "默认打开「" + Rules.TAB_LABELS[i] + "」", null)
                },
            ),
            GroupSpec(
                title = "桌面图标",
                toggles = listOf(
                    ToggleSpec(LauncherEntry.KEY, "隐藏桌面图标", null),
                ),
            ),
        ),
    )

    // ───────────────────────── 功能：三大卡片 ─────────────────────────

    private val catNav = CategorySpec(
        id = "nav",
        title = "底部导航",
        blurb = "至少保留一个入口。",
        icon = AppIcons.NavigationTabs,
        toggles = tabs,
    )

    private val catChannels = CategorySpec(
        id = "channels",
        title = "书城频道",
        blurb = "保留至少一个频道；只剩一个时自动折叠频道栏。",
        icon = AppIcons.BookChannels,
        toggles = channels,
    )

    private val catFloating = CategorySpec(
        id = "floating",
        title = "悬浮入口",
        blurb = "浮窗与快捷入口。",
        icon = AppIcons.PictureInPicture,
        toggles = listOf(
            // 两个底栏样式互斥：开启一个会自动关闭另一个（见 SettingsActivity 的 onToggle）。
            ToggleSpec(
                "glass_bottom_bar_floating", "启用玻璃底栏",
                "底栏脱离屏幕边缘成悬浮胶囊：左右留白、底部悬浮、大圆角玻璃。",
            ),
            ToggleSpec(
                "glass_bottom_bar_docked", "玻璃底栏（贴底）",
                "保持贴底全宽，仅替换为通透玻璃背景与顶部高光分割线。",
            ),
            ToggleSpec("glass_top_bar", "顶部栏毛玻璃渐变", "在顶栏上叠加一层渐变玻璃，内容从下方透出。"),
            ToggleSpec("refresh_confirm", "下拉刷新确认", "下拉刷新时弹出确认框"),
            ToggleSpec("hide_search_bar", "隐藏搜索栏和分类", "折叠搜索栏与分类整行，让下方内容顶上去。"),
            ToggleSpec("hide_welfare_float", "隐藏福利与任务浮图", null),
            ToggleSpec("hide_post_float", "隐藏发帖快捷入口", null),
            ToggleSpec("hide_drama_resume", "隐藏短剧继续播放浮窗", "仅隐藏自动续播浮窗。"),
            ToggleSpec(
                "glass_bottom_bar_swipe", "悬浮底栏滑动切换",
                "在底栏上左右滑动即可切换 Tab，保留原有单击操作。",
            ),
            ToggleSpec(
                "hide_widget_prompt", "禁止「添加桌面卡片」提示",
                "拦掉番茄引导你把书城/书籍添加到桌面的弹窗与提示。",
            ),
        ),
        sliders = listOf(
            SliderSpec(
                key = "glass_bar_alpha", title = "背景不透明度",
                description = "越高越不透明，越低越通透。",
                min = 10, max = 90, default = 55, unit = "%",
                visibleWhen = null,
            ),
            SliderSpec(
                key = "glass_bar_blur", title = "模糊强度",
                description = "高斯模糊：0 关闭；悬浮/贴底 1 = 16dp、2 = 64dp；液态 1 = 1dp、2 = 2.5dp（Android 13+）。",
                min = 0, max = 2, default = 1,
                visibleWhen = null,
            ),
            SliderSpec(
                key = "glass_bar_bounce", title = "液态滑块 Q 弹度",
                description = "调节液态玻璃底栏滑块的弹性回弹与果冻形变幅度（默认 50%，仅液态玻璃生效）。",
                min = 0, max = 100, default = 50, unit = "%",
            ),
            SliderSpec(
                key = "glass_bar_width", title = "悬浮底栏长度",
                description = "调节悬浮玻璃底栏占屏幕宽度的比例（默认 100%）。悬浮玻璃与液态玻璃均生效。",
                min = 50, max = 100, default = 100, unit = "%",
                visibleWhen = "glass_bottom_bar_floating",
            ),
            SliderSpec(
                key = "glass_bar_corner", title = "悬浮圆角",
                description = "0dp = 纯直角矩形，36dp = 全胶囊圆角。悬浮玻璃与液态玻璃均生效。",
                min = 0, max = 36, default = 24, unit = "dp",
                visibleWhen = "glass_bottom_bar_floating",
            ),
            SliderSpec(
                key = "glass_bar_margin", title = "悬浮左右边距",
                description = "悬浮玻璃与液态玻璃均生效。",
                min = 0, max = 32, default = 12, unit = "dp",
                visibleWhen = "glass_bottom_bar_floating",
            ),
            SliderSpec(
                key = "glass_bar_bottom", title = "悬浮底部间距",
                description = "悬浮玻璃与液态玻璃均生效。",
                min = 0, max = 40, default = 16, unit = "dp",
                visibleWhen = "glass_bottom_bar_floating",
            ),
        ),
    )

    private val catShelf = CategorySpec(
        id = "shelf",
        title = "书架展示",
        blurb = "书架页的展开行为与金币控件。",
        icon = AppIcons.MenuBook,
        toggles = listOf(
            ToggleSpec("auto_expand_shelf", "自动展开全部", null),
            ToggleSpec("hide_coin", "隐藏金币控件", null),
        ),
    )

    private val catReader = CategorySpec(
        id = "reader",
        title = "阅读界面",
        blurb = "章末入口与听书快捷按钮。",
        icon = AppIcons.ChromeReaderMode,
        toggles = listOf(
            ToggleSpec("hide_reader_video_entry", "隐藏章末免广告入口", null),
            ToggleSpec("hide_reader_audio", "隐藏快捷听书按钮"),
            ToggleSpec("hide_status_bar", "常驻隐藏系统状态栏", "内容延伸到状态栏下方；需要时从顶部下滑仍可见。"),
            ToggleSpec("unlimited_listen", "听书不中断（免广告）", "让听书额度判定恒为已授权，不再弹「看广告继续」。"),
            ToggleSpec("unlimited_autoread", "自动阅读不中断（免广告）", "让自动阅读额度判定恒为已授权，不再中断。"),
        ),
    )

    private val catAds = CategorySpec(
        id = "ads",
        title = "广告与游戏",
        blurb = "横幅、插屏、直播推广与章末小游戏。",
        icon = AppIcons.Block,
        toggles = listOf(
            ToggleSpec("hide_splash_ad", "屏蔽启动开屏广告", "让宿主按「未命中投放」处理，启动流程照常。"),
            ToggleSpec("hide_banner", "隐藏原生横幅广告", null),
            ToggleSpec("hide_reader_ads", "隐藏阅读广告组件", null),
            ToggleSpec("hide_reader_live_popup", "屏蔽阅读页直播弹窗", "需同时开启「隐藏阅读广告组件」。"),
            ToggleSpec("hide_games", "隐藏章末小游戏"),
        ),
    )

    private val catIncome = CategorySpec(
        id = "income",
        title = "收益与游戏",
        blurb = "「我的」页面的收益卡片与游戏区域。",
        icon = AppIcons.Savings,
        toggles = listOf(
            ToggleSpec("hide_income", "隐藏收益记录与收益卡片", "可能同时隐藏余额显示区域。"),
            ToggleSpec("hide_frequent_games", "隐藏常玩游戏", null),
        ),
    )

    private val catMine = CategorySpec(
        id = "mine",
        title = "「我的」功能入口",
        blurb = "逐项隐藏「我的」页面的九宫格入口。",
        icon = AppIcons.Person,
        toggles = mineEntries,
    )

    private val catTablet = CategorySpec(
        id = "tablet",
        title = "平板专属入口",
        blurb = "600dp 以上大屏布局。",
        icon = AppIcons.Tablet,
        toggles = tabletEntries,
    )

    /**
     * VIP 与实验功能。
     * local_vip 原本放在「关于」页，这里迁入功能页——
     * 它是一个会影响宿主行为的功能开关，不属于关于页的元信息。
     */
    private val catVip = CategorySpec(
        id = "vip",
        title = "VIP 与实验功能",
        blurb = "本地判断类 Hook，可选开启。",
        icon = AppIcons.WorkspacePremium,
        toggles = listOf(
            ToggleSpec("local_vip", "本地 VIP 判断 Hook", "仅影响本地判断。"),
        ),
    )

    private val catGlobalGlass = CategorySpec(
        id = "global_glass",
        title = "全局玻璃",
        blurb = "卡片文字区域与页面面板的玻璃外观。",
        icon = AppIcons.GlassLayers,
        toggles = listOf(
            ToggleSpec("glass_global_enabled", "启用全局玻璃", "为宿主页面的卡片与面板启用玻璃外观。"),
            ToggleSpec("glass_global_card_text", "书籍文字区模糊玻璃", "延伸同卡封面氛围色，封面与文字保持清晰。", enabledWhen = "glass_global_enabled"),
            ToggleSpec("glass_global_panels", "搜索栏、卡片与弹窗分级玻璃", "搜索轻模糊、面板中模糊、刷新确认弹窗重模糊。", enabledWhen = "glass_global_enabled"),
            ToggleSpec("glass_global_store_liquid", "书城搜索与分类液态玻璃", "为书城顶部搜索框与分类按钮启用与底栏同款的液态玻璃效果（不影响推荐流）。", enabledWhen = "glass_global_enabled"),
            ToggleSpec("glass_global_mine_liquid", "我的页顶部组件液态玻璃", "为「我的」页顶部搜索框、九宫格与金币福利卡片启用与底栏同款的液态玻璃效果（不影响推荐流）。", enabledWhen = "glass_global_enabled"),
            ToggleSpec("glass_global_rank_cover", "推荐榜底部模糊", "为推荐榜底部启用固定毛玻璃背景，自动适配日间与夜间。", enabledWhen = "glass_global_enabled"),
            ToggleSpec("glass_rank_edge_feather", "推荐榜边缘轻微羽化", "将推荐榜左右两侧边缘改为柔和透明羽化过渡，避免内容硬裁切（默认关闭）。", enabledWhen = "glass_global_enabled"),
            ToggleSpec("rank_equal_columns", "推荐榜多列均分铺满", "将推荐榜所有列强制均分铺满当前屏幕（适合平板宽屏；手机端建议关闭，保持默认每页 8 本横向滑动）。"),
        ),
    )

    private val catGlassNav = CategorySpec(
        id = "glass_nav", title = "底部导航栏", blurb = "玻璃底栏、形态、模糊与液态滑块。", icon = AppIcons.GlassDock,
        toggles = catFloating.toggles.filter { it.key in setOf("glass_bottom_bar_floating", "glass_bottom_bar_docked", "glass_bottom_bar_swipe") }.map {
            if (it.key == "glass_bottom_bar_swipe") it.copy(title = "液态滑块与横向滑动切换", enabledWhen = "glass_bar_enabled") else it
        },
        sliders = listOf(SliderSpec(key = "glass_bar_style", title = "底栏样式", min = 0, max = 2, default = 0, options = listOf("悬浮玻璃", "贴底玻璃", "液态玻璃"))) + catFloating.sliders,
        note = "贴底旧开关保留用于旧配置兼容；新配置使用样式选择。",
    )
    private val catTopVisual = CategorySpec("top_visual", "顶部与页面视觉", "书城顶部滚动羽化。", AppIcons.TopFade, catFloating.toggles.filter { it.key == "glass_top_bar" })
    private val catMallLayout = CategorySpec("mall_layout", "书城模块精简", "搜索整行与原有书城模块。", AppIcons.LayoutTrim, catFloating.toggles.filter { it.key in setOf("hide_search_bar", "hide_widget_prompt") })
    private val catInteraction = CategorySpec("interaction", "交互与防误触", "刷新确认与续播浮窗。", AppIcons.GestureConfirm, catFloating.toggles.filter { it.key in setOf("refresh_confirm", "hide_drama_resume") })
    private val catFloatEntries = catFloating.copy(id = "floating", title = "浮窗与快捷入口", blurb = "福利、任务与发帖浮窗。", icon = AppIcons.FloatingShortcut, toggles = catFloating.toggles.filter { it.key in setOf("hide_welfare_float", "hide_post_float") }, sliders = emptyList())
    val featureCards: List<CardSpec> = listOf(
        CardSpec("界面美化 / 界面修改", listOf(catGlobalGlass, catGlassNav, catTopVisual, catNav, catShelf)),
        CardSpec("书城布局与精简", listOf(catMallLayout, catChannels)),
        CardSpec("交互与体验", listOf(catInteraction, catFloatEntries)),
        CardSpec("阅读与净化", listOf(catReader, catAds)),
        CardSpec("个人与扩展", listOf(catIncome, catMine, catTablet, catVip)),
    )

    /** 按 id 查分类，供二级详情页使用。 */
    fun category(id: String): CategorySpec? =
        featureCards.flatMap { it.categories }.firstOrNull { it.id == id }

    /** 功能页所有可配置布尔键（不包含 dashboard / theme 的键）。 */
    val featureKeys: List<String> =
        featureCards.flatMap { it.categories }.flatMap { it.toggles }.map { it.key }

    /** 功能页所有整型参数键（滑块）。 */
    val featureIntKeys: List<String> =
        featureCards.flatMap { it.categories }.flatMap { it.sliders }.map { it.key }

    /**
     * 互斥组：开启其中任意一项，必须关闭同组其余项。
     * 底栏样式只能有一种——悬浮和贴底同时生效会互相覆盖。
     */
    val mutexGroups: List<List<String>> = listOf(
        listOf("glass_bottom_bar_floating", "glass_bottom_bar_docked"),
    )

    /** 返回与 [key] 互斥的其它键。 */
    fun mutexOf(key: String): List<String> =
        mutexGroups.firstOrNull { key in it }?.filter { it != key } ?: emptyList()

    val features = PageSpec(
        label = "功能",
        headline = "功能开关",
        subhead = "",
        groups = emptyList(),
    )

    val theme = PageSpec(
        label = "主题",
        headline = "主题与外观",
        subhead = "",
        groups = listOf(
            GroupSpec(
                title = "毛玻璃",
                toggles = emptyList(),
            ),
            GroupSpec(
                title = "书籍编号工具",
                toggles = listOf(
                    ToggleSpec("show_book_id", "显示书籍内部编号", "点击编号可复制。"),
                    ToggleSpec("book_id_search", "编号搜索直达", "搜索书籍编号直达详情。"),
                ),
            ),
            GroupSpec(
                title = "下架书籍与计数",
                toggles = listOf(
                    ToggleSpec("force_open_removed", "尝试打开下架书籍", null),
                    ToggleSpec("raw_read_counts", "显示接口原始阅读计数", "显示接口原始值。"),
                ),
            ),
            GroupSpec(
                title = "应用提示",
                toggles = listOf(
                    ToggleSpec("hide_update_popup", "关闭自动更新弹窗", null),
                ),
            ),
        ),
    )

    /** 关于页：local_vip 已迁出，这里只保留日志、书籍工具入口。 */
    val about = PageSpec(
        label = "关于",
        headline = "日志与关于",
        subhead = "",
        groups = listOf(
            GroupSpec(
                title = "日志与配置",
                toggles = listOf(
                    ToggleSpec("debug", "输出命中日志", null),
                    ToggleSpec("trace_ab", "记录实验配置名称", "需同时开启「输出命中日志」。"),
                ),
            ),
            GroupSpec(
                title = "书籍工具",
                toggles = listOf(
                    ToggleSpec("in_app_entry", "在番茄设置中显示书籍编号工具", null),
                ),
            ),
        ),
    )

    val pages = listOf(dashboard, features, theme, about)

    /** 所有受管 key（含非布尔项）。用于「关闭全部定制」的精确复位。 */
    val allToggleKeys: List<String> = (
        pages.flatMap { it.groups }.flatMap { it.toggles }.map { it.key } +
            featureKeys
        ).distinct()
}

/** StartupTabRules.KEYS 的只读镜像（Java 侧为包级可见）。 */
object StartupEntry {
    const val PREFIX = "start_"
    val KEYS = listOf("start_store", "start_series", "start_earn", "start_shelf", "start_mine")
}

object TabletEntry {
    val LABELS = listOf("视频创作", "兴趣偏好", "我的笔记", "看过的人", "番茄指南", "我的钱包", "活动中心", "我的公益", "我的催更")
    val KEYS = listOf(
        "pad_video_creation", "pad_interest", "pad_notes", "pad_seen_people",
        "pad_guide", "pad_wallet", "pad_activity", "pad_public_welfare", "pad_urge_update",
    )
}

/** 桌面图标不是 SharedPreferences 项，归属 PackageManager。 */
object LauncherEntry {
    const val KEY = "__launcher_hidden__"
}
