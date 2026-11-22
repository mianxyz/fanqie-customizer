package io.github.mianxyz.fanqiecustomizer;

import java.util.*;

public final class Rules {
    public static final String PACKAGE = "io.github.mianxyz.fanqiecustomizer";
    public static final String TARGET = "com.dragon.read";
    /**
     * Google Play 海外版包名。
     *
     * 它不是国内版的换皮分发：独立包名、独立签名、独立类表。
     * 但已用四个版本的 Play APK 逐个核对，本模块依赖的 8 个关键类
     * （SplashActivity、AttributionManager、NewBookMallFragment、
     * FanqieMineFragmentV2、ReaderAdManager、PopProxy、
     * NsUtilsDependImpl、OpeningScreenADActivity）全部存在且同名，
     * 稳定入口存在仅说明可探测；精确类/方法/字段/资源由 HostSymbols 按包名与版本独立解析。
     */
    public static final String TARGET_PLAY = "com.dragon.read.oversea.gp";

    /** 本模块适配的全部宿主包名。 */
    public static final String[] TARGET_PACKAGES = {TARGET, TARGET_PLAY};

    /** 是否为受支持的宿主包名。 */
    public static boolean isTargetPackage(String name) {
        if (name == null) return false;
        for (String pkg : TARGET_PACKAGES) if (pkg.equals(name)) return true;
        return false;
    }
    public static final String PREFS = "settings";
    /**
     * 不属于 底部导航 / 频道 / 我的 三组的分组外开关。
     * 这是 [PrivacyRules.settingKey] 的白名单来源，也是免 Root 后备通道
     * 同步的全部键；改动必须同步 tests/SettingsKeyContractTest。
     */
    /**
     * 整型参数键（滑块）。与布尔开关分开管理：
     * 布尔走 EXTRA_KEYS，整型走 INT_KEYS，两者共用同一条同步通道。
     */
    public static final String[] INT_KEYS = {
        "glass_bar_alpha",      // 底栏背景不透明度 10..90（百分比）
        "glass_bar_blur",       // 模糊强度档 0..2
        "glass_bar_corner",     // 悬浮圆角 dp 0..36（0 = 纯直角矩形）
        "glass_bar_margin",     // 悬浮左右边距 dp 0..32
        "glass_bar_bottom",     // 悬浮底部间距 dp 0..40
        "glass_bar_style",
        "glass_bar_bounce",     // 液态弹性 0..100
        "glass_bar_width",      // 悬浮底栏宽度百分比 50..100
    };

    /**
     * 已退役的旧键。只用于把老配置平滑迁移到新键，**不进任何导出列表**。
     *
     * glass_bottom_bar 是 5.3.5 及更早版本的单一底栏开关，
     * 现在拆成 floating / docked 两个互斥样式。
     */
    public static final String LEGACY_GLASS_BAR = "glass_bottom_bar";

    /** 整型参数默认值，顺序与 INT_KEYS 一致。 */
    public static final int[] INT_DEFAULTS = {55, 1, 24, 12, 16, 0, 50, 100};

    public static int intDefault(String key) {
        for (int i = 0; i < INT_KEYS.length; i++) if (INT_KEYS[i].equals(key)) return INT_DEFAULTS[i];
        return 0;
    }

    public static boolean isIntKey(String key) {
        if (key == null) return false;
        for (String k : INT_KEYS) if (k.equals(key)) return true;
        return false;
    }

    /**
     * 把滑块值钳到该键的合法区间。
     *
     * 与 SettingsKeys.clamp 同源：GUI 侧写入前钳一次，
     * 宿主侧读取时再钳一次——两边都做，避免任何一侧漏掉越界值。
     */
    public static int clampInt(String key, int value) {
        switch (key) {
            case "glass_bar_alpha":  return Math.max(10, Math.min(90, value));
            case "glass_bar_blur":   return Math.max(0, Math.min(2, value));
            case "glass_bar_corner": return Math.max(0, Math.min(36, value));
            case "glass_bar_margin": return Math.max(0, Math.min(32, value));
            case "glass_bar_bottom": return Math.max(0, Math.min(40, value));
            case "glass_bar_style": return Math.max(0, Math.min(2, value));
            case "glass_bar_bounce": return Math.max(0, Math.min(100, value));
            case "glass_bar_width": return Math.max(50, Math.min(100, value));
            default: return value;
        }
    }

