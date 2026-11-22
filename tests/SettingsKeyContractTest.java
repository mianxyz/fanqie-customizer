package io.github.mianxyz.fanqiecustomizer;

import java.util.*;

/**
 * Guard the one invariant that silently breaks the module if violated:
 * every preference key the Compose UI can write must pass
 * {@link PrivacyRules#settingKey}, otherwise SettingsApp.sync() drops it
 * and the toggle appears to save but never reaches the hooked process.
 *
 * The key list below is the authoritative mirror of
 * app/src/main/kotlin/.../ui/settings/Spec.kt (Catalog + StartupEntry +
 * TabletEntry) plus ConfigBridge.STARTUP_KEYS.
 */
public final class SettingsKeyContractTest {

    /**
     * Every integer parameter surfaced by the Compose UI as a slider.
     *
     * 与 UI_KEYS 分开维护：两者走同一条同步通道，但类型不同，
     * 契约要求它们各自与 Java 侧 Rules.INT_KEYS / EXTRA_KEYS 完全一致。
     */
    private static final String[] UI_INT_KEYS = {
        "glass_bar_alpha", "glass_bar_blur", "glass_bar_corner",
        "glass_bar_margin", "glass_bar_bottom", "glass_bar_style", "glass_bar_width", "glass_bar_bounce",
    };

    /** Every key surfaced by the Compose UI as a boolean toggle. */
    private static final String[] UI_KEYS = {
        // dashboard
        "enabled",
        // startup tab (single-choice)
        "start_store", "start_series", "start_earn", "start_shelf", "start_mine",
        // bottom navigation
        "hide_store", "hide_series", "hide_earn", "hide_shelf", "hide_mine",
        // bookmall channels
        "channel_recommend", "channel_novel", "channel_audio", "channel_drama",
        "channel_classic", "channel_story", "channel_video", "channel_knowledge",
        "channel_comic", "channel_new", "channel_shop",
        // floating entries
        "hide_welfare_float", "hide_post_float", "hide_drama_resume",
        // shelf
        "auto_expand_shelf", "hide_coin",
        // reader
        "hide_reader_video_entry", "hide_reader_audio",
        "hide_status_bar", "unlimited_listen", "unlimited_autoread",
        "glass_bottom_bar_floating", "glass_bottom_bar_docked",
        "glass_top_bar", "refresh_confirm",
        "glass_global_enabled", "glass_global_card_text", "glass_global_panels", "glass_global_rank_cover",
        "glass_global_store_liquid", "glass_global_mine_liquid", "rank_equal_columns", "glass_rank_edge_feather",
        "hide_search_bar", "hide_widget_prompt",
        "glass_bottom_bar_swipe",
        "hide_banner", "hide_reader_ads", "hide_reader_live_popup", "hide_splash_ad", "hide_games",
        // mine
        "hide_income", "hide_frequent_games",
        "mine_order", "mine_history", "mine_message", "mine_creator",
        "mine_reservation", "mine_like", "mine_download", "mine_game", "mine_help", "mine_vip",
        // tablet
        "pad_video_creation", "pad_interest", "pad_notes", "pad_seen_people",
        "pad_guide", "pad_wallet", "pad_activity", "pad_public_welfare", "pad_urge_update",
        // tools / diagnostics
        "show_book_id", "book_id_search", "force_open_removed", "raw_read_counts",
        "hide_update_popup", "debug", "trace_ab", "local_vip", "in_app_entry",
    };

