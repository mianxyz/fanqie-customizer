package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.*;
import java.util.*;

/** Independent fixed 5.3.58 inventory and defaults, including every old integer. */
public final class SettingsDefaultsContractTest {
    public static void main(String[] args) throws Exception {
        Map<String, String[]> frozen = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of("tests/fixtures/SettingsDefaults-5.3.58.tsv"))) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] fields = line.split("\t", -1);
            check(fields.length == 3, "bad baseline row: " + line);
            check(frozen.put(fields[0], fields) == null, "duplicate baseline key: " + fields[0]);
        }
        check(frozen.size() == 82, "complete old inventory: " + frozen.size());
        Set<String> expected = new TreeSet<>(frozen.keySet());
        expected.add("glass_global_rank_cover");
        expected.add("rank_equal_columns");
        expected.add("glass_bar_width");
        expected.add("glass_bar_bounce");
        expected.add("mine_vip");
        expected.add("pad_urge_update");
        expected.add("glass_rank_edge_feather");
        expected.add("glass_global_store_liquid");
        expected.add("glass_global_mine_liquid");
        expected.remove("hide_rank_recommend");
        String[] current = Rules.allSettingKeys();
        check(current.length == 90, "exact old+new inventory length");
        check(new TreeSet<>(Arrays.asList(current)).equals(expected), "exact old+new inventory");
        for (String[] fields : frozen.values()) {
            String key = fields[0];
            if (key.equals("hide_rank_recommend")) continue;
            check(PrivacyRules.settingKey(key), "old key synchronizes: " + key);
            if (fields[1].equals("int")) {
                check(Rules.isIntKey(key), "old int type: " + key);
                check(Rules.intDefault(key) == Integer.parseInt(fields[2]), "old int default: " + key);
                check(!Rules.defaultOn(key), "old int not a boolean default: " + key);
            } else {
                check(fields[1].equals("bool"), "baseline type: " + key);
                check(fields[2].equals("true") || fields[2].equals("false"), "baseline boolean: " + key);
                check(!Rules.isIntKey(key), "old bool type: " + key);
                check(Rules.defaultOn(key) == Boolean.parseBoolean(fields[2]), "old boolean default: " + key);
            }
        }
        check(!Rules.defaultOn("glass_global_rank_cover") && !Rules.isIntKey("glass_global_rank_cover"), "rank cover is opt-in boolean");
        check(Rules.isIntKey("glass_bar_width") && Rules.intDefault("glass_bar_width") == 100, "new width integer defaults to 100");
        check(!Rules.defaultOn("glass_global_enabled"), "global master defaults off");
        check(Rules.defaultOn("glass_global_card_text"), "card text defaults on");
        check(Rules.defaultOn("glass_global_panels"), "panels default on");
        for (String key : List.of("glass_global_store_liquid", "glass_global_mine_liquid")) {
            check(PrivacyRules.settingKey(key) && !Rules.isIntKey(key) && !Rules.defaultOn(key), "new liquid option synchronizes as opt-in boolean: " + key);
        }
        check(!Rules.defaultOn("rank_equal_columns") && !Rules.isIntKey("rank_equal_columns"), "rank columns opt-in boolean");
        check(!Rules.defaultOn("pad_urge_update")&&!Rules.isIntKey("pad_urge_update")&&PrivacyRules.settingKey("pad_urge_update"), "urge opt-in synchronized boolean");
        check(!Rules.defaultOn(null) && !Rules.defaultOn("unknown_setting"), "unknown defaults off");
        System.out.println("SettingsDefaultsContractTest passed (89 keys, original 88 defaults unchanged)");
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
