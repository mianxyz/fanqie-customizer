package io.github.mianxyz.fanqiecustomizer;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 已识别控件清单（中文名 + 技术标识）。
 *
 * 用途：让用户看清「模块到底认出了哪些控件」，而不是盲猜开关。
 * 这里记录的是**本模块已经适配并确认过的**控件，不是宿主全部控件——
 * 未适配的控件不在其中，这一点必须如实呈现，不能假装覆盖了整页。
 *
 * 写入走 commit()：清单在 hook 进程里产生、在设置页里读取，
 * 两边不是同一进程，异步落盘会丢条目。
 */
public final class ControlCatalog {
    public static final String PREFS = "control_catalog";
    private static final int MAX_ENTRIES = 200;

    private ControlCatalog() {}

    /** 一条记录：技术标识 + 中文名。 */
    public static final class Entry {
        public final String id;
        public final String label;
        public final String page;
        Entry(String id, String label, String page) { this.id = id; this.label = label; this.page = page; }
    }

    /** 模块内置的已适配控件表：id = 类名或规则名，label = 中文名。 */
    private static final String[][] KNOWN = {
        // 底部导航
        {"com.dragon.read.widget.BottomTabBarLayout", "底部导航栏", "导航"},
        {"hide_store", "书城入口", "导航"},
        {"hide_series", "短剧入口", "导航"},
        {"hide_earn", "赚钱入口", "导航"},
        {"hide_shelf", "书架入口", "导航"},
        {"hide_mine", "我的入口", "导航"},
        // 书城
        {"com.dragon.read.component.biz.impl.NewBookMallFragment", "书城页面", "书城"},
        {"channel_recommend", "推荐频道", "书城"},
        {"channel_novel", "小说频道", "书城"},
        {"channel_audio", "听书频道", "书城"},
        {"channel_drama", "看剧频道", "书城"},
        {"channel_classic", "经典频道", "书城"},
        {"channel_story", "短篇频道", "书城"},
        {"channel_video", "视频频道", "书城"},
        {"channel_knowledge", "知识频道", "书城"},
        {"channel_comic", "漫画频道", "书城"},
        {"channel_new", "新书频道", "书城"},
        {"channel_shop", "商城频道", "书城"},
        // 阅读
        {"com.dragon.read.reader.ad.ReaderAdManager", "阅读广告管理器", "阅读"},
        {"com.dragon.read.pop.PopProxy", "弹窗调度器", "阅读"},
        {"hide_banner", "原生横幅广告", "阅读"},
        {"hide_reader_ads", "阅读广告组件", "阅读"},
        {"hide_reader_live_popup", "阅读页直播弹窗", "阅读"},
        {"hide_reader_video_entry", "章末免广告入口", "阅读"},
        {"hide_reader_audio", "快捷听书按钮", "阅读"},
        {"hide_games", "章末小游戏", "阅读"},
        // 我的
        {"com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2", "我的页面", "我的"},
        {"hide_income", "收益记录", "我的"},
        {"hide_frequent_games", "常玩游戏", "我的"},
        // 悬浮
        {"hide_welfare_float", "福利浮图", "悬浮"},
        {"hide_post_float", "发帖快捷按钮", "悬浮"},
        {"hide_drama_resume", "短剧续播浮窗", "悬浮"},
        // 开屏与系统
        {"com.dragon.read.pages.splash.AttributionManager", "开屏广告判定", "开屏"},
        {"com.dragon.read.pages.splash.SplashActivity", "启动页", "开屏"},
        {"com.dragon.read.ad.openingscreenad.OpeningScreenADActivity", "开屏广告页", "开屏"},
        {"hide_splash_ad", "启动开屏广告", "开屏"},
        {"hide_status_bar", "系统状态栏", "系统"},
    };

    /** 全部内置条目。 */
    public static List<Entry> all() {
        List<Entry> out = new ArrayList<>(KNOWN.length);
        for (String[] row : KNOWN) out.add(new Entry(row[0], row[1], row[2]));
        return out;
    }

    /** 运行期实际命中过的条目（由 hook 进程写入）。 */
    public static Set<String> observed(Context context) {
        Set<String> out = new LinkedHashSet<>();
        if (context == null) return out;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = prefs.getString("hits", "");
            if (raw != null && !raw.isEmpty())
                Collections.addAll(out, raw.split("\n"));
        } catch (Throwable ignored) {
            // 清单只是诊断辅助，读失败不影响开关。
        }
        return out;
    }

    /** hook 进程记录一次命中；去重并限长，避免无界增长。 */
    public static void record(Context context, String id) {
        if (context == null || id == null) return;
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = prefs.getString("hits", "");
            Set<String> hits = new LinkedHashSet<>();
            if (raw != null && !raw.isEmpty()) Collections.addAll(hits, raw.split("\n"));
            if (!hits.add(id)) return;
            while (hits.size() > MAX_ENTRIES) {
                String first = hits.iterator().next();
                hits.remove(first);
            }
            prefs.edit().putString("hits", String.join("\n", hits)).commit();
        } catch (Throwable ignored) {
            // 同上：诊断功能不参与主流程。
        }
    }

    /** 「我的」九宫格与平板入口的中文名来自既有规则表。 */
    public static List<Entry> grouped() {
        List<Entry> out = new ArrayList<>();
        for (int i = 0; i < Rules.MINE_KEYS.length; i++)
            out.add(new Entry(Rules.MINE_KEYS[i], Rules.MINE_LABELS[i], "我的"));
        for (String key : TabletRules.KEYS)
            out.add(new Entry(key, labelForTablet(key), "平板"));
        return out;
    }

    private static String labelForTablet(String key) {
        for (int i = 0; i < TabletRules.KEYS.length; i++)
            if (TabletRules.KEYS[i].equals(key)) return TabletRules.LABELS[i];
        return key;
    }
}
