package io.github.mianxyz.fanqiecustomizer;

import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.Map;

/**
 * {@link SettingsProvider} 与 {@link SettingsResponder} 共用的序列化。
 *
 * 关键约束：这里**只导出真正存过的键**，绝不拿 {@link Rules#defaultOn} 补齐。
 *
 * 原因：内嵌框架（NPatch 本地模式）会把模块代码与宿主打进同一个 APK，
 * provider / receiver 因此运行在番茄进程里，context 拿到的是
 * com.dragon.read 的私有 prefs——通常是空的。若按默认值补齐，
 * 就会返回一份「看起来非空」的全默认配置，把调用方判断成
 * 「已经拿到配置」，从而跳过真正存有用户开关的共享文件，
 * 表现为「所有开关都退回默认、等于没生效」。
 *
 * 整型参数（滑块）与布尔开关走同一条通道：
 * Bundle 里分别 putBoolean / putInt，读取方按 [PrivacyRules.settingKey]
 * 与 [Rules.isIntKey] 区分类型。
 */
final class SettingsKeys {
    private SettingsKeys() {}

    /** 只导出宿主 prefs 里确实存在的受管键（布尔 + 整型）；空表表示「这里没有配置」。 */
    static Bundle dump(SharedPreferences prefs) {
        Bundle reply = new Bundle();
        if (prefs == null) return reply;
        Map<String, ?> all;
        try {
            all = prefs.getAll();
        } catch (Throwable ignored) {
            return reply;
        }
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            String key = entry.getKey();
            if (!PrivacyRules.settingKey(key)) continue;
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                reply.putBoolean(key, (Boolean) value);
            } else if (value instanceof Integer && Rules.isIntKey(key)) {
                reply.putInt(key, (Integer) value);
            }
        }
        return reply;
    }

    /**
     * 收集布尔配置。
     *
     * 只收布尔：整型参数单独由 [collectInts] 处理，避免一个 Map
     * 同时承载两种类型（那样调用方每次都要做类型判断，容易出错）。
     */
    static void collect(Bundle extras, Map<String, Boolean> target) {
        if (extras == null || target == null) return;
        for (String key : extras.keySet()) {
            if (Rules.isIntKey(key)) continue;
            Boolean value = accept(key, extras.getBoolean(key));
            if (value != null) target.put(key, value);
        }
    }

    /** 收集整型参数。缺省值由调用方按 [Rules.intDefault] 补齐。 */
    static void collectInts(Bundle extras, Map<String, Integer> target) {
        if (extras == null || target == null) return;
        for (String key : extras.keySet()) {
            if (!Rules.isIntKey(key)) continue;
            int value = extras.getInt(key, Rules.intDefault(key));
            Integer accepted = acceptInt(key, value);
            if (accepted != null) target.put(key, accepted);
        }
    }

    /**
     * 单键过滤规则（与 Android 类型解耦，便于单元测试）：
     * 非受管键一律丢弃，受管键保留其值。
     */
    static Boolean accept(String key, boolean value) {
        if (!PrivacyRules.settingKey(key)) return null;
        return value;
    }

    /** 整型键的过滤与范围钳制；非整型键返回 null。 */
    static Integer acceptInt(String key, int value) {
        if (!Rules.isIntKey(key)) return null;
        return clamp(key, value);
    }

    /** 把滑块值钳到合法区间；委托 Rules，保持唯一事实来源。 */
    static int clamp(String key, int value) {
        return Rules.clampInt(key, value);
    }

    /**
     * 判断一份开关是否包含真正的用户配置：
     * 主开关 enabled 被显式存过，才算「有配置」。
     * 只看「非空」会被少量杂项误判。
     */
    /**
     * 把旧单一底栏开关迁移成两个新样式键。
     *
     * 老用户开着 glass_bottom_bar，升级后如果不管，两个新键都是关闭，
     * 底栏玻璃会突然消失——等于升级把功能弄没了。这里按最接近旧行为的
     * 方式落到「贴底」样式（旧版本来就是贴底的）。
     */
    static void migrate(Map<String, Boolean> values) {
        if (values == null) return;
        Boolean legacy = values.remove(Rules.LEGACY_GLASS_BAR);
        if (legacy == null || !legacy) return;
        if (!values.containsKey("glass_bottom_bar_floating")
                && !values.containsKey("glass_bottom_bar_docked")) {
            values.put("glass_bottom_bar_docked", Boolean.TRUE);
        }
    }

    static boolean hasUserConfig(Map<String, Boolean> values) {
        return values != null && values.containsKey("enabled");
    }
}
