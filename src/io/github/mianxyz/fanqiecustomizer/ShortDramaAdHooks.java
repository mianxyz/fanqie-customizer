package io.github.mianxyz.fanqiecustomizer;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

/**
 * 短剧插页广告（Series Patch Ad / 章节间插入短剧）拦截。
 *
 * 适配验证（apktool smali 逐条核对，两版符号与签名完全一致）：
 *   com.dragon.read 7.3.9.32 (73932)   —— 本模块目标版本
 *   com.dragon.read.oversea.gp 7.4.3.32 (74332) —— 海外版
 *
 * 依赖的符号：
 *   com.dragon.read.nonstandard.ad.impl.NonStandardAdApiImpl
 *     isSeriesPatchAdAvailable()Z
 *     hasSeriesPatchAd(JJ)Z
 *     checkPauseAdInserted(Ljava/lang/String;I)Z
 *   com.dragon.read.base.ssconfig.SsConfigMgr
 *     getABValue(Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;
 *   com.dragon.read.base.ssconfig.model.AutoReadingShowAd
 *     <init>(ZZ)V   // (inspireAdDisable, insertAdEnable)
 *
 * 仅 hook 运行时判断，不改写 APK 任何字节，故不影响应用签名与服务端签名校验。
 */
final class ShortDramaAdHooks {
    private static final String API_IMPL = "com.dragon.read.nonstandard.ad.impl.NonStandardAdApiImpl";
    private static final String SS_CONFIG = "com.dragon.read.base.ssconfig.SsConfigMgr";
    private static final String AUTO_AD = "com.dragon.read.base.ssconfig.model.AutoReadingShowAd";
    private static final String CONFIG_KEY = "auto_reading_show_ad_strategy";

    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;

    ShortDramaAdHooks(XposedModule module, FeatureHooks.Host host, ClassLoader loader) {
        this.module = module;
        this.host = host;
        this.loader = loader;
    }

    void install() {
        int hits = 0;
        hits += blockFalse(API_IMPL, "isSeriesPatchAdAvailable");
        hits += blockFalse(API_IMPL, "hasSeriesPatchAd", long.class, long.class);
        hits += blockFalse(API_IMPL, "checkPauseAdInserted", String.class, int.class);
        hits += blockReaderInsertAd();
        host.log("Short-drama ad hooks registered: " + hits);
    }

    /** 让目标的 boolean 方法恒返回 false（无参 / 指定参数签名）。 */
    private int blockFalse(String className, String methodName, Class<?>... params) {
        int count = 0;
        try {
            Class<?> cls = Class.forName(className, false, loader);
            for (Method m : cls.getDeclaredMethods()) {
                if (!m.getName().equals(methodName) || m.getReturnType() != boolean.class) continue;
                Class<?>[] types = m.getParameterTypes();
                if (types.length != params.length) continue;
                boolean match = true;
                for (int i = 0; i < params.length; i++) {
                    if (types[i] != params[i]) { match = false; break; }
                }
                if (!match) continue;
                module.hook(m).intercept(chain -> Boolean.FALSE);
                count++;
            }
        } catch (Throwable t) {
            host.log("Short-drama ad hook unavailable: " + className + "." + methodName
                    + " " + PrivacyRules.errorType(t));
        }
        return count;
    }

    /**
     * 拦截 SsConfigMgr.getABValue("auto_reading_show_ad_strategy")，
     * 返回一个 insertAdEnable=false 的 AutoReadingShowAd 实例，关闭阅读流里的插页广告。
     */
    private int blockReaderInsertAd() {
        try {
            Class<?> mgr = Class.forName(SS_CONFIG, false, loader);
            Class<?> autoAd = Class.forName(AUTO_AD, false, loader);
            final Constructor<?> ctor = autoAd.getDeclaredConstructor(boolean.class, boolean.class);
            ctor.setAccessible(true);
            int count = 0;
            for (Method m : mgr.getDeclaredMethods()) {
                if (!m.getName().equals("getABValue")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length != 2 || p[0] != String.class || p[1] != Object.class) continue;
                module.hook(m).intercept(chain -> {
                    if (CONFIG_KEY.equals(chain.getArg(0))) {
                        return ctor.newInstance(false, false);
                    }
                    return chain.proceed();
                });
                count++;
            }
            return count;
        } catch (Throwable t) {
            host.log("Reader insert-ad config hook unavailable: " + PrivacyRules.errorType(t));
            return 0;
        }
    }
}
