package io.github.mianxyz.fanqiecustomizer;

import java.util.HashMap;
import java.util.Map;

/**
 * 免 Root 后备通道的判定逻辑回归。
 *
 * 这里锁定一次真实事故：provider / receiver 在内嵌框架里与宿主同进程，
 * 读的是番茄自己的空 prefs。若按默认值补齐，它会返回一份「看起来非空」
 * 的全默认配置，调用方据此认为已拿到配置并跳过文件后备 ——
 * 结果所有开关退回默认（enabled=false），表现为「模块完全没生效」。
 */
public final class FallbackChannelTest {

    public static void main(String[] args) {
        // 空配置必须是「没有配置」，而不是「一份全默认的配置」。
        Map<String, Boolean> hostSideEmpty = new HashMap<>();
        check(!SettingsKeys.hasUserConfig(hostSideEmpty),
            "empty host prefs must not be treated as user config");

        // 只有零散杂项、没有主开关，同样不算用户配置。
        Map<String, Boolean> partial = new HashMap<>();
        partial.put("hide_games", Boolean.TRUE);
        check(!SettingsKeys.hasUserConfig(partial),
            "config without the master switch is not authoritative");

        // 文件后备读出来的完整配置（含 enabled）才算。
        Map<String, Boolean> fromFile = new HashMap<>();
        fromFile.put("enabled", Boolean.TRUE);
        fromFile.put("hide_games", Boolean.FALSE);
        check(SettingsKeys.hasUserConfig(fromFile),
            "a complete file config is authoritative");

        // 用户主动关闭也必须被尊重：enabled=false 仍然是「读到了配置」。
        Map<String, Boolean> disabled = new HashMap<>();
        disabled.put("enabled", Boolean.FALSE);
        check(SettingsKeys.hasUserConfig(disabled),
            "explicitly disabled is still a loaded configuration");

        // 过滤规则不得让非受管键混进配置。
        // 直接测 SettingsKeys.accept：android.jar 的 Bundle 是 stub，
        // 在 JVM 上无法实例化，逻辑因此与 Android 类型解耦。
        check(SettingsKeys.accept("enabled", true) != null, "managed key passes through");
        check(SettingsKeys.accept("glass_level", true) == null,
            "glass level stays local and never enters the fallback path");
        check(SettingsKeys.accept("not_a_module_key", true) == null,
            "unknown keys are dropped");

        System.out.println("FallbackChannelTest passed (4 acceptance rules, 3 filter rules)");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}