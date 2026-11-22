package io.github.mianxyz.fanqiecustomizer;

import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;
import java.util.function.Consumer;

/**
 * Independent Android SDK implementation of a refractive glass pipeline, not upstream source.
 * Input is the node's own recorded backdrop, in local pixels; record foreground icons separately.
 * API 33+: source -> Gaussian blur -> AGSL refraction/material -> RenderNode.
 * Call on the rendering/UI thread. alpha is tint opacity percent (0..100), corner is px.
 * A false result asks the caller to install its existing Gaussian fallback on this node.
 */
public final class LiquidGlassRenderer {
    static final String SOURCE = """
        uniform shader content;
        uniform float2 size;
        uniform float corner;
        uniform float density;
        uniform float alpha;
        uniform float dark;
        uniform float pill;
        uniform float dragging;
        uniform float activity;
        uniform float direction;

        float roundbox(float2 p, float2 b, float r) {
            float2 q = abs(p) - b + float2(r);
            return length(max(q, float2(0.0))) + min(max(q.x, q.y), 0.0) - r;
        }
        float2 bounded(float2 p) {
            return clamp(p, float2(0.5), max(size - float2(0.5), float2(0.5)));
        }
        half4 main(float2 xy) {
            float2 halfSize = size * 0.5;
            float2 p = xy - halfSize;
            float radius = mix(corner, min(halfSize.x, halfSize.y), pill);
            float dist = roundbox(p, halfSize, radius);
            float aa = clamp(density * 0.45, 0.75, 1.5);
            float coverage = 1.0 - smoothstep(-aa, aa, dist);
            if (coverage <= 0.0) return half4(0.0);

            // Central differences remain stable at straight edges and at the box centre.
            float epsilon = max(0.5, density * 0.35);
            float2 gradient = float2(
                roundbox(p + float2(epsilon, 0.0), halfSize, radius)
                  - roundbox(p - float2(epsilon, 0.0), halfSize, radius),
                roundbox(p + float2(0.0, epsilon), halfSize, radius)
                  - roundbox(p - float2(0.0, epsilon), halfSize, radius));
            float2 normal = gradient / max(length(gradient), 0.0001);
            float depth = max(-dist, 0.0);
            float bevel = min(max(7.0 * density, radius * 0.32), min(halfSize.x, halfSize.y));
            float edge = 1.0 - smoothstep(0.0, max(bevel, 0.5), depth);
            float slope = 1.0 - sqrt(max(0.0, 1.0 - edge * edge));
            // Only pills fade from a quiet surface to a lens; panels retain their original material.
            float materialActivity = mix(1.0, activity, pill);
            float3 surfaceNormal = normalize(float3(normal * slope * 0.88 * materialActivity, 1.0));
            // Nonlinear curved-surface refraction; cap displacement for tiny nodes.
            float bend = min(14.0 * density, min(halfSize.x, halfSize.y) * 0.48) * materialActivity;
            float2 offset = -normal * bend * slope * (0.35 + 0.65 * slope);
            float centre = 1.0 - smoothstep(0.0, 1.0,
                length(p / max(halfSize, float2(0.5))));
            float magnification = mix(1.08, 1.14, pill * centre);
            magnification = mix(1.0, magnification, materialActivity);
            float2 uv = halfSize + p / magnification + offset;
            float dispersion = min(0.32 * density, 0.75) * slope * materialActivity;
            half4 middle = content.eval(bounded(uv));
            half4 red = content.eval(bounded(uv + normal * dispersion));
            half4 blue = content.eval(bounded(uv - normal * dispersion));
            float3 rgb = float3(float(red.r) / max(float(red.a), 0.0001),
                float(middle.g) / max(float(middle.a), 0.0001),
                float(blue.b) / max(float(blue.a), 0.0001));
            float luma = dot(rgb, float3(0.2126, 0.7152, 0.0722));
            float saturation = max(max(rgb.r, rgb.g), rgb.b) - min(min(rgb.r, rgb.g), rgb.b);
            rgb = mix(float3(luma), rgb, 1.0 + 0.20 * (1.0 - saturation));
            float3 tint = mix(float3(0.97, 0.985, 1.0), float3(0.055, 0.065, 0.085), dark);
            float tintAmount = alpha * mix(0.28, 0.40, dark)
                * mix(1.10, 0.85, luma) * mix(1.0, 0.58, dragging);
            tintAmount = mix(tintAmount, mix(mix(0.10, 0.16, dark), tintAmount * 0.20, activity), pill);
            rgb = mix(rgb, tint, tintAmount);

            float lightShift = direction * pill * activity * 0.65;
            float3 lightA = normalize(float3(-0.55 + lightShift, -0.70, 0.55));
            float3 lightB = normalize(float3(0.60, 0.65, 0.45));
            float specular = pow(max(dot(surfaceNormal, normalize(lightA + float3(0.0, 0.0, 1.0))), 0.0), 28.0)
                + 0.45 * pow(max(dot(surfaceNormal, normalize(lightB + float3(0.0, 0.0, 1.0))), 0.0), 34.0);
            float fresnel = 0.04 + 0.96 * pow(1.0 - surfaceNormal.z, 5.0);
            float rim = exp(-abs(dist + aa * 0.25) / max(0.65, density * 0.65));
            float directional = 0.35 + 0.65 * abs(dot(normal, normalize(float2(-0.65 + lightShift, -0.76))));
            // Soft inner contour complements the caller's external Canvas shadow.
            float innerShadow = pill * exp(-depth / max(1.0, 2.0 * density))
                * (0.035 + 0.035 * max(dot(normal, normalize(float2(0.6, 0.8))), 0.0)) * materialActivity;
            rgb *= 1.0 - innerShadow;
            rgb += float3(1.0, 0.985, 0.95) * (specular * slope * 0.26
                + rim * directional * (0.28 + fresnel * 0.38)) * materialActivity;
            float outAlpha = clamp(float(middle.a) * coverage * mix(1.0, 0.88, dragging), 0.0, 1.0);
            float4 liquidColor = float4(clamp(rgb, 0.0, 1.0) * outAlpha, outAlpha);
            // Quiet pill is a solid-color translucent fog, never a copy of the raw backdrop.
            float fogAlpha = coverage * mix(0.46, 0.28, dark);
            float3 fogRgb = mix(float3(0.88, 0.90, 0.93), float3(0.28, 0.30, 0.34), dark);
            float4 fogColor = float4(fogRgb * fogAlpha, fogAlpha);
            return half4(mix(liquidColor, mix(fogColor, liquidColor, activity), pill));
        }
        """;

