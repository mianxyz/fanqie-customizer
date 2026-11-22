package io.github.mianxyz.fanqiecustomizer;

import android.content.Context;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 免 Root 内嵌框架（LSPatch / FPA）的共享配置文件后备通道。
 *
 * 定位：这是 provider / 广播都拿不到值时的最后一层，不是主通道。
 * 真实 LSPosed 安装走 getRemotePreferences()，本类不参与。文件只写
 * 本模块包名自己的外部目录，不写公共 Download/Documents，避免把一个
 * 模块的开关暴露成全局可读文件。
 */
public final class EmbeddedPrefs {
    public static final String FILE_NAME = "settings.txt";

    private EmbeddedPrefs() {}

    /** 设置页侧（模块自己的进程）可写的目录；宿主侧会读同一位置。 */
    private static List<File> writeTargets(Context context) {
        List<File> out = new ArrayList<>();
        if (context != null) {
            File own = context.getExternalFilesDir(null);
            if (own != null) out.add(new File(own, FILE_NAME));
        }
        return out;
    }

    /** 宿主进程可读的目录：内嵌框架下两者同包名，路径一致。 */
    private static List<File> readTargets() {
        List<File> out = new ArrayList<>();
        for (String dir : new String[] {
            "/sdcard/Android/data/" + Rules.PACKAGE + "/files",
            "/storage/emulated/0/Android/data/" + Rules.PACKAGE + "/files",
        }) out.add(new File(dir, FILE_NAME));
        return out;
    }

    /** 写出全部受管开关（布尔 + 整型），返回成功写入的路径数。 */
    static int write(Context context, Map<String, Boolean> values, Map<String, Integer> ints) {
        if (values == null) return 0;
        byte[] data = serialize(values, ints).getBytes(StandardCharsets.UTF_8);
        int ok = 0;
        for (File file : writeTargets(context)) {
            if (writeFile(file, data)) ok++;
        }
        return ok;
    }

    /**
     * 与 {@link SettingsKeys#dump} 一致的完整导出：整份受管键列表都写出去，
     * 未保存过的键用默认值补齐。三条后备通道导出的内容因此完全相同，
     * 不会出现「文件里缺某个键、provider 里却有」的差异。
     */
    private static String serialize(Map<String, Boolean> values, Map<String, Integer> ints) {
        List<String> keys = new ArrayList<>(Arrays.asList(Rules.allSettingKeys()));
        Collections.sort(keys);
        StringBuilder text = new StringBuilder();
        for (String key : keys) {
            if (!PrivacyRules.settingKey(key)) continue;
            if (Rules.isIntKey(key)) {
                Integer v = ints == null ? null : ints.get(key);
                int effective = v != null ? v : Rules.intDefault(key);
                text.append(key).append('=').append(effective).append('\n');
            } else {
                Boolean value = values.get(key);
                boolean effective = value != null ? value : Rules.defaultOn(key);
                text.append(key).append('=').append(effective).append('\n');
            }
        }
        return text.toString();
    }

    /** 宿主进程侧写出，把收到的开关留存给下次冷启动。 */
    static boolean writeHost(Map<String, Boolean> values, Map<String, Integer> ints) {
        if (values == null || values.isEmpty()) return false;
        byte[] data = serialize(values, ints).getBytes(StandardCharsets.UTF_8);
        for (File file : readTargets()) {
            if (writeFile(file, data)) return true;
        }
        return false;
    }

    private static boolean writeFile(File file, byte[] data) {
        File dir = file.getParentFile();
        File tmp = new File(dir, FILE_NAME + ".tmp");
        try {
            if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return false;
            try (FileOutputStream out = new FileOutputStream(tmp)) { out.write(data); }
            if (file.exists() && !file.delete()) return false;
            return tmp.renameTo(file) || copy(tmp, file);
        } catch (Throwable ignored) {
            return false;
        } finally {
            try { tmp.delete(); } catch (Throwable ignored) {}
        }
    }

    private static boolean copy(File src, File dst) throws Exception {
        try (InputStream in = new FileInputStream(src); FileOutputStream out = new FileOutputStream(dst)) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
        }
        return true;
    }

    /** 只读布尔（整型丢弃）。保留给不关心参数的调用方。 */
    static Map<String, Boolean> read() {
        Map<String, Boolean> result = read(null);
        if (result == null || result.isEmpty()) return null;
        return result;
    }

    /**
     * 读取共享文件，同时填充布尔与整型。
     *
     * 布尔返回值为 null 表示「没读到文件」；否则即使只读到整型，
     * 也会返回一个（可能为空的）布尔表，让调用方区分「无文件」与「文件存在」。
     */
    static Map<String, Boolean> read(Map<String, Integer> intOut) {
        List<File> candidates = new ArrayList<>();
        for (File file : readTargets()) if (file.isFile()) candidates.add(file);
        Collections.sort(candidates, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        for (File file : candidates) {
            Map<String, Boolean> result = parse(file, intOut);
            if (result != null) return result;
        }
        return null;
    }

    private static Map<String, Boolean> parse(File file, Map<String, Integer> intOut) {
        if (file == null || !file.isFile() || file.length() > 65536) return null;
        Map<String, Boolean> values = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int split = line.indexOf('=');
                if (split <= 0) continue;
                String key = line.substring(0, split).trim();
                String value = line.substring(split + 1).trim();
                if (!PrivacyRules.settingKey(key)) continue;
                if (Rules.isIntKey(key)) {
                    try {
                        int parsed = Integer.parseInt(value);
                        Integer clamped = SettingsKeys.acceptInt(key, parsed);
                        if (clamped != null && intOut != null) intOut.put(key, clamped);
                    } catch (NumberFormatException ignored) {
                    }
                } else if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
                    values.put(key, Boolean.parseBoolean(value));
                }
            }
            return values;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
