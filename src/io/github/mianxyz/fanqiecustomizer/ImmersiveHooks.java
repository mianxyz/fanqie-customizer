package io.github.mianxyz.fanqiecustomizer;

import android.app.Activity;
import android.os.Build;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/**
 * 常驻隐藏系统状态栏。
 *
 * 不依赖番茄的任何类名——只挂 Activity 生命周期，所以四个受支持版本
 * （7.3.9.32 / 7.4.1.32 / 7.4.3.32 / 7.4.5.32）行为一致，
 * 也不会因为宿主重构而失效。
 *
 * 做法是每个 Activity 走一次 onCreate 之后，把系统栏设成「滑出式隐藏」：
 * 内容延伸到状态栏下方，需要时从边缘下滑仍能临时看到状态栏，
 * 不会把状态栏彻底锁死导致用户没法看时间。
 *
 * 只在 enabled 且开关打开时安装；关闭时不注册任何回调。
 */
final class ImmersiveHooks {
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;

    ImmersiveHooks(XposedModule module, FeatureHooks.Host host, ClassLoader loader) {
        this.module = module;
        this.host = host;
        this.loader = loader;
    }

    void install() {
        int hits = 0;
        try {
            Class<?> activity = Class.forName("android.app.Activity", false, loader);
            for (Method method : activity.getDeclaredMethods()) {
                if (!"onCreate".equals(method.getName())) continue;
                Class<?>[] params = method.getParameterTypes();
                if (params.length != 1 || params[0] != android.os.Bundle.class) continue;
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        hideSystemBars((Activity) chain.getThisObject());
                    } catch (Throwable error) {
                        host.log("Immersive apply failed: " + PrivacyRules.errorType(error));
                    }
                    return result;
                });
                hits++;
            }
        } catch (Throwable error) {
            host.log("Immersive hook unavailable: " + PrivacyRules.errorType(error));
        }
        host.log("Immersive hooks registered: " + hits);
    }

    /** 把状态栏设为可滑出隐藏；导航栏保持系统默认，避免影响返回手势。 */
    private static void hideSystemBars(Activity activity) {
        if (activity == null) return;
        Window window = activity.getWindow();
        if (window == null) return;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // Android 9 / 10 走旧的 View flag；模块 minSdk 是 28，这条分支需要保留。
            View decor = window.getDecorView();
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }
}