    public static final String[] EXTRA_KEYS = {
        "enabled","in_app_entry","debug","trace_ab","local_vip",
        "hide_welfare_float","hide_post_float","hide_drama_resume","auto_expand_shelf","hide_coin",
        "hide_reader_video_entry","hide_reader_audio",
        "hide_banner","hide_reader_ads","hide_reader_live_popup","hide_splash_ad","hide_games",
        "hide_status_bar",
        "unlimited_listen","unlimited_autoread",
        "hide_search_bar","hide_widget_prompt",
        "glass_bottom_bar_swipe",
        "glass_bottom_bar_floating","glass_bottom_bar_docked","glass_top_bar","refresh_confirm",
        "hide_income","hide_frequent_games","show_book_id","book_id_search",
        "force_open_removed","raw_read_counts","hide_update_popup",
        "glass_global_enabled","glass_global_card_text","glass_global_panels","glass_global_rank_cover",
        "glass_global_store_liquid","glass_global_mine_liquid","rank_equal_columns","glass_rank_edge_feather"
    };
    public static final String LOG_GUIDE = "开启模块中的‘输出命中日志’，确认配置已同步。强行停止并重新打开番茄，在问题页面复现后，进入 LSPosed → 日志 → 模块日志，导出并保存日志。\n\n日志可能包含个人信息，分享前请检查并遮盖敏感内容。";
    /**
     * 受支持的宿主版本。
     *
     * 开屏广告（pages.splash）从 7.3.9.32 就存在，7.4.1.32 起重构并增强了内部实现，
     * 因此适配区间覆盖整条 7.3.9 → 7.4.5 线，而不是只对 7.4.x 生效。
     * 每个版本都用真实 APK 的类表核对过关键 Hook 点是否存在。
     */
    public static final long[] SUPPORTED_VERSION_CODES = {73932L, 74132L, 74332L, 74532L};
    public static final String[] SUPPORTED_VERSION_NAMES = {"7.3.9.32", "7.4.1.32", "7.4.3.32", "7.4.5.32"};

    public static boolean supported(long versionCode, String versionName) {
        for (long code : SUPPORTED_VERSION_CODES) if (code == versionCode) return true;
        return false;
    }

    public static final String[] TAB_KEYS = {"hide_store", "hide_series", "hide_earn", "hide_shelf", "hide_mine"};
    public static final String[] TAB_LABELS = {"书城", "短剧", "赚钱", "书架", "我的"};
    public static final String[] CHANNEL_LABELS={"推荐","小说","听书","看剧","经典","短篇","视频","知识","漫画","新书","商城"};
    public static final String[] CHANNEL_KEYS={"channel_recommend","channel_novel","channel_audio","channel_drama","channel_classic","channel_story","channel_video","channel_knowledge","channel_comic","channel_new","channel_shop"};
    public static final String[] MINE_LABELS={"我的订单","浏览记录","我的消息","创作中心","我的预约","我的点赞","我的下载","游戏中心","反馈与帮助","会员中心"};
    public static final String[] MINE_KEYS={"mine_order","mine_history","mine_message","mine_creator","mine_reservation","mine_like","mine_download","mine_game","mine_help","mine_vip"};
    private Rules() {}
    /**
     * 全部受管开关键名（三组 + 启动页 + 平板 + 分组外）。
     * 后备通道与内容提供者按此列表导出，顺序不影响语义。
     * 只影响本模块自己界面外观的键（glass_level）刻意不在其中。
     */
    public static String[] allSettingKeys() {
        List<String> keys = new ArrayList<>();
        Collections.addAll(keys, TAB_KEYS);
        Collections.addAll(keys, CHANNEL_KEYS);
        Collections.addAll(keys, MINE_KEYS);
        Collections.addAll(keys, StartupTabRules.KEYS);
        Collections.addAll(keys, TabletRules.KEYS);
        Collections.addAll(keys, EXTRA_KEYS);
        Collections.addAll(keys, INT_KEYS);
        return keys.toArray(new String[0]);
    }
    public static boolean defaultOn(String key){
        return "in_app_entry".equals(key)
            || "glass_global_card_text".equals(key)
            || "glass_global_panels".equals(key);
    }

    public static String tabKey(String name) {
        if (name == null) return null;
        switch (name) {
            case "BookStore": return "hide_store";
            case "LuckyBenefit": return "hide_earn";
            case "Bookshelf": case "BookShelf": return "hide_shelf";
            case "MyProfile": return "hide_mine";
            case "VideoSeriesFeedTab": return "hide_series";
            default: return null;
        }
    }

