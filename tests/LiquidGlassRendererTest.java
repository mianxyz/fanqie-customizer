package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone pure-Java rules and SDK/AGSL contract checks; no graphics stub is executed. */
public final class LiquidGlassRendererTest {
    public static void main(String[] args) throws Exception {
        check(!LiquidGlassRenderer.Rules.supported(31), "API31 fallback");
        check(!LiquidGlassRenderer.Rules.supported(32), "API32 fallback");
        check(LiquidGlassRenderer.Rules.supported(33), "API33 shader");
        check(LiquidGlassRenderer.supported(34), "future API");
        check(!LiquidGlassRenderer.Rules.validSize(0, 100), "zero width");
        check(!LiquidGlassRenderer.Rules.validSize(100, -1), "negative height");
        check(!LiquidGlassRenderer.Rules.validSize(Integer.MAX_VALUE, 100), "excessive extent");
        check(LiquidGlassRenderer.Rules.validSize(2000, 3200), "tablet extent");
        close(LiquidGlassRenderer.Rules.density(Float.NaN), 1f, "NaN density");
        close(LiquidGlassRenderer.Rules.density(Float.POSITIVE_INFINITY), 1f, "infinite density");
        close(LiquidGlassRenderer.Rules.density(-1f), .25f, "negative density");
        close(LiquidGlassRenderer.Rules.density(100f), 8f, "high density");
        close(LiquidGlassRenderer.Rules.corner(Float.NaN, 100, 20), 0f, "NaN corner");
        close(LiquidGlassRenderer.Rules.corner(Float.NEGATIVE_INFINITY, 100, 20), 0f, "infinite corner");
        close(LiquidGlassRenderer.Rules.corner(999f, 100, 20), 10f, "corner fits short edge");
        close(LiquidGlassRenderer.Rules.corner(-5f, 100, 20), 0f, "negative corner");
        close(LiquidGlassRenderer.blurRadius(-1, 2f), 0f, "blur zero");
        close(LiquidGlassRenderer.blurRadius(1, 2f), 2f, "blur medium");
        close(LiquidGlassRenderer.blurRadius(2, 2f), 5f, "blur strong");
        close(LiquidGlassRenderer.blurRadius(999, Float.NaN), 2.5f, "blur sanitizes both inputs");
        check(LiquidGlassRenderer.Rules.alpha(Integer.MIN_VALUE) == 0, "low alpha");
        check(LiquidGlassRenderer.Rules.alpha(Integer.MAX_VALUE) == 100, "high alpha");
        var a = LiquidGlassRenderer.Rules.params(100, 20, 999, Float.NaN, 3, 200, false, true, false);
        var b = LiquidGlassRenderer.Rules.params(100, 20, 10, 1, 2, 100, false, true, false);
        check(a.equals(b), "normalized cache key equality");
        check(!a.equals(LiquidGlassRenderer.Rules.params(100, 20, 10, 1, 2, 100, false, true, true)), "drag invalidates cache");
        check(!a.equals(LiquidGlassRenderer.Rules.params(100, 20, 10, 1, 2, 100, true, true, false)), "theme invalidates cache");
        check(!a.equals(LiquidGlassRenderer.Rules.params(100, 20, 10, 1, 2, 100, false, false, false)), "mode invalidates cache");
        check(!a.equals(LiquidGlassRenderer.Rules.params(101, 20, 10, 1, 2, 100, false, true, false)), "size invalidates cache");
        String source = Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/LiquidGlassRenderer.java"));
        String agsl = LiquidGlassRenderer.SOURCE;
        check(agsl.contains("uniform shader content;") && agsl.contains("uniform float2 size;"), "input and size contract");
        for (String name : new String[]{"corner", "density", "alpha", "dark", "pill", "dragging", "activity", "direction"}) {
            check(agsl.contains("uniform float " + name + ";"), name + " declared");
            check(source.contains("setFloatUniform(\"" + name + "\""), name + " bound");
        }
        check(source.contains("createRuntimeShaderEffect(shader, \"content\")"), "content child bound by SDK");
        check(source.contains("createChainEffect(outer, inner)"), "blur then refract");
        check(source.contains("node.setRenderEffect(cachedEffect)"), "node-only destination");
        check(!source.contains("setInputShader(") && !source.contains("Bitmap") && !source.contains("View" + ".setRenderEffect"), "no bitmap or view pipeline");
        check(source.contains("params.equals(cachedParams)"), "stable parameters reuse native effect");
        check(source.contains("disabled = true;") && source.contains("if (disabled ||"), "permanent failure fuse");
        String reset = source.substring(source.indexOf("public void reset()"), source.indexOf("private void emit"));
        check(!reset.contains("disabled =") && !reset.contains("initialized ="), "reset keeps failure and one-time logging guards");
        check(agsl.contains("roundbox(p +") && agsl.contains("roundbox(p -"), "difference normals");
        check(agsl.contains("mix(1.08, 1.14, pill * centre)"), "legacy 1.08 to 1.14x lens bounds");
        check(agsl.contains("mix(1.0, activity, pill)") && agsl.contains("mix(1.0, magnification, materialActivity)"), "pill activity controls lens while panels retain full refraction");
        check(agsl.contains("direction * pill * activity") && agsl.contains("* materialActivity"), "activity drives refraction depth and directional light");
        check(agsl.contains("rgb, tint, tintAmount") && agsl.contains("fresnel") && agsl.contains("specular"), "glass material stages");
        check(agsl.contains("* outAlpha") && agsl.contains("smoothstep(-aa, aa, dist)"), "premultiplied antialiased output");
        check(agsl.contains("float4 fogColor = float4(fogRgb * fogAlpha, fogAlpha)") && agsl.contains("mix(fogColor, liquidColor, activity)"), "quiet fixed fog transitions to raw lens in premultiplied space");
        check(agsl.contains("mix(liquidColor, mix(fogColor, liquidColor, activity), pill)"), "non-pill panels preserve sampled liquid output");
        System.out.println("LiquidGlassRendererTest passed");
    }
    private static void close(float actual, float expected, String message) {
        check(Float.isFinite(actual) && Math.abs(actual - expected) < .0001f, message);
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}