    private final Consumer<String> log;
    private RenderEffect cachedEffect;
    private Params cachedParams;
    private boolean disabled;
    private boolean initialized;

    public LiquidGlassRenderer(Consumer<String> log) { this.log = log; }

    public static boolean supported(int api) { return Rules.supported(api); }
    public static float blurRadius(int blur, float density) { return Rules.blurRadius(blur, density); }

    public boolean apply(RenderNode node, int width, int height, float corner, float density,
            int blur, int alpha, boolean dark, boolean pill, boolean dragging) {
        return applyParams(node, Rules.params(width, height, corner, density, blur, alpha, dark, pill, dragging));
    }

    /** Continuous pill material: activity 0..1, direction -1..1 (right positive). Old API remains valid. */
    public boolean apply(RenderNode node, int width, int height, float corner, float density,
            int blur, int alpha, boolean dark, boolean pill, float activity, float direction) {
        return applyParams(node, Rules.params(width, height, corner, density, blur, alpha, dark, pill, activity, direction));
    }

    private boolean applyParams(RenderNode node, Params params) {
        if (disabled || !supported(Build.VERSION.SDK_INT) || node == null
                || !Rules.validSize(params.width, params.height)) return false;
        try {
            if (cachedEffect == null || !params.equals(cachedParams)) {
                RenderEffect effect = Api33.create(params);
                cachedEffect = effect;
                cachedParams = params;
            }
            // Reattach even when the same effect is reused for a different node or fallback replaced it.
            node.setRenderEffect(cachedEffect);
            if (!initialized) {
                initialized = true;
                emit("LiquidGlass initialized mode=" + (params.pill ? "pill" : "bar") + " params=" + params);
            }
            return true;
        } catch (RuntimeException | LinkageError error) {
            disabled = true;
            cachedEffect = null;
            cachedParams = null;
            emit("LiquidGlass disabled; Gaussian fallback: " + error.getClass().getSimpleName()
                    + " " + error.getMessage());
            return false;
        }
    }

    /** Clear native/cache references; a failed shader stays disabled for this renderer's lifetime. */
    public void reset() { cachedEffect = null; cachedParams = null; }

    private void emit(String message) {
        if (log != null) try { log.accept(message); } catch (RuntimeException ignored) { }
    }

