package io.github.mianxyz.fanqiecustomizer;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Source-boundary guards only; no duplicate rendering implementation or device claims. */
public final class GlobalGlassIsolationTest {
    private static final Path SOURCE = Path.of("src/io/github/mianxyz/fanqiecustomizer");
    private static final String GLASS_SHA256 = "BE54D2CD2573D2AF88CB13CE5FEFCF8B33675D3C92C569A988A61EA5306C6914";
    public static void main(String[] args) throws Exception {
        byte[] glass = HostBaselineEquivalenceTest.originalBytes("GlassSurfaces.java");
        check(HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(glass)).equals(GLASS_SHA256), "563 reviewed GlassSurfaces identity changed");
        check(HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(HostBaselineEquivalenceTest.originalBytes("NativeCoverGlass.java"))).equals("9C4E040B615FA69354CCC71158021C3380DCEC3F4D8299D362DBCC92555252CF"), "563 reviewed rank renderer identity changed");
        check(HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(HostBaselineEquivalenceTest.originalBytes("RankLayoutFix.java"))).equals("3A8173BCE383C1602ED8A8109E52047E8428AD40991F5A1BBFC44BBBFDBC501E"), "rank layout frozen for 561");
        String widthSource = new String(glass, StandardCharsets.UTF_8);
        String width = methodBlock(widthSource, "private void updateBarWidth(");
        check(widthSource.contains("if (!barEnabled()) { restoreBarTree(bar, 0); return; }"), "disabled restores original host geometry");
        check(width.contains("int percent = docked ? 100"), "docked uses full parent width");
        check(width.contains("parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight()"), "parent size recomputed for rotation");
        check(width.contains("tabs) * dp(48,") && width.contains("Math.min(available, Math.max(minSafeWidth,"), "visible tabs retain safe slots within parent");
        check(width.contains("parentWidth - 2 * margin") && width.contains("Math.round(available * (percent / 100f))"), "width percentage recalculated after current margins");
        check(widthSource.contains("if (!isRoot) return;"), "nested bottom bars do not shrink twice");
        check(widthSource.contains("updateBarStyleGeometry(bar); updateBarWidth(bar); fitTabTrack(bar);"), "track follows current style and width");
        check(widthSource.indexOf("ensureBarReady(bar);") < widthSource.indexOf("paint(bar, floating);"), "zero-size bar registers readiness before painting");
        check(widthSource.contains("state.queued || painted.contains(bar) || !bar.isAttachedToWindow()") && widthSource.contains("if (!bar.isAttachedToWindow() || bar.getWidth() <= 0 || bar.getHeight() <= 0) return;"), "readiness checks dimensions before queue and before execution");
        check(widthSource.contains("state.observer==observer&&observer.isAlive()") && widthSource.contains("removeOnPreDrawListener(state.listener)"), "observer identity deduplication and detach cleanup");
        byte[] baseline = Files.readAllBytes(Path.of(".verify/553-baseline").resolve(SOURCE).resolve("RefreshConfirm.java"));
        byte[] current = Files.readAllBytes(SOURCE.resolve("RefreshConfirm.java"));
        check(HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(current)).equals("5DC7EF336516FC8C09B0AB79133220409415ED731E7EA4505700B593A68BC4AC"), "RefreshConfirm remains exactly frozen during width extension");
        String original = new String(baseline, StandardCharsets.UTF_8);
        String eol = original.contains("\r\n") ? "\r\n" : "\n";
        String call = "GlobalGlassSurfaces.decorateDialog(a,card);";
        check(occurrences(original, "return d;") == 1, "baseline return anchor must be unique");
        String expected = original.replace("return d;", eol + "        " + call + eol + "        return d;");
        check(Arrays.equals(expected.getBytes(StandardCharsets.UTF_8), current), "RefreshConfirm differs beyond the one decoration insertion");
        check(!Files.exists(SOURCE.resolve("GlobalGlassProbe.java")), "lead must remove retired GlobalGlassProbe source");
        String module = Files.readString(SOURCE.resolve("ModuleHook.java"));
        String install = "installQuietly(\"global-glass\",()->new GlobalGlassSurfaces(this,businessHost,context.getClassLoader()).install(app));";
        check(occurrences(module, install) == 1, "global glass entry must be unique");
        int host = module.indexOf("FeatureHooks.Host businessHost=");
        int hostEnd = module.indexOf("        };", host);
        check(host >= 0 && hostEnd > host && module.indexOf(install) > hostEnd, "global glass entry precedes businessHost completion");
        check(module.contains("businessConfig.install(this,app,this::logMessage);"), "business config install retained");
        Path global = SOURCE.resolve("GlobalGlassSurfaces.java");
        check(Files.isRegularFile(global), "missing GlobalGlassSurfaces implementation");
        // Conservative same-file screen-capture guard: a new file that names a pager
        // and records/draws content requires review. This does not prove runtime isolation.
        Pattern pager = Pattern.compile("(?:ViewPager|InterceptSelectViewPager)");
        Pattern recording = Pattern.compile("(?:beginRecording|Picture\\s*\\(|\\.draw\\s*\\()");
        try (Stream<Path> paths = Files.walk(SOURCE)) {
            for (Path file : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                check(!source.contains("GlobalGlassProbe") && !source.contains("global-glass-probe"), "retired probe reference in " + file);
                // Existing renderer is byte-pinned above; only new global-glass family is scanned.
                if (!file.getFileName().toString().startsWith("GlobalGlass")) continue;
                String code = withoutComments(source);
                String dialog = methodBlock(code, "private void captureDialog(");
                String panels = dialog.isEmpty() ? code : code.replace(dialog, "");
                check(!panels.contains("PixelCopy"), "global panel PixelCopy boundary in " + file);
                // The dialog exception must not be called by a second panel path.
                if (!dialog.isEmpty()) check(occurrences(code, "captureDialog(") == 2, "dialog capture has an extra caller in " + file);
                check(!(pager.matcher(source).find() && recording.matcher(source).find()), "global pager recording boundary in " + file);
            }
        }
        String gradle = Files.readString(Path.of("app/build.gradle.kts"));
        check(gradle.contains("versionCode = 107") && gradle.contains("versionName = \"6.1\""), "6.1 / 107 version contract");
        System.out.println("GlobalGlassIsolationTest passed (byte identity and conservative source boundaries only)");
    }
    private static String withoutComments(String text) {
        return text.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//[^\\r\\n]*", "");
    }
    private static String methodBlock(String text, String signature) {
        int start=text.indexOf(signature); if(start<0)return "";
        int open=text.indexOf('{',start), depth=0; boolean quoted=false, escaped=false; char quote=0;
        for(int i=open;i<text.length();i++){
            char c=text.charAt(i);
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c==quote)quoted=false;continue;}
            if(c=='"'||c=='\''){quoted=true;quote=c;continue;}
            if(c=='{')depth++;else if(c=='}'&&--depth==0)return text.substring(start,i+1);
        }
        throw new AssertionError("unclosed dialog capture method");
    }
    private static int occurrences(String text, String part) { int count=0, at=0; while ((at=text.indexOf(part,at))>=0) {count++;at+=part.length();} return count; }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
