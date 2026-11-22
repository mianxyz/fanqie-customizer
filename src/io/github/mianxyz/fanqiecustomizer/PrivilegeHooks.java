package io.github.mianxyz.fanqiecustomizer;

import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/**
 * 听书 / 自动阅读的免广告与不中断。
 *
 * 背景（由 7.4.5.32 真实 APK 反汇编确认）：
 * 宿主把「听一段就停、弹窗看广告才能继续、看完 30 分钟免广告」整套逻辑
 * 收敛在 {@code IPrivilegeManager} 这一个权限接口上：
 *
 *   hasAutoPagePrivilege()        自动阅读是否还有额度
 *   consumeReadPrivilege(J,...)   阅读额度消耗
 *   hasTtsConsumptionPrivilege()  听书消费权限
 *   consumeTtsPrivilege(J,...)    听书额度消耗
 *   hasNoAdPrivilege()            免广告
 *   isNoAd(String)                按场景判定免广告
 *   isForeverNoAd()               永久免广告
 *   hasNoAdFollAllScene()         全场景免广告
 *   hasNoAdForShortSeries()       短剧免广告
 *
 * 三个权限类在 7.3.9.32 / 7.4.1.x / 7.4.3.32 / 7.4.5.32 以及 Play 版
 * （com.dragon.read.oversea.gp）中全部存在，因此本 Hook 对全部受支持版本生效。
 *
 * 实现方式：只让「判定」恒为已授权，不改写任何网络响应、不伪造服务端权益。
 * 消耗类方法保持原样调用——额度不消耗，等于不会触发中断弹窗。
 */
final class PrivilegeHooks {
    private static final String PRIVILEGE = "com.dragon.read.component.biz.api.manager.privilege.IPrivilegeManager";
    /** 反汇编确认的实现类。接口上的方法不可 hook，必须挂这里。 */
    private static final String IMPL = "com.dragon.read.component.biz.impl.privilege.PrivilegeManager";

    /**
     * 恒为 true 的判定白名单 —— 严格限定在「免广告 + 听书/自动阅读额度」。
     *
     * 反汇编（classes3.dex PrivilegeManager，79 个方法）后逐一甄别：
     * 下列 9 个只回答「本次广告/额度是否还要拦截」，不影响账号身份、
     * 不影响推荐流、不影响风控。
     *
     * 已刻意排除的高风险方法（曾考虑过，现在明确不碰）：
     *   hasPrivilege / isVip / isAnyVip / getAllVipInfo / updateVipInfo
     *       → VIP 身份判定，会连锁影响推荐、权益与风控评分
     *   adVipAvailable
     *       → 广告 VIP 可用性，被广告投放策略直接引用
     *   hasInspireBookPrivilege / checkIsInInspiresBooks / getInspiresBook
     *       → 推荐位（神作/灵感书）判定，直接决定推荐流内容
     *   checkCommentForbidden / canShowVipRelational / canReadShortStory
     *       → 社交与风控相关
     *   getPrivilegeJson / updatePrivileges / onUserInfoUpdateFail
     *       → 权益同步链路，改写会破坏登录态缓存
     */
    private static final String[] ALWAYS_TRUE = {
        "hasAutoPagePrivilege",
        "hasTtsConsumptionPrivilege",
        "hasTtsPrivilege",
        "hasTtsNaturePrivilege",
        "hasTtsNewUserPrivilege",
        "hasNoAdPrivilege",
        "hasNoAdFollAllScene",
        "hasNoAdForShortSeries",
        "hasNoAdReadConsumptionPrivilege",
        "isForeverNoAd",
    };

    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;

    PrivilegeHooks(XposedModule module, FeatureHooks.Host host, ClassLoader loader) {
        this.module = module;
        this.host = host;
        this.loader = loader;
    }

    void install() {
        int hits = 0;
        // 只挂实现类。
        //
        // 实机日志（平板 7.3.9.32）确认：对 interface 上声明的方法调用
        // module.hook() 会抛 IllegalArgumentException，导致 11 个方法
        // 全部注册失败（Privilege hooks registered: 0）。
        // 实现类是 com.dragon.read.component.biz.impl.privilege.PrivilegeManager。
        hits += hookClass(IMPL);
        host.log("Privilege hooks registered: " + hits);
    }

    private int hookClass(String className) {
        Class<?> cls;
        try {
            cls = HostSymbols.type(className,loader);
        } catch (Throwable missing) {
            return 0;
        }
        int count = 0;
        for (Method method : cls.getDeclaredMethods()) {
            if (method.getReturnType() != boolean.class) continue;
            if (method.getParameterTypes().length != 0) continue;
            if (!wanted(method)) continue;
            try {
                module.hook(method).intercept(chain -> Boolean.TRUE);
                count++;
            } catch (Throwable error) {
                host.log("Privilege hook failed: " + method.getName() + " " + PrivacyRules.errorType(error));
            }
        }
        // isNoAd(String) / isBookAdFree(String) 是按书判定，
        // 同样只回答「这本书要不要出广告」，不涉及推荐与账号。
        for (Method method : cls.getDeclaredMethods()) {
            String name = method.getName();
            if (!HostSymbols.matches(method,"isNoAd") && !HostSymbols.matches(method,"isBookAdFree")) continue;
            if (method.getReturnType() != boolean.class && !HostSymbols.matches(method,"isBookAdFree")) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || params[0] != String.class) continue;
            try {
                module.hook(method).intercept(chain -> Boolean.TRUE);
                count++;
            } catch (Throwable error) {
                host.log("Privilege isNoAd hook failed: " + PrivacyRules.errorType(error));
            }
        }
        return count;
    }

    private static boolean wanted(Method method) {
        for (String candidate : ALWAYS_TRUE) if (HostSymbols.matches(method,candidate)) return true;
        return false;
    }
}