    /** Kept behind the API gate so API 31/32 never instantiate RuntimeShader. */
    private static final class Api33 {
        static RenderEffect create(Params p) {
            RuntimeShader shader = new RuntimeShader(SOURCE);
            shader.setFloatUniform("size", p.width, p.height);
            shader.setFloatUniform("corner", p.corner);
            shader.setFloatUniform("density", p.density);
            shader.setFloatUniform("alpha", p.alpha / 100f);
            shader.setFloatUniform("dark", p.dark ? 1f : 0f);
            shader.setFloatUniform("pill", p.pill ? 1f : 0f);
            shader.setFloatUniform("dragging", p.dragging ? 1f : 0f);
            shader.setFloatUniform("activity", p.activity);
            shader.setFloatUniform("direction", p.direction);
            RenderEffect outer = RenderEffect.createRuntimeShaderEffect(shader, "content");
            float radius = Rules.blurRadius(p.blur, p.density)
                    + (p.pill ? 1.5f * p.density * (1f - p.activity) : 0f);
            if (radius <= 0f) return outer;
            RenderEffect inner = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP);
            return RenderEffect.createChainEffect(outer, inner);
        }
    }

    /** Analytic underdamped spring: upstream DampedDragAnimation scale X/Y use
     * damping 0.6/0.7, stiffness 250; velocity uses 0.5/300. No Android runtime required. */
    static final class Spring {
        float value, velocity, target;
        Spring(float initial) { value = target = initial; }
        void step(float seconds, float stiffness, float damping) {
            if (!Float.isFinite(seconds) || seconds <= 0f) return;
            double dt = Math.min(seconds, .064f), omega = Math.sqrt(stiffness);
            double decay = damping * omega, freq = omega * Math.sqrt(1.0 - damping * damping);
            double displacement = value - target, b = (velocity + decay * displacement) / freq;
            double c = Math.cos(freq * dt), sn = Math.sin(freq * dt), e = Math.exp(-decay * dt);
            value = target + (float) (e * (displacement * c + b * sn));
            velocity = (float) (e * ((-decay * displacement + freq * b) * c
                    + (-decay * b - freq * displacement) * sn));
        }
        boolean settled() { return Math.abs(value-target) < .001f && Math.abs(velocity) < .005f; }
    }

    record Params(int width, int height, float corner, float density, int blur, int alpha,
            boolean dark, boolean pill, boolean dragging, float activity, float direction) { }

    /** Pure Java: usable with android.jar stubs without constructing graphics objects. */
    static final class Rules {
        private Rules() { }
        record Bounce(float posDamping,float sxDamping,float syDamping,float pressScale,float velocityScale) { }
        static float bounceMap(int value,float low,float middle,float high) {
            float x=Math.max(0,Math.min(100,value))/50f;
            float t=x<=1f?x:x-1f;t=t*t*(3f-2f*t);
            return x<=1f?low+(middle-low)*t:middle+(high-middle)*t;
        }
        static float damping(float value) { return Float.isFinite(value)?Math.max(.301f,Math.min(.919f,value)):.5f; }
        static Bounce bounce(int value) {
            return new Bounce(damping(bounceMap(value,.86f,.50f,.34f)),
                damping(bounceMap(value,.88f,.60f,.38f)),damping(bounceMap(value,.90f,.70f,.44f)),
                bounceMap(value,1.08f,1.16f,1.22f),bounceMap(value,.01f,.04f,.08f));
        }
        static float barPressScale(int value) { return bounceMap(value,1.015f,1.028f,1.042f); }
        static float barDamping(int value) { return damping(bounceMap(value,.919f,.68f,.38f)); }
        static float barScale(float value) { return Float.isFinite(value)?Math.max(.97f,Math.min(1.06f,value)):1f; }
        static void resetBarScale(Spring spring) { spring.value=spring.target=1f;spring.velocity=0f; }
        static void stepBarScale(Spring spring,float dt,int bounce) {
            spring.target=barScale(spring.target);
            if(!Float.isFinite(spring.value)||!Float.isFinite(spring.velocity))resetBarScale(spring);
            spring.step(dt,250f,barDamping(bounce));spring.value=barScale(spring.value);
            if(spring.settled()){spring.value=spring.target;spring.velocity=0f;}
        }
        static boolean supported(int api) { return api >= 33; }
        static boolean validSize(int width, int height) {
            return width > 0 && height > 0 && width <= 32768 && height <= 32768;
        }
        static float density(float value) {
            return Float.isFinite(value) ? Math.max(0.25f, Math.min(8f, value)) : 1f;
        }
        static int blur(int value) { return Math.max(0, Math.min(2, value)); }
        static int alpha(int value) { return Math.max(0, Math.min(100, value)); }
        static float corner(float value, int width, int height) {
            float finite = Float.isFinite(value) ? value : 0f;
            return Math.max(0f, Math.min(finite, Math.min(Math.max(0, width), Math.max(0, height)) * 0.5f));
        }
        static float blurRadius(int value, float density) {
            int level = blur(value);
            return (level == 0 ? 0f : level == 1 ? 1f : 2.5f) * density(density);
        }
        static float activity(float value) { return Float.isFinite(value) ? Math.max(0f, Math.min(1f, value)) : 0f; }
        static float direction(float value) { return Float.isFinite(value) ? Math.max(-1f, Math.min(1f, value)) : 0f; }
        static Params params(int width, int height, float corner, float density, int blur, int alpha,
                boolean dark, boolean pill, float activity, float direction) {
            return new Params(width, height, corner(corner, width, height), density(density),
                    blur(blur), alpha(alpha), dark, pill, false, activity(activity), direction(direction));
        }
        static Params params(int width, int height, float corner, float density, int blur, int alpha,
                boolean dark, boolean pill, boolean dragging) {
            return new Params(width, height, corner(corner, width, height), density(density),
                    blur(blur), alpha(alpha), dark, pill, dragging, dragging ? 1f : 0f, 0f);
        }
    }
}