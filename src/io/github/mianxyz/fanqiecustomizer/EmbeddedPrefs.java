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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Optional shared settings channel for embedded frameworks (FPA / LSPatch).
 *
 * In a real LSPosed install the settings activity writes its switches through
 * XposedService into remote preferences, and the hook side reads them with
 * getRemotePreferences(). An embedded framework has no XposedService, so the
 * settings page stays at "waiting for sync" and the hook side receives an empty
 * (or null) preference map. This class bridges the two processes with a small
 * plain-text file in a shared external directory instead.
 */
public final class EmbeddedPrefs {
    public static final String FILE_NAME = "fanqie_customizer_settings.txt";

    private static final String[] SHARED_DIRS = {
        "/sdcard/Android/media/com.dragon.read",
        "/sdcard/Download",
        "/sdcard/Documents",
        "/storage/emulated/0/Download",
        "/storage/emulated/0/Documents",
    };

    private EmbeddedPrefs() {}

    public static String primaryPath() {
        return new File(SHARED_DIRS[0], FILE_NAME).getAbsolutePath();
    }

    private static List<File> writeTargets(Context context) {
        List<File> out = new ArrayList<>();
        for (String dir : SHARED_DIRS) out.add(new File(dir, FILE_NAME));
        if (context != null) {
            File own = context.getExternalFilesDir(null);
            if (own != null) out.add(new File(own, FILE_NAME));
        }
        return out;
    }

    /** Write every known switch (with defaults filled in). Returns how many paths succeeded. */
    public static int write(Context context, SharedPreferencesLike source) {
        StringBuilder text = new StringBuilder();
        List<String> keys = new ArrayList<>();
        Collections.addAll(keys, Rules.allSettingKeys());
        Collections.sort(keys);
        for (String key : keys) {
            if (!PrivacyRules.settingKey(key)) continue;
            text.append(key).append('=').append(source.getBoolean(key, Rules.defaultOn(key))).append('\n');
        }
        byte[] data = text.toString().getBytes(StandardCharsets.UTF_8);
        int ok = 0;
        for (File file : writeTargets(context)) {
            File dir = file.getParentFile();
            File tmp = new File(dir, FILE_NAME + ".tmp");
            try {
                if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) continue;
                try (FileOutputStream out = new FileOutputStream(tmp)) {
                    out.write(data);
                }
                if (file.exists() && !file.delete()) continue;
                if (tmp.renameTo(file) || copy(tmp, file)) ok++;
            } catch (Throwable ignored) {
                // try the next shared directory
            } finally {
                try { tmp.delete(); } catch (Throwable ignored) {}
            }
        }
        return ok;
    }

    /** Called from inside the hooked host process, which owns SHARED_DIRS[0]. */
    public static boolean writeHost(Map<String, Boolean> values) {
        StringBuilder text = new StringBuilder();
        List<String> keys = new ArrayList<>(values.keySet());
        Collections.sort(keys);
        for (String key : keys) {
            if (!PrivacyRules.settingKey(key)) continue;
            text.append(key).append('=').append(values.get(key)).append('\n');
        }
        File file = new File(SHARED_DIRS[0], FILE_NAME);
        File dir = file.getParentFile();
        File tmp = new File(dir, FILE_NAME + ".tmp");
        try {
            if (dir != null && !dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) return false;
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(text.toString().getBytes(StandardCharsets.UTF_8));
            }
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

    /** Read switches from the first readable shared file; null means no file available. */
    public static Map<String, Boolean> read() {
        for (String dir : SHARED_DIRS) {
            Map<String, Boolean> result = parse(new File(dir, FILE_NAME));
            if (result != null) return result;
        }
        return null;
    }

    private static Map<String, Boolean> parse(File file) {
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
                if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))
                    values.put(key, Boolean.parseBoolean(value));
            }
            return values;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** Minimal abstraction so this class does not depend on Android types at the call site. */
    public interface SharedPreferencesLike {
        boolean getBoolean(String key, boolean fallback);
    }
}
