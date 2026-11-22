package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Source contracts; device navigation is verified separately. */
public final class SettingsUi600Test {
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static String source(String name) throws Exception {
        return Files.readString(Path.of("app/src/main/kotlin/io/github/mianxyz/fanqiecustomizer/ui", name));
    }
    public static void main(String[] args) throws Exception {
        String spec = source("settings/Spec.kt"), icons = source("icons/AppIcons.kt");
        Map<String, String> bindings = new LinkedHashMap<>();
        Matcher declarations = Pattern.compile("private val (cat\\w+) = ([\\s\\S]*?)(?=\\n    (?:private val|val featureCards))").matcher(spec);
        while (declarations.find()) {
            Matcher icon = Pattern.compile("AppIcons\\.(\\w+)").matcher(declarations.group(2));
            check(icon.find(), "explicit category icon: " + declarations.group(1));
            bindings.put(declarations.group(1), icon.group(1));
        }
        String cards = spec.substring(spec.indexOf("val featureCards:"), spec.indexOf("/** 按 id 查分类"));
        Matcher refs = Pattern.compile("\\bcat\\w+\\b").matcher(cards);
        Set<String> names = new HashSet<>(), used = new HashSet<>();
        int count = 0;
        while (refs.find()) {
            String ref = refs.group(), name = bindings.get(ref);
            check(used.add(ref), "category appears once: " + ref);
            check(name != null && names.add(name), "unique reachable category icon: " + ref + " / " + name);
            check(icons.contains("val " + name + ": ImageVector"), "icon exists: " + name);
            count++;
        }
        check(count == 15, "all 15 reachable categories inspected: " + count);
        Matcher paths = Pattern.compile("categoryOutline\\(\"(\\w+)\", \"([^\"]+)\"\\)").matcher(icons);
        Set<String> shapes = new HashSet<>();
        int outlines = 0;
        while (paths.find()) {
            check(names.contains(paths.group(1)), "new outline reachable: " + paths.group(1));
            check(shapes.add(paths.group(2)), "unique path geometry: " + paths.group(1));
            outlines++;
        }
        check(outlines == 8, "eight duplicate categories redrawn");
        check(icons.contains("viewportWidth = 24f, viewportHeight = 24f") && icons.contains("strokeLineWidth = 1.5f")
            && icons.contains("strokeLineCap = StrokeCap.Round") && icons.contains("strokeLineJoin = StrokeJoin.Round")
            && icons.contains("fill = null"), "consistent unfilled rounded outline style");
        String shell = source("settings/SettingsScreen.kt"), pages = source("settings/Pages.kt");
        int owner = shell.indexOf("val tabListStates = listOf("), boundary = shell.indexOf("details.AnimatedContent(");
        check(owner >= 0 && owner < boundary, "overview states outside AnimatedContent");
        String states = shell.substring(owner, shell.indexOf("val hazeState", owner));
        check(states.split("rememberLazyListState\\(\\)", -1).length - 1 == 4, "independent saved state for four tabs");
        check(shell.contains("index, tabListStates[index], bridge"), "pass correct tab state");
        String tab = shell.substring(shell.indexOf("private fun TabPageContent("));
        check(tab.contains("listState: LazyListState") && tab.contains("state = listState") && !tab.contains("rememberLazyListState()"), "tab consumes external state");
        String detail = pages.substring(pages.indexOf("fun FeatureDetailScreen("), pages.indexOf("// ─", pages.indexOf("fun FeatureDetailScreen(")));
        check(detail.contains("LazyColumn(") && !detail.contains("tabListStates") && !detail.contains("state = listState"), "detail uses independent fresh default state");
        check(shell.contains("closeDetail()") && shell.contains("onToggle, closeDetail)"), "system and top back share detail close");
        check(!shell.contains("scrollToItem(") && !shell.contains("animateScrollToItem("), "no forced overview reset");
        System.out.println("SettingsUi600Test passed (15 unique icons, 8 unique outlines, retained overview/fresh detail source contracts)");
    }
}
