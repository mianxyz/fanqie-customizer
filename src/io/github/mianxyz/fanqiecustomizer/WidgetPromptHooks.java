package io.github.mianxyz.fanqiecustomizer;

import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/**
 * #21 禁止「添加桌面卡片」引导。
 *
 * ── 反汇编确证（7.3.9.32，classes*.dex 全量扫描字符串表）───────���────
 * 落点：com.dragon.read.router.action.UgAppWidgetAction
 *   classes8.dex 该类本体：Lcom/dragon/read/router/action/UgAppWidgetAction;
 *                 父类 Lcom/dragon/read/router/action/AbsActionRoute;
 *                 唯一执行方法 c(Landroid/content/Context;Lcom/bytedance/router/c;)V
 *   classes20.dex 注册处：
 *       Lcom/bytedance/router/generator/mapping/SmartrouterMapping$$ug_impl;
 *       init(Ljava/util/Map;)V -> "com.dragon.read.router.action.UgAppWidgetAction"
 *
 * 也就是说：宿主一切「把这个卡片放到桌面」的引导（书架提示、阅读页弹窗、
 * 活动页按钮）最终都走这条 bytedance router 动作。
 *
 * 相关但**不在此 hook** 的类（避免误伤）：
 *   - AppWidgetManager.requestPinAppWidget：系统 API，番茄在

 *     classes23.dex 里只是调用方；拦系统 API 会影响所有应用。
 *   - WidgetGuideSchemaInterceptor / INovelRecommendationWidgetGuideAB：AB 开关，
 *     只控制是否展示素材，不是真正的动作入口。
 *   - DesktopWidgetResp / DesktopWidgetRequest：网络模型，不拦。
 *
 * 做法：让 c(...) 直接返回、不执行原逻辑。动作本身不携带必需副作用，
 * 跳过它只会让「引导加桌面卡片」这一步不发生，不影响其他路由。
 */
final class WidgetPromptHooks {
    /** 反汇编确证的桌面卡片路由动作。 */
    private static final String ACTION = "com.dragon.read.router.action.UgAppWidgetAction";

    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;

    WidgetPromptHooks(XposedModule module, FeatureHooks.Host host, ClassLoader loader) {
        this.module = module;
        this.host = host;
        this.loader = loader;
    }

    void install() {
        int hits = 0;
        try {
            Class<?> cls = HostSymbols.type(ACTION,loader);
            for (Method m : cls.getDeclaredMethods()) {
                // 只挂执行入口 c(Context, c)：<init>/<clinit> 跳过。
                if (!HostSymbols.matches(m,"c")) continue;
                Class<?>[] params = m.getParameterTypes();
                if (params.length != 2) continue;
                if (!"android.content.Context".equals(params[0].getName())) continue;
                module.hook(m).intercept(chain -> {
                    host.log("Widget prompt blocked");
                    return null;
                });
                hits++;
            }
        } catch (Throwable error) {
            host.log("Widget prompt FAILED: " + error.getClass().getName()
                    + ": " + error.getMessage());
        }
        try {
            Class<?> widget=HostSymbols.type("ve3.s0",loader);
            for(Method m:widget.getDeclaredMethods()){
                boolean request=m.getReturnType()==boolean.class&&m.getParameterCount()==9&&HostSymbols.matches(m,"G");
                boolean route=m.getReturnType()==void.class&&m.getParameterCount()==5&&HostSymbols.matches(m,"j");
                if(!request&&!route)continue;
                module.hook(m).intercept(chain->{if(!host.on("enabled")||!host.on("hide_widget_prompt"))return chain.proceed();host.log("Widget permission blocked: "+m.getName());return request?Boolean.FALSE:null;});hits++;
            }
        }catch(Throwable error){host.log("Widget permission FAILED: "+error);}
        host.log("Widget prompt hooks: " + hits);
    }
}