    public static boolean allTabsHidden(Map<String, ?> values) {
        for (String key : TAB_KEYS) if (!Boolean.TRUE.equals(values.get(key))) return false;
        return true;
    }

    public static boolean allChannelsHidden(Map<String,?> values){for(String key:CHANNEL_KEYS)if(!Boolean.TRUE.equals(values.get(key)))return false;return true;}

    public static String channelKey(String title,int type){
        if(title!=null)for(int i=0;i<CHANNEL_LABELS.length;i++)if(CHANNEL_LABELS[i].equals(title.trim()))return CHANNEL_KEYS[i];
        switch(type){
            case 2:return CHANNEL_KEYS[0];case 25:return CHANNEL_KEYS[1];case 5:return CHANNEL_KEYS[2];
            case 8:case 17:case 37:case 38:return CHANNEL_KEYS[3];
            case 11:case 6:return CHANNEL_KEYS[4];case 13:return CHANNEL_KEYS[5];
            case 4:case 16:return CHANNEL_KEYS[6];case 7:case 12:return CHANNEL_KEYS[7];
            case 9:case 32:return CHANNEL_KEYS[8];case 3:case 31:return CHANNEL_KEYS[9];case 15:return CHANNEL_KEYS[10];
            default:return null;
        }
    }

    public static String mineKey(String title,String type){
        if(title!=null)for(int i=0;i<MINE_LABELS.length;i++)if(MINE_LABELS[i].equals(title.trim()))return MINE_KEYS[i];
        if(type==null)return null;
        switch(type){
            case "MyOrder":case "AD_ORDER":return "mine_order";
            case "ReadHistory":case "ReadHistoryAll":case "ReadHistoryVideo":case "READING_HISTORY":case "MINE_HISTORY_CARD":return "mine_history";
            case "MyMessage":case "MY_MESSAGE":return "mine_message";
            case "WriterCenter":case "CreatorCenter":case "CreationCenter":case "WRITER_CENTER":case "CREATION_CENTER":return "mine_creator";
            case "VideoSubscribe":case "VIDEO_BOOKING":return "mine_reservation";
            case "MyDigg":case "DiggContent":case "DiggVideo":case "VIDEO_LIKE":case "DIGG_CONTENT":case "MINE_LIKE":return "mine_like";
            case "Download":case "BOOK_DOWNLOAD":return "mine_download";
            case "GameCenter":case "MINI_GAME":return "mine_game";
            case "FeedbackHelp":case "FEEDBACK_AND_HELP":return "mine_help";
            case "MemberCenter":case "VIP":return "mine_vip";
            case "GoldBalance":case "POLARIS":case "CREATION_INCOME":return "hide_income";
            default:return null;
        }
    }

    public static String viewRule(String name) {
        switch (name) {
            case "com.bytedance.ug.sdk.kmp.novel.pendant.w":return "hide_welfare_float";
            case "com.bytedance.ug.sdk.kmp.novel.pendant.widget.InnerPendantView":
            case "com.bytedance.ug.sdk.kmp.novel.pendant.widget.PendantHideView":
            case "com.bytedance.ug.sdk.luckydog.task.tasktimer.view.LuckyCommonPendantView":return "hide_welfare_float";
            case "com.dragon.read.ad.banner.ui.BaseBannerView":
            case "com.dragon.read.reader.extend.banner.BannerBackgroundView":
            case "com.dragon.read.reader.extend.banner.CoverView":return "hide_banner";
            case "com.dragon.read.ad.brand.ui.BrandChapterFrontAdView":
            case "com.dragon.read.ad.brand.ui.BrandChapterFrontTopView":
            case "com.dragon.read.ad.cartoon.ui.CartoonCardAdView": return "hide_reader_ads";
            case "com.dragon.read.component.biz.impl.bookshelf.polaris.BookshelfCoinBarView":
            case "com.dragon.read.widget.GoldCoinStickerView":
            case "com.dragon.read.goldcoinbox.widget.GoldCoinBoxCircleTipView":
            case "com.dragon.read.goldcoinbox.widget.GoldCoinBoxTextView":
            case "com.dragon.read.ad.coinreward.ui.BaseCoinRewardView": return "hide_coin";
            case "com.dragon.read.ad.chapterend.view.ChapterEndGameCenterItemView": return "hide_games";
            default: return null;
        }
    }
}
