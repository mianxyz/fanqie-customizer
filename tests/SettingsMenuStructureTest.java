package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

public final class SettingsMenuStructureTest {
    static Set<String> keys(String text) {
        Set<String> keys = new TreeSet<>();
        Matcher m = Pattern.compile("(?:ToggleSpec\\s*\\(\\s*|key\\s*=\\s*)\"([^\"]+)\"").matcher(text);
        while (m.find()) keys.add(m.group(1));
        return keys;
    }
    public static void main(String[] args) throws Exception {
        String current = Files.readString(Path.of("app/src/main/kotlin/io/github/mianxyz/fanqiecustomizer/ui/settings/Spec.kt"));
        String old = Files.readString(Path.of("tests/fixtures/Spec-5.3.51.kt.txt"));
        Set<String> added = Set.of("glass_global_enabled", "glass_global_card_text", "glass_global_panels", "glass_global_rank_cover", "glass_global_store_liquid", "glass_global_mine_liquid", "rank_equal_columns", "glass_rank_edge_feather");
        Set<String> previous = keys(old), next = keys(current);
        previous.remove("hide_rank_recommend");
        check(previous.size() >= 35, "inventory parser coverage: " + previous.size());
        check(next.containsAll(added), "all six global keys declared");
        for (String key : added) {
            Matcher occurrences = Pattern.compile("ToggleSpec\\s*\\(\\s*\"" + Pattern.quote(key) + "\"").matcher(current);
            int count = 0;
            while (occurrences.find()) count++;
            check(count == 1, "global key declared exactly once: " + key);
        }
        next.remove("glass_bar_style");
        next.remove("glass_bar_width");
        next.remove("glass_bar_bounce");
        int widthStart = current.indexOf("key = \"glass_bar_width\"");
        check(widthStart >= 0 && current.indexOf("key = \"glass_bar_width\"", widthStart + 1) < 0, "width slider declared once");
        String width = current.substring(widthStart, current.indexOf("\n            ),", widthStart));
        check(width.contains("title = \"悬浮底栏长度\""), "width title");
        check(width.contains("调节悬浮玻璃底栏占屏幕宽度的比例（默认 100%）。悬浮玻璃与液态玻璃均生效。"), "width original description");
        check(width.contains("min = 50, max = 100, default = 100, unit = \"%\""), "width slider range/default/unit");
        check(width.contains("visibleWhen = \"glass_bottom_bar_floating\""), "floating-only width visibility");
        check(current.contains("+ catFloating.sliders"), "width reachable from bottom navigation group");
        String mall = current.substring(current.indexOf("private val catMallLayout"), current.indexOf("private val catInteraction"));
        check(mall.contains("setOf(\"hide_search_bar\", \"hide_widget_prompt\")"), "mall retains search and widget controls");
        next.removeAll(added);
        check(previous.equals(next), "settings key inventory changed: " + previous + " / " + next);
        for (String id : new String[]{"glass_nav", "top_visual", "mall_layout", "interaction", "floating", "global_glass"}) {
            check(current.contains("\"" + id + "\""), "missing route " + id);
        }
        Matcher category = Pattern.compile("private val catGlobalGlass\\s*=\\s*CategorySpec\\(([\\s\\S]*?)\\n    \\)").matcher(current);
        check(category.find(), "global glass category declaration");
        String global = category.group(1);
        check(keys(global).equals(added), "global glass category owns exactly six keys");
        check(global.indexOf("rank_equal_columns") > global.indexOf("glass_global_rank_cover"), "rank columns follows rank cover");
        check(global.contains("推荐榜底部模糊") && global.contains("为推荐榜底部启用固定毛玻璃背景，自动适配日间与夜间。"), "rank cover precise copy");
        check(global.contains("书城搜索与分类液态玻璃") && global.contains("为书城顶部搜索框与分类按钮启用与底栏同款的液态玻璃效果（不影响推荐流）。"), "store liquid precise copy");
        check(global.contains("我的页顶部组件液态玻璃") && global.contains("为「我的」页顶部搜索框、九宫格与金币福利卡片启用与底栏同款的液态玻璃效果（不影响推荐流）。"), "mine liquid precise copy");
        check(!global.contains("封面生成推荐榜") && !global.contains("封面拼贴"), "rank copy describes fixed frosted glass");
        check(!Rules.defaultOn("glass_global_store_liquid") && !Rules.defaultOn("glass_global_mine_liquid"), "liquid children default off");
        check(!Rules.defaultOn("glass_global_rank_cover"), "rank cover defaults off");
        check(global.contains("id = \"global_glass\""), "global glass route belongs to category");
        Matcher toggles = Pattern.compile("ToggleSpec\\s*\\(\\s*\"([^\"]+)\"([\\s\\S]*?)\\)").matcher(global);
        Map<String, String> dependencies = new TreeMap<>();
        int declarations = 0;
        while (toggles.find()) {
            declarations++;
            Matcher dependency = Pattern.compile("enabledWhen\\s*=\\s*\"([^\"]+)\"").matcher(toggles.group(2));
            String when = dependency.find() ? dependency.group(1) : "";
            check(dependencies.put(toggles.group(1), when) == null, "duplicate global toggle");
        }
        check(declarations == 8, "exactly six global toggle declarations");
        check(dependencies.equals(Map.of("glass_rank_edge_feather", "glass_global_enabled", "rank_equal_columns", "", "glass_global_enabled", "", "glass_global_card_text", "glass_global_enabled", "glass_global_panels", "glass_global_enabled", "glass_global_rank_cover", "glass_global_enabled", "glass_global_store_liquid", "glass_global_enabled", "glass_global_mine_liquid", "glass_global_enabled")), "precise master/child dependencies: " + dependencies);
        check(current.contains("CardSpec(\"界面美化 / 界面修改\", listOf(catGlobalGlass,"), "global glass reachable from appearance card");
        check(Pattern.compile("fun defaultOn\\(key: String\\): Boolean = Rules\\.defaultOn\\(key\\)").matcher(current).find(), "Catalog defaults delegate to Rules");
        check(!Rules.defaultOn("glass_global_enabled"), "master defaults off");
        check(Rules.defaultOn("glass_global_card_text") && Rules.defaultOn("glass_global_panels"), "children default on");
        int[] defaults = {55, 1, 24, 12, 16};
        String[] names = {"glass_bar_alpha", "glass_bar_blur", "glass_bar_corner", "glass_bar_margin", "glass_bar_bottom"};
        for (int i = 0; i < names.length; i++) check(Rules.intDefault(names[i]) == defaults[i], "old default changed: " + names[i]);
        check(Rules.intDefault("glass_bar_style") == 0, "style default remains floating");
        int[] styleInputs = {Integer.MIN_VALUE, -1, 0, 1, 2, 3, Integer.MAX_VALUE};
        int[] styleExpected = {0, 0, 0, 1, 2, 2, 2};
        for (int i = 0; i < styleInputs.length; i++) {
            check(Rules.clampInt("glass_bar_style", styleInputs[i]) == styleExpected[i], "style clamp: " + styleInputs[i]);
        }
        check(current.contains("SliderSpec(key = \"glass_bar_style\", title = \"底栏样式\", min = 0, max = 2, default = 0, options = listOf(\"悬浮玻璃\", \"贴底玻璃\", \"液态玻璃\"))"), "three style options and unchanged default");
        String pages = Files.readString(Path.of("app/src/main/kotlin/io/github/mianxyz/fanqiecustomizer/ui/settings/Pages.kt"));
        check(pages.contains("val floatingStyle = !bridge.get(\"glass_bottom_bar_docked\") && bridge.getInt(\"glass_bar_style\") != 1"), "floating and liquid styles enable floating sliders");
        check(pages.contains("enabled = glassEnabled && (s.visibleWhen != \"glass_bottom_bar_floating\" || floatingStyle)"), "floating slider gate remains connected");
        System.out.println("SettingsMenuStructureTest passed (old inventory/defaults, global page/dependencies/defaults)");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
