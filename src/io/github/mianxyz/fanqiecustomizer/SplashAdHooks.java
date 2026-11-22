package io.github.mianxyz.fanqiecustomizer;

import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/**
 * 应用启动开屏广告拦截。
 *
 * 与 5.0.3 被放弃的「跳过开屏」不是同一件事：那次是试图跳过整个启动页，
 * 这里只让宿主自己判定「本次不需要开屏广告」，启动流程完全保留。
 *
 * 切入点选在 {@code pages.splash.AttributionManager.hasHitAttribution()}：
 * 宿主在三条路径上都用它决定是否请求开屏广告
 * （SplashActivity.startCommonSplash、SplashFragment.onCreateContent、c4.b），
 * 返回 false 等于「本次没有命中投放」——宿主自己就不会发请求，
 * 既没有广告，也不会留下等待广告超时的空白。
 *
 * 这样做而不是拦截 requestAd：拦请求会让页面进入「等广告返回」的状态，
 * 反而可能多等一个超时；而从判定处返回 false，连请求都不会发出。
 *
 * 跨版本成立：该类的这个方法在 7.3.9.32 / 7.4.1.32 / 7.4.3.32 / 7.4.5.32
 * 四个版本的类表中都存在（已用真实 APK 逐个核对），因此不需要按版本分叉。
 * 类名万一在将来版本消失，hook 注册会失败并静默跳过，不影响其它功能。
 */
final class SplashAdHooks {
    private static final String ATTRIBUTION = "com.dragon.read.pages.splash.AttributionManager";

    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;

    SplashAdHooks(XposedModule module, FeatureHooks.Host host, ClassLoader loader) {
        this.module = module;
        this.host = host;
        this.loader = loader;
    }

    void install() {
        int hits = 0;
        try {
            Class<?> cls = HostSymbols.type(ATTRIBUTION,loader);
            for (Method method : cls.getDeclaredMethods()) {
                if (!HostSymbols.matches(method,"hasHitAttribution")) continue;
                if (method.getReturnType() != boolean.class) continue;
                if (method.getParameterTypes().length != 0) continue;
                module.hook(method).intercept(chain -> Boolean.FALSE);
                hits++;
            }
        } catch (Throwable error) {
            host.log("Splash ad hook unavailable: " + PrivacyRules.errorType(error));
        }
        host.log("Splash ad hooks registered: " + hits);
    }
}