    public static void main(String[] args) {
        for (String key : UI_KEYS) {
            check(PrivacyRules.settingKey(key), "UI key must be synchronizable: " + key);
        }

        // 反向检查：Java 白名单里声明的每个键都必须是受管键或已退役键，
        // 避免白名单悄悄漂移出 UI 之外而无人发现。
        check(PrivacyRules.settingKey("enabled"), "master switch is synchronizable");
        check(!PrivacyRules.settingKey("glass_level"),
            "glass level stays local: it only affects this module's own settings UI");
        check(!PrivacyRules.settingKey("__launcher_hidden__"),
            "launcher visibility is PackageManager state, never a preference");

        // 默认值必须与 Rules.defaultOn 完全一致（Compose 侧 Catalog.defaultOn 的镜像）。
        // 原有默认值保留；新增两个全局玻璃子项默认开启，主开关默认关闭。
        // hide_reader_live_popup 早期默认开启（会替用户屏蔽直播弹窗），
        // 现已改为默认关闭——全新安装不该带着一个用户没选过的屏蔽项。
        check(Rules.defaultOn("in_app_entry"), "in-app book tools entry defaults on");
        check(!Rules.defaultOn("hide_reader_live_popup"),
            "reader live popup is opt-in, never on by default");
        for (String key : UI_KEYS) {
            if (key.equals("in_app_entry") || key.equals("glass_global_card_text")
                    || key.equals("glass_global_panels")) {
                check(Rules.defaultOn(key), "default-on key: " + key);
                continue;
            }
            check(!Rules.defaultOn(key), "opt-in default for " + key);
        }

        // ── 免 Root 后备通道 ──────────────────────────────────────────
        // 后备通道按 Rules.allSettingKeys() 导出，它必须与 UI 键集合完全一致：
        // 少一个键，内嵌框架下那个开关就永远不会同步；多一个键，就等于
        // 悄悄把非开关项暴露给宿主。
        // 导出集合 = 布尔键 + 整型键，必须与 UI 暴露的两组键完全一致。
        Set<String> exported = new TreeSet<>(Arrays.asList(Rules.allSettingKeys()));
        check(exported.size() == 90, "88 retained plus independent tablet urge key");
        Set<String> ui = new TreeSet<>(Arrays.asList(UI_KEYS));
        ui.addAll(Arrays.asList(UI_INT_KEYS));
        Set<String> missing = new TreeSet<>(ui);
        missing.removeAll(exported);
        Set<String> extra = new TreeSet<>(exported);
        extra.removeAll(ui);
        check(missing.isEmpty(), "fallback channel must export every UI key, missing=" + missing);
        check(extra.isEmpty(), "fallback channel must not export unknown keys, extra=" + extra);
        check(exported.size() == UI_KEYS.length + UI_INT_KEYS.length,
            "fallback export is exactly the UI key set (" + exported.size() + ")");

        // ── 整型参数（滑块）契约 ─────────────────────────────────────
        // UI 的整型键必须与 Rules.INT_KEYS 一一对应，否则滑块同步会被丢弃。
        check(new TreeSet<>(Arrays.asList(UI_INT_KEYS))
                        .equals(new TreeSet<>(Arrays.asList(Rules.INT_KEYS))),
            "UI slider keys must equal Rules.INT_KEYS");
        check(Rules.INT_DEFAULTS.length == Rules.INT_KEYS.length,
            "every INT_KEY has a default value");
        for (String key : Rules.INT_KEYS) {
            check(PrivacyRules.settingKey(key), "int key passes settingKey: " + key);
            check(Rules.isIntKey(key), "int key recognized by isIntKey: " + key);
            int def = Rules.intDefault(key);
            // 默认值必须落在合法区间内，否则首次启动就会被钳制走样。
            check(Rules.clampInt(key, def) == def, "default within range: " + key);
            // 越界值必须被钳制，不能把非法值写进宿主。
            check(Rules.clampInt(key, -999) != -999, "clamps below range: " + key);
            check(Rules.clampInt(key, 99999) != 99999, "clamps above range: " + key);
        }
        check(Rules.intDefault("glass_bar_width") == 100, "floating width defaults to legacy geometry");
        check(Rules.clampInt("glass_bar_width", -1) == 50 && Rules.clampInt("glass_bar_width", 101) == 100, "width bounds 50..100");
        check(Rules.clampInt("glass_bar_width", 75) == 75, "width interior preserved");
        // 圆角必须覆盖 0（纯直角）~36（全胶囊）：0 是有效档位，不能被钳成正数。
        check(Rules.clampInt("glass_bar_corner", 0) == 0, "corner 0dp (square) allowed");
        check(Rules.clampInt("glass_bar_corner", 36) == 36, "corner 36dp allowed");
        check(Rules.clampInt("glass_bar_corner", -5) == 0, "corner clamps below to 0");
        check(Rules.clampInt("glass_bar_corner", 99) == 36, "corner clamps above to 36");
        for (String key : new String[]{"glass_bottom_bar_swipe", "hide_widget_prompt"}) {
            check(PrivacyRules.settingKey(key), "new key passes settingKey: " + key);
            check(Rules.defaultOn(key) == false, "new key defaults off: " + key);
        }
        // ── 旧底栏开关迁移 ───────────────────────────────────────────
        // glass_bottom_bar 已退役：它不能出现在任何导出列表里，
        // 但老配置开启时必须被翻译成新的「贴底」样式，否则升级等于删功能。
        check(!exported.contains(Rules.LEGACY_GLASS_BAR), "legacy bar key is not exported");
        Map<String, Boolean> legacyOn = new HashMap<>();
        legacyOn.put(Rules.LEGACY_GLASS_BAR, Boolean.TRUE);
        SettingsKeys.migrate(legacyOn);
        check(Boolean.TRUE.equals(legacyOn.get("glass_bottom_bar_docked")),
            "legacy on -> docked style");
        check(!legacyOn.containsKey(Rules.LEGACY_GLASS_BAR), "legacy key removed after migration");
        // 已经是新键的配置不能被迁移覆盖。
        Map<String, Boolean> alreadyNew = new HashMap<>();
        alreadyNew.put(Rules.LEGACY_GLASS_BAR, Boolean.TRUE);
        alreadyNew.put("glass_bottom_bar_floating", Boolean.TRUE);
        SettingsKeys.migrate(alreadyNew);
        check(!alreadyNew.containsKey("glass_bottom_bar_docked"),
            "migration never overwrites an explicit new choice");

        // 布尔键不能混进整型集合，反之亦然。
        for (String key : Rules.INT_KEYS) {
            check(!Arrays.asList(UI_KEYS).contains(key), "int key not in boolean UI list: " + key);
        }
        for (String key : UI_KEYS) {
            check(!Rules.isIntKey(key), "boolean key never treated as int: " + key);
        }

        // 每个导出键都必须过白名单，否则 SettingsKeys.dump/collect 会把它丢掉。
        for (String key : exported) {
            check(PrivacyRules.settingKey(key), "exported key must pass settingKey: " + key);
        }
        // glass_level 只影响模块自身界面外观，必须留在本地、不进后备通道。
        check(!exported.contains("glass_level"), "glass level never leaves the module");

        // 至少保留一个入口 / 频道的两条界面约束仍然由 Rules 提供。
        Map<String, Object> allHidden = new HashMap<>();
        for (String key : Rules.TAB_KEYS) allHidden.put(key, Boolean.TRUE);
        check(Rules.allTabsHidden(allHidden), "all-hidden tab set detected");
        allHidden.put(Rules.TAB_KEYS[0], Boolean.FALSE);
        check(!Rules.allTabsHidden(allHidden), "a single visible tab clears the guard");

        // 受支持版本必须真的能通过 supported() 判定，否则放开版本门等于没放。
        for (long code : Rules.SUPPORTED_VERSION_CODES) {
            check(Rules.supported(code, ""), "supported version accepted: " + code);
        }
        check(!Rules.supported(73931L, ""), "unknown version rejected");

        // 宿主包名：国内版与 Play 版都必须被识别，其他一律拒绝。
        check(Rules.isTargetPackage("com.dragon.read"), "domestic package accepted");
        check(Rules.isTargetPackage("com.dragon.read.oversea.gp"), "play package accepted");
        check(!Rules.isTargetPackage("com.dragon.read.oversea"), "partial package name rejected");
        check(!Rules.isTargetPackage(null), "null package rejected");
        check(Rules.SUPPORTED_VERSION_CODES.length == Rules.SUPPORTED_VERSION_NAMES.length,
            "supported version code/name tables stay in sync");

        System.out.println("SettingsKeyContractTest passed (" + UI_KEYS.length
            + " UI keys verified, " + exported.size() + " exported to fallback channel)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
