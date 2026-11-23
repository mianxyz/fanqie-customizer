package io.github.mianxyz.fanqiecustomizer;

import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 番茄底栏玻璃化：真实高斯模糊 + 内容透出。
 *
 * 三个此前失效的根因与对应修法：
 *   1. 内容区被底栏上沿裁断 → 内容容器底部 margin 归零并 MATCH_PARENT，
 *      底栏用 translationZ 抬到内容之上，封面才能真正滚到它背后；
 *   2. 只画 GradientDrawable 没有真模糊 → API31+ 用 RenderEffect.createBlurEffect
 *      做真实高斯模糊，再叠半透明着色 + 镜面高光；
 *   3. 宿主白色底槽与矩形白边 → 底栏自身/父链/子层背景统一置透明，
 *      并用 ViewOutlineProvider + clipToOutline 真正裁出圆角。
 */
final class GlassSurfaces {
    private static final String BOTTOM = "com.dragon.read.widget.BottomTabFrameLayout";
    private static final String TOP = "com.dragon.read.widget.titlebar.TitleBar";
    /**
     * 书城头部渐变背景。
     *
     * 真实全限定名由 dextool 在 classes15.dex 中按短名搜出：
     *   Lcom/dragon/read/component/biz/impl/bookmall/widge/BookstoreHeaderBgView;
     * 早前记的 com.dragon.read.widget.BookstoreHeaderBgView 是错的，
     * 实机日志因此报 "Glass top missing"。
     */
    private static final String HEADER = "com.dragon.read.component.biz.impl.bookmall.widge.BookstoreHeaderBgView";
    /**
     * 真正承载书城全部彩色内容的分页容器。
     *
     * 真实名：Lcom/dragon/read/component/biz/impl/ui/global/InterceptSelectViewPager;
     * 它不在底栏的兄弟节点上，而在更深的 ViewPager 分支里——
     * 之前把 MATCH_PARENT 打在底栏兄弟容器上（stretched=4）完全没碰到它，
     * 所以底栏背后始终是窗口白底。这里专门按类名定位它并逐层拉通。
     */
    private static final String PAGER = "com.dragon.read.component.biz.impl.ui.global.InterceptSelectViewPager";
    /**
     * 书城真正可见的顶栏容器。
     *
     * 实机 dumpsys 坐标树（3827 行）实测：
     *   AppBarLayout{...7f1107c4 app:id/anw}      0,0-2000,277
     *     CollapsingToolbarLayout                 0,0-2000,277
     *       LinearLayout(id=dua)                  0,0-2000,277
     *         LinearLayout(id=dug) 0,0-2000,145   ← 搜索栏
     *         LinearLayout(id=ft9) 0,0-2000,163   ← 频道栏
     * 之前画在 BookstoreHeaderBgView（0,0-2000,3200 全屏底图）上，
     * 被上层所有内容盖住，所以永远看不见。
     */
    private static final String APP_BAR = "com.google.android.material.appbar.AppBarLayout";
    /** 底栏安全高度上限：超过这个高度的父容器一律不动，避免把根容器透明化。 */
    private static final int BAR_REGION_MAX_PX = 300;
    /**
     * View 树递归深度上限，统一 80。
     *
     * 实机坐标树证明 SuperSwipeRefreshLayout 埋在 30 层以上，
     * 之前各处分别写 8 / 24 / 40，导致 find() 与 fixRefreshLayouts()
     * 各自踩空（日志里 refreshFixed=0 就是这么来的）。
     */
    private static final int MAX_DEPTH = 80;
    /** 离屏录制降采样比例：0.5 提速并让模糊更柔和。 */
    private static final float SCALE = 0.5f;
    private static final String BOTTOM_SCALE = "com.dragon.read.widget.BottomTabScaleLayout";
    /** 宿主下拉容器：承载全部滚动内容，本文件多处按类名定位。 */
    private static final String HOST_REFRESH = "com.dragon.read.widget.refresh.SuperSwipeRefreshLayout";
    private static final String BOTTOM_BAR = "com.dragon.read.widget.BottomTabBarLayout";

    private static final Set<View> painted =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static final Set<View> arranged =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));
    private static final Set<View> cleared =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));

    /**
     * 当前扫描使用的根视图。
     *
     * 不能用 bar.getRootView()：实机出现 refreshFixed=0 —— 底栏与
     * SuperSwipeRefreshLayout 在 MainFragmentActivity 的不同分支里，
     * 从底栏向上取根不一定能覆盖到书城那一支。统一用 decorView 才可靠。
     */
    private View scanRoot;

    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;

    /**
     * 宿主 Application。
     *
     * 必须由 ModuleHook 在 Application.attach 回调里直接传进来。
     * 之前靠反射读 ActivityThread.mInitialApplication 恒为 null：
     * 该字段要等 Application.attachBaseContext 之后才由系统赋值，
     * 而模块在 attach 回调里就开始安装 hook，时机更早。
     * 实机日志 "Glass lifecycle FAILED: no Application" 就是这个原因。
     */
    private final android.app.Application app;

    GlassSurfaces(XposedModule module, FeatureHooks.Host host, ClassLoader loader,
            android.app.Application app) {
        this.module = module;
        this.host = host;
        this.loader = loader;
        this.app = app;
    }

    void install() {
        boolean bar = host.on("glass_bottom_bar_floating") || host.on("glass_bottom_bar_docked");
        int hits = bar ? hookBottom() : 0;
        if (bar) { installSwipeHook(); hits += hookSkinWrites(); }
        if (host.on("glass_top_bar")) hits += hookTop();
        host.log("Glass surfaces hooks: " + hits
                + " floating=" + host.on("glass_bottom_bar_floating")
                + " docked=" + host.on("glass_bottom_bar_docked")
                + " api=" + Build.VERSION.SDK_INT);
    }

    /**
     * 入口：原生 ActivityLifecycleCallbacks，不再 Hook onResume。
     *
     * 为什么换：5.3.23 时 onResume 能触发（Glass scan: found=2），
     * 5.3.24 起同一份 hook 注册成功（Glass surfaces hooks: 3）却再也没回调，
     * 且 5.3.25 连 "Glass scan start" 都打不出来。实测已排除熄屏因素
     * （mWakefulness=Awake / mScreenOn=true）。与其继续猜 Xposed hook 为何丢失，
     * 不如改用 Android 原生生命周期回调——它不经过任何 Hook 框架，零失败率。
     */
    private int hookBottom() {
        try {
            if (app == null) {
                host.log("Glass lifecycle FAILED: app is null");
                return 0;
            }
            app.registerActivityLifecycleCallbacks(
                    new android.app.Application.ActivityLifecycleCallbacks() {
                        @Override public void onActivityCreated(android.app.Activity a,
                                android.os.Bundle b) { }
                        @Override public void onActivityStarted(android.app.Activity a) { }
                        @Override public void onActivityResumed(android.app.Activity a) {
                            if (!"com.dragon.read.pages.main.MainFragmentActivity"
                                    .equals(a.getClass().getName())) return;
                            // 三时机触发：立即 / 首帧布局后 / 800ms 后（Fragment 与
                            // ViewPager 晚挂载，实机 h=0 与 refreshFixed=0 都源于扫早了）。
                            refreshGlassConfig(a);
                            watchTargetPages(a);
                            scan(a);
                            View root = a.getWindow().getDecorView();
                            if (root == null) return;
                            root.post(() -> scan(a));
                            root.postDelayed(() -> scan(a), 800L);
                        }
                        @Override public void onActivityPaused(android.app.Activity a) { }
                        @Override public void onActivityStopped(android.app.Activity a) { }
                        @Override public void onActivitySaveInstanceState(android.app.Activity a,
                                android.os.Bundle b) { }
                        @Override public void onActivityDestroyed(android.app.Activity a) { }
                    });
            host.log("Glass hooking: ActivityLifecycleCallbacks registered");
            return 1;
        } catch (Throwable error) {
            host.log("Glass lifecycle FAILED: " + error.getClass().getName()
                    + ": " + error.getMessage());
            return 0;
        }
    }

    /**
     * 阻断宿主切 Tab 时把底栏刷回纯白。
     *
     * 实机现象：点击底栏切 Tab 后底栏立刻变纯白、边框消失。
     * 反汇编/反射确证 BottomTabFrameLayout 有 setBottomTabBackground(int)，
     * 宿主在切 Tab 与皮肤加载完成时都会调用它。直接拦掉返回 null。
     */
    private int hookHostBackgroundWrite() {
        int count = 0;
        try {
            Class<?> cls = HostSymbols.type(BOTTOM, loader);
            for (Method m : cls.getDeclaredMethods()) {
                if (!"setBottomTabBackground".equals(m.getName())) continue;
                module.hook(m).intercept(chain -> {
                    // 玻璃底栏开启时，任何宿主背景写入都丢弃。
                    host.log("Glass blocked host setBottomTabBackground");
                    return null;
                });
                count++;
            }
        } catch (Throwable error) {
            host.log("Glass block bg FAILED: " + error.getClass().getName()
                    + ": " + error.getMessage());
        }
        return count;
    }

    /**
     * 阻断底栏换肤写背景（DEX 反汇编确证的确切写入点）。
     *
     * setBottomTabBackground(int) 的指令流：
     *   pc0   SkinManager.isNightMode()                 按深浅色分支
     *   pc67  FrameLayout.setBackgroundResource(int)    分支 A 直接设资源
     *   pc84  SkinDelegate.setBackground(View,int)      分支 B 走换肤
     *   pc136 com.dragon.read.base.util.j.h(Window,int) 同时改 window 背景色
     *
     * 之前一刀切拦 setBottomTabBackground 会让深色分支的黑底无人清理、
     * 切回浅色永久锁黑；不拦又直接盖死玻璃层。
     * 正解：让宿主逻辑照常跑完（状态机不变），只把「三处真正落地背景」的
     * 调用拦掉，这样 View 与 Window 都不会被刷成实心色。
     */
    private int hookSkinWrites() {
        int n = 0;
        // ① FrameLayout.setBackgroundResource(int) —— 仅当目标是底栏家族时拦
        try {
            Method m = View.class.getMethod("setBackgroundResource", int.class);
            module.hook(m).intercept(chain -> {
                Object self = chain.getThisObject();
                if (barEnabled() && !restoringBar && self != null && isBarFamily(self.getClass().getName())) return null;
                return chain.proceed();
            });
            n++;
        } catch (Throwable error) {
            host.log("Glass block bgRes FAILED: " + error.getClass().getName());
        }
        // ② SkinDelegate.setBackground(View,int)
        try {
            Class<?> sd = HostSymbols.type("com.dragon.read.base.skin.SkinDelegate", loader);
            Method m = sd.getMethod("setBackground", View.class, int.class);
            module.hook(m).intercept(chain -> {
                Object v = chain.getArgs().get(0);
                if (barEnabled() && !restoringBar && v != null && isBarFamily(v.getClass().getName())) return null;
                return chain.proceed();
            });
            n++;
            host.log("Glass block skinDelegate ok");
        } catch (Throwable error) {
            host.log("Glass block skinDelegate FAILED: " + error.getClass().getName());
        }
        // null is our cleanup operation; block only non-null host backgrounds.
        for (String method : new String[]{"setBackgroundColor", "setBackground"}) {
            try {
                Method m = View.class.getMethod(method,
                        "setBackgroundColor".equals(method) ? int.class : Drawable.class);
                module.hook(m).intercept(chain -> {
                    Object self = chain.getThisObject();
                    if (barEnabled() && !restoringBar && self != null && isBarFamily(self.getClass().getName())
                            && chain.getArgs().get(0) != null) return null;
                    return chain.proceed();
                });
                n++;
            } catch (Throwable error) {
                host.log("Glass block " + method + " FAILED: " + error.getClass().getName());
            }
        }
        return n;
    }

    /** 判断是否属于底栏家族（底栏自身与其两层容器）。 */
    private static boolean isBarFamily(String name) {
        return BOTTOM.equals(name) || BOTTOM_SCALE.equals(name) || BOTTOM_BAR.equals(name);
    }

    private int hookTop() {
        int ok = 0;
        for (String name : new String[]{TOP, HEADER}) {
            try {
                HostSymbols.type(name, loader);
                ok++;
            } catch (Throwable error) {
                host.log("Glass top missing: " + name);
            }
        }
        host.log("Glass top classes resolved: " + ok + "/2");
        return ok;
    }

    void scan(android.app.Activity activity){
        try {
            View root = activity.getWindow().getDecorView();
            scanRoot = root;
            host.log("Glass scan start: " + activity.getClass().getSimpleName()
                    + " root=" + (root != null));
            View cached=cachedBar(root);
            int found=0;if(cached!=null){applyToBar(cached,true);found=1;}
            host.log("Glass scan: found=" + found);
            // 诊断必须独立 try：之前把它跟在 sweep 后面，
            // 内部一抛异常整段日志就没了，反而更难定位。
            try {
                if(host.on("debug"))host.log(diagnose(root));
            } catch (Throwable diagError) {
                host.log("Glass diag ERR: " + diagError.getClass().getSimpleName()
                        + ": " + diagError.getMessage());
            }
            // onResume 时 View 树可能还没挂完（实机出现过 found=0），
            // 布局完成后再扫一次，保证底栏与顶栏一定能被处理。
            if (root != null && found == 0) {
                root.post(() -> {
                    try {
                        View retry=cachedBar(root);int again=0;if(retry!=null){applyToBar(retry,true);again=1;}
                        host.log("Glass scan retry: found=" + again);
                    } catch (Throwable ignored) {
                    }
                });
            }
        } catch (Throwable error) {
            host.log("Glass scan FAILED: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    /**
     * 单行诊断：一次装机就看穿全部真凶状态，避免反复装机猜。
     * 输出 SuperSwipeRefreshLayout 的 [top,bottom,marginBottom,parentPadBottom]、
     * 底栏透明化层数、AppBarLayout 是否命中。
     */
    private String diagnose(View root) {
        StringBuilder sb = new StringBuilder("Glass diag:");
        try {
            View src = scanRoot != null ? scanRoot : root;
            View refresh = find(src, HOST_REFRESH, 0);
            if (refresh == null) {
                sb.append(" ssrl=NONE");
            } else {
                int mb = 0;
                if (refresh.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
                    mb = ((ViewGroup.MarginLayoutParams) refresh.getLayoutParams()).bottomMargin;
                }
                int ppb = 0;
                if (refresh.getParent() instanceof View) {
                    ppb = ((View) refresh.getParent()).getPaddingBottom();
                }
                sb.append(" ssrl=[").append(refresh.getTop()).append(',')
                        .append(refresh.getBottom()).append(',').append(mb).append(',')
                        .append(ppb).append(']');
                View list = firstScrollable(refresh, 0);
                if (list != null) {
                    sb.append(" listPadB=").append(list.getPaddingBottom())
                            .append(" listClip=").append(
                                    list instanceof ViewGroup
                                            && ((ViewGroup) list).getClipToPadding());
                }
            }
            View bar = find(src, BOTTOM, 0);
            sb.append(" bar=").append(bar != null ? "hit" : "MISS");
            sb.append(" cleared=").append(cleared.size());
            sb.append(" appbar=").append(find(src, APP_BAR, 0) != null ? "hit" : "MISS");
            sb.append(" screenH=").append(src != null ? src.getHeight() : -1);
        } catch (Throwable error) {
            sb.append(" ERR=").append(error.getClass().getSimpleName());
        }
        return sb.toString();
    }

    private final java.util.Map<View,java.lang.ref.WeakReference<View>> discoveredBars=new WeakHashMap<>();
    private View cachedBar(View root){java.lang.ref.WeakReference<View> ref=discoveredBars.get(root);View bar=ref==null?null:ref.get();if(bar!=null&&bar.isAttachedToWindow()&&bar.getRootView()==root)return bar;bar=find(root,BOTTOM,0);if(bar!=null)discoveredBars.put(root,new java.lang.ref.WeakReference<>(bar));else discoveredBars.remove(root);return bar;}
    private int sweep(View view, int depth) {
        if (depth > MAX_DEPTH || view == null) return 0;
        int found = 0;
        String cls = view.getClass().getName();
        if (BOTTOM.equals(cls)) {
            boolean isRoot = true;
            for(android.view.ViewParent a=view.getParent();a instanceof View;a=a.getParent())
                if(BOTTOM.equals(a.getClass().getName())){isRoot=false;break;}
            applyToBar(view, isRoot);
            found++;
        }
        if (!(view instanceof ViewGroup)) return found;
        ViewGroup g = (ViewGroup) view;
        for (int i = 0; i < g.getChildCount(); i++) {
            found += sweep(g.getChildAt(i), depth + 1);
        }
        return found;
    }

    /**
     * 顶栏磨砂渐变。
     *
     * 目标是 AppBarLayout（0,0-2000,277，搜索栏 + 频道栏所在）。
     * 用「上浓下透」的自适应渐变：既保证状态栏与搜索栏区域可读，
     * 又让滚到它背后的彩色榜单被柔和压暗，形成可见的顶部毛玻璃过渡。
     */
    private void applyToBar(View bar, boolean isRoot) {
        if (!isRoot) return; // One glass shell: nested host frames must not receive another margin.
        rememberBarTree(bar, 0);
        ensureBarReady(bar);
        if (!barEnabled()) { restoreBarTree(bar, 0); return; }
        boolean floating = !dockedStyle();
        clearSlot(bar);
        // 防锁死：lift 内部若一个 SuperSwipeRefreshLayout 都没命中（refreshFixed=0），
        // 说明这一帧 View 树还没长全，不能把 arranged 标记为已完成，
        // 否则后续补扫会被 add() 挡掉，内容永远拉不通。
        if (!arranged.contains(bar)) {
            if (lift(bar, floating)) arranged.add(bar);
        }
        // 零尺寸时不加入 painted：否则 paint() 会按 0x0 建 Outline 与模糊层，
        // 之后再也不会重画（顶栏 h=0 就是同类问题）。
        if (bar.getWidth() > 0 && bar.getHeight() > 0 && painted.add(bar)) {
            paint(bar, floating);
        }
        if (bar.getWidth() > 0 && bar.getHeight() > 0) { updateBarStyleGeometry(bar); updateBarWidth(bar); fitTabTrack(bar); }
    }

    private final java.util.Map<View, BarReadyState> barReady = new WeakHashMap<>();
    private static final class BarReadyState { boolean queued; }
    private void ensureBarReady(final View bar) {
        if (barReady.containsKey(bar)) return;
        final BarReadyState state = new BarReadyState();
        barReady.put(bar, state);
        Runnable initialize = () -> queueBarReady(bar, state);
        bar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob) -> initialize.run());
        bar.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View v) { bindBarRedraw(v); initialize.run(); }
            @Override public void onViewDetachedFromWindow(View v) { resetLiquidPress(v,pills.get(v),"detach"); unbindBarRedraw(v); restoreLiquidClipping(); nativeTabCache.remove(v);nativeTabShapes.remove(v);tabTracks.remove(v);suspendedBars.remove(v); }
        });
        // Register independently of paint: a zero-size discovery must still observe its first layout.
        watchRedraw(bar);
        initialize.run();
    }
    private void queueBarReady(final View bar, final BarReadyState state) {
        if (state.queued || painted.contains(bar) || !bar.isAttachedToWindow()
                || bar.getWidth() <= 0 || bar.getHeight() <= 0) return;
        state.queued = true;
        bar.post(() -> {
            state.queued = false;
            if (!bar.isAttachedToWindow() || bar.getWidth() <= 0 || bar.getHeight() <= 0) return;
            applyToBar(bar, true);
            host.log("Glass bar ready: painted=" + painted.contains(bar) + " size=" + bar.getWidth() + "x" + bar.getHeight());
        });
    }

    /**
     * 清除底栏白色底槽。
     *
     * 安全边界（必须遵守）：只对「高度明显属于底栏区域」的容器透明化。
     * 实机坐标树里底栏父链上有大量 0,0-0,0 的 ConstraintLayout，
     * 继续往上就是 0,0-2000,3200 的全屏根容器——把根容器透明化
     * 会让整个窗口露出黑底，那样比白槽更糟。
     */
    private void clearSlot(View bar){
        // ① 底栏自身与两个已知子层：无条件透明化。
        for (View v : new View[]{bar, child(bar, BOTTOM_SCALE), child(bar, BOTTOM_BAR)}) {
            if (v == null || !cleared.add(v)) continue;
            try {
                v.setBackgroundColor(Color.TRANSPARENT);
            } catch (Throwable ignored) {
            }
        }
        // ② 父链：只在高度属于底栏区域时才动，最多 3 层。
        View pv = bar.getParent() instanceof View ? (View) bar.getParent() : null;
        for (int i = 0; i < 3 && pv != null; i++) {
            final View cur = pv;
            final int h = cur.getHeight();
            // h == 0 表示尚未测量，等布局完成后再判断。
            if (h > 0 && h <= BAR_REGION_MAX_PX && cleared.add(cur)) {
                try {
                    if(!parentBackgrounds.containsKey(cur))parentBackgrounds.put(cur,cur.getBackground());
                    cur.setBackgroundColor(Color.TRANSPARENT);
                } catch (Throwable ignored) {
                }
            } else if (h == 0) {
                cur.post(() -> {
                    try {
                        if (cur.getHeight() > 0 && cur.getHeight() <= BAR_REGION_MAX_PX
                                && cleared.add(cur)) {
                            if(!parentBackgrounds.containsKey(cur))parentBackgrounds.put(cur,cur.getBackground());
                    cur.setBackgroundColor(Color.TRANSPARENT);
                        }
                    } catch (Throwable ignored) {
                    }
                });
            }
            // clip 相关无论高度都可以放开：它不影响绘制内容，只影响裁剪。
            try {
                if (cur instanceof ViewGroup) {
                    ViewGroup vg = (ViewGroup) cur;
                    vg.setClipChildren(false);
                    vg.setClipToPadding(false);
                }
            } catch (Throwable ignored) {
            }
            pv = cur.getParent() instanceof View ? (View) cur.getParent() : null;
        }
    }

    private View child(View parent, String cls) {
        if (!(parent instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) parent;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c != null && cls.equals(HostSymbols.canonical(c.getClass().getName()))) return c;
        }
        return null;
    }

    /**
     * 方向 1：从 InterceptSelectViewPager 起，向上逐层解除底部占位约束。
     *
     * 为什么要逐层：实机 dump 显示底栏的兄弟节点只有头部渐变层，
     * 书城列表在 ViewPager 分支深处（stretched=4 打在了错误的节点上）。
     * 这里从 pager 自己出发，把每一层父容器的 bottom 约束与高度统一放开，
     * 内容才能真正铺到屏幕最后一行像素，底栏才有东西可以透出来。
     */
    private boolean lift(View bar, boolean floating){
        boolean ok = false;
        try {
            float density = bar.getResources().getDisplayMetrics().density;
            int margin = floating ? dp(liveInt("glass_bar_margin"), density) : 0;
            int bottom = floating ? dp(liveInt("glass_bar_bottom"), density) : 0;

            // ① 底栏自身：外边距 + 抬到内容之上。
            ViewGroup.LayoutParams lp = bar.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) lp;
                if(mp.leftMargin!=margin||mp.rightMargin!=margin||mp.bottomMargin!=bottom){
                    mp.leftMargin = margin;mp.rightMargin = margin;mp.bottomMargin = bottom;bar.setLayoutParams(mp);
                }
            }
            // 彻底关闭原生投影。
            // 半透明玻璃 View 上的 setElevation 会：① 把胶囊内部熏脏；
            // ② 阴影被父容器矩形边界硬切成锐利的黑色长方形框。
            // 柔和悬浮感改由 GlassBackdropView 自己画的圆角描边体现。
            bar.setElevation(0f);
            if (Build.VERSION.SDK_INT >= 21) bar.setTranslationZ(0f);
            if (Build.VERSION.SDK_INT >= 28) {
                bar.setOutlineAmbientShadowColor(Color.TRANSPARENT);
                bar.setOutlineSpotShadowColor(Color.TRANSPARENT);
            }
            bar.bringToFront();

            // ② 承载封面的 SuperSwipeRefreshLayout 自己不是全屏，
            //    实机实测 0,305-2000,3074（底=3074，比屏幕少 126 = 底栏高）。
            //    它是内容能否穿过底栏的关键，单独处理，并给内层列表留安全区。
            View root = scanRoot != null ? scanRoot : bar.getRootView();
            int refreshFixed = fixRefreshLayouts(root, bar);

            // ③ 找到真正的页面容器，从它向上拉通。
            View pager = find(root, PAGER, 0);
            if (pager != null) watchPagerPadding(pager, bar);
            int layers = 0;
            if (pager != null) {
                for (View v = pager; v != null && layers < 8; layers++) {
                    stretchToBottom(v);
                    android.view.ViewParent parent = v.getParent();
                    v = parent instanceof View ? (View) parent : null;
                }
                // 同级兄弟里凡是盖在 pager 上方、把高度钉死在底栏之上的也一并放开。
                ViewGroup pageRoot = pager.getParent() instanceof ViewGroup
                        ? (ViewGroup) pager.getParent() : null;
                if (pageRoot != null) {
                    for (int i = 0; i < pageRoot.getChildCount(); i++) {
                        View sib = pageRoot.getChildAt(i);
                        if (sib != null && sib != pager && sib != bar) stretchToBottom(sib);
                    }
                }
            }
            host.log("Glass lift: floating=" + floating
                    + " margin=" + liveInt("glass_bar_margin")
                    + "dp bottom=" + liveInt("glass_bar_bottom")
                    + "dp pager=" + (pager != null) + " layers=" + layers
                    + " refreshFixed=" + refreshFixed);
            ok = refreshFixed > 0;
        } catch (Throwable error) {
            host.log("Glass lift FAILED: " + error.getClass().getName() + ": " + error.getMessage());
        }
        return ok;
    }

    /**
     * 放开单个容器的底部占位：
     * FrameLayout/LinearLayout → bottomMargin=0 且高度 MATCH_PARENT；
     * RelativeLayout → 移除 ABOVE 规则；
     * ConstraintLayout → bottomToBottom=PARENT、clearing bottomToTop。
     */
    private void stretchToBottom(View v) {
        try {
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp instanceof ViewGroup.MarginLayoutParams) {
                ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) lp;
                // 三个都要清：实机 SuperSwipeRefreshLayout 卡在 305..3074，
                // 正是 bottomMargin 与父级 paddingBottom 共同造成的。
                boolean changed = mp.bottomMargin != 0 || mp.height != ViewGroup.LayoutParams.MATCH_PARENT;
                mp.bottomMargin = 0;
                mp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                if (changed) v.setLayoutParams(mp);
            }
            if (v instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) v;
                // paddingBottom 会把内部列表顶上去，必须归零。
                if (vg.getPaddingBottom() != 0) {
                    vg.setPadding(vg.getPaddingLeft(), vg.getPaddingTop(),
                            vg.getPaddingRight(), 0);
                }
                // 严禁在这里 setClipChildren(false)：
                // SuperSwipeRefreshLayout 待机时刷新圆球藏在自身顶边之上，
                // 关掉裁剪会让它向上溢出，压在频道栏「短篇」上面（实机已复现）。
                // bottom 已拉通到 3200 + paddingBottom 归零，不需要关 clip 就能铺满。
                vg.setClipChildren(true);
                vg.setClipToPadding(true);
            }
            maybeFixConstraints(v);
        } catch (Throwable ignored) {
        }
    }

    /** ConstraintLayout：bottomToBottom=PARENT_ID，并清掉 bottomToTop。 */
    private void maybeFixConstraints(View v) {
        try {
            if (Build.VERSION.SDK_INT < 17) return;
            ViewGroup.LayoutParams lp = v.getLayoutParams();
            if (lp == null) return;
            if (!lp.getClass().getName().startsWith("androidx.constraintlayout")) return;
            Class<?> cst = Class.forName("androidx.constraintlayout.widget.ConstraintLayout");
            Object parent = v.getParent();
            if (parent == null || !cst.isInstance(parent)) return;
            java.lang.reflect.Method setSet = lp.getClass().getMethod("setBottomToBottom",
                    int.class);
            java.lang.reflect.Method setTop = lp.getClass().getMethod("setBottomToTop", int.class);
            setTop.invoke(lp, -1);
            setSet.invoke(lp, cst.getField("PARENT_ID").getInt(null));
            ((View) parent).requestLayout();
        } catch (Throwable ignored) {
        }
    }

    /**
     * 把每一个 SuperSwipeRefreshLayout 拉成全屏，并给内层列表留底部安全区。
     *
     * 实机坐标树证据：书城与书架的 SuperSwipeRefreshLayout 分别是
     *   0,305-2000,3074   与   0,277-2000,3041
     * 底边都比屏幕少 126（正好是底栏高度），说明内容在底栏上沿就被截断。
     * 把它们改成 MATCH_PARENT 并清掉 margin/padding，内容才能穿过底栏；
     * 同时给内部列表 paddingBottom = 底栏高 + 悬浮间距 + 余量，
     * 配合 clipToPadding=false，滚动到底时最后一项仍能完整露出。
     */
    private int fixRefreshLayouts(View root, View bar) {
        return fixRefreshLayouts(root, bar, 0);
    }

    private int fixRefreshLayouts(View root, View bar, int depth) {
        if (root == null || root == bar || depth > MAX_DEPTH || excludedListBranch(root) || ScrollWorkGate.busy(root)) return 0;
        if (HOST_REFRESH.equals(root.getClass().getName())) {
            try {
                clearRefreshBottom(root);
                if (root instanceof ViewGroup) {
                    ViewGroup g = (ViewGroup) root;
                    if (g.getPaddingBottom() != 0)
                        g.setPadding(g.getPaddingLeft(), g.getPaddingTop(), g.getPaddingRight(), 0);
                    g.setClipChildren(true);
                    g.setClipToPadding(true);
                }
                if (root.getParent() instanceof ViewGroup) {
                    ViewGroup parent = (ViewGroup) root.getParent();
                    parent.setClipChildren(true);
                    parent.setClipToPadding(true);
                }
                if (!isStoreView(root)) applyListSafety(root, bar);
                if (guarded.add(root)) {
                    root.addOnLayoutChangeListener((v, l, t, rr, b, ol, ot, or_, ob) -> {
                        boolean margin = v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams
                                && ((ViewGroup.MarginLayoutParams) v.getLayoutParams()).bottomMargin != 0;
                        boolean padding = v.getParent() instanceof View
                                && ((View) v.getParent()).getPaddingBottom() != 0;
                        if (margin || padding) clearRefreshBottom(v);
                    });
                }
                // Never scan a main list's ViewHolders for more refresh/list nodes.
                return 1;
            } catch (Throwable error) {
                host.log("Glass refresh fix FAILED: " + error.getClass().getName());
                return 0;
            }
        }
        if (isMainScrollType(root)) return 0;
        int fixed = 0;
        if (root instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) root;
            for (int i = 0; i < g.getChildCount(); i++)
                fixed += fixRefreshLayouts(g.getChildAt(i), bar, depth + 1);
        }
        return fixed;
    }

    private void clearRefreshBottom(View v) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (lp.bottomMargin != 0 || lp.height != ViewGroup.LayoutParams.MATCH_PARENT) {
                lp.bottomMargin = 0;
                lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                v.setLayoutParams(lp);
            }
        }
        if (v.getParent() instanceof View) {
            View parent = (View) v.getParent();
            if (parent.getPaddingBottom() != 0)
                parent.setPadding(parent.getPaddingLeft(), parent.getPaddingTop(), parent.getPaddingRight(), 0);
        }
    }

    /**
     * 悬浮底栏横向滑动切换 Tab（glass_bottom_bar_swipe）。
     *
     * 之前只加了白名单键、没有实现手势，所以开启后无任何效果。
     * 这里挂在 BottomTabBarLayout 的触摸回调上：
     *  · 水平位移超过 touchSlop 且大于垂直位移 → 拦截并跟手移动指示胶囊；
     *  · 松手按落点换算 Tab 索引，调 pager.setCurrentItem 真正切页；
     *  · 单击完全放行（不消费），不影响原有点击切 Tab。
     */
    private final java.util.Map<View,Boolean> suspendedBars=new WeakHashMap<>();
    private boolean operationOverlay(View view,int depth){
        if(view==null||depth>8||view.getVisibility()!=View.VISIBLE||view.getAlpha()<=.05f)return false;
        String name=view.getClass().getName();
        if(BOTTOM_SCALE.equals(name)||BOTTOM_BAR.equals(name)||view instanceof GlassBackdropView||view instanceof GlassPillView)return false;
        if(name.startsWith("com.dragon.read.component.biz.impl.bookshelf.booklayout.edit.")&&view.getWidth()>0&&view.getHeight()>0)return true;
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)if(operationOverlay(group.getChildAt(i),depth+1))return true;}
        return false;
    }
    private boolean suspendBar(View bar){
        Rect bounds=new Rect();boolean onScreen=bar.getGlobalVisibleRect(bounds)&&bounds.width()>0&&bounds.height()>0;
        boolean blocked=BarInteractionRules.suspend(bar.isShown(),bar.isEnabled(),bar.getAlpha(),onScreen,operationOverlay(bar,0));
        Boolean previous=suspendedBars.put(bar,blocked);
        if(previous==null||previous!=blocked){
            if(blocked){
                PillState state=pills.get(bar);if(state!=null){state.drag=false;state.pressed=false;state.pendingTargetIdx=-1;if(state.animator!=null)state.animator.cancel();if(state.velocityTracker!=null){state.velocityTracker.recycle();state.velocityTracker=null;}state.activity.target=0f;resetLiquidPress(bar,state,"paused");}
                if(bar.getParent()!=null)bar.getParent().requestDisallowInterceptTouchEvent(false);
                OriginalBarState original=originalBars.get(bar);if(original!=null){bar.setElevation(original.elevation);bar.setTranslationZ(original.z);}
                restoreLiquidClipping();
            }
            if(bar instanceof ViewGroup){ViewGroup group=(ViewGroup)bar;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child instanceof GlassBackdropView||child instanceof GlassPillView)child.setVisibility(blocked?View.GONE:View.VISIBLE);}}
            if(!blocked){updateBarStyleGeometry(bar);refreshPill(bar);}
            host.log("Glass bar interaction paused="+blocked+" vis="+bar.getVisibility()+" alpha="+bar.getAlpha()+" y="+bar.getTranslationY()+" elevation="+bar.getElevation()+" z="+bar.getTranslationZ());
        }
        return blocked;
    }
    private boolean pillDispatchHooked;
    private final java.util.Map<View,PillState> pills=new WeakHashMap<>();
    private void installSwipe(final View bar){
        if(!swiped.add(bar))return;
        pills.put(bar,new PillState());
        bar.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->refreshPill(v));
        ViewGroup tabs=tabTrack(bar);
        if(tabs!=null)tabs.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->refreshPill(bar));
        bar.getViewTreeObserver().addOnWindowFocusChangeListener(focus->{if(focus)refreshPill(bar);else resetLiquidPress(bar,pills.get(bar),"focus-lost");});
        installSwipeHook();
    }
    private void installSwipeHook() {
        if(pillDispatchHooked)return;
        try {
            Method dispatch=ViewGroup.class.getDeclaredMethod("dispatchTouchEvent",android.view.MotionEvent.class);
            module.hook(dispatch).intercept(chain->{
                Object receiver=chain.getThisObject();PillState state=pills.get(receiver);
                if(state==null||!(receiver instanceof View))return chain.proceed();
                View v=(View)receiver;android.view.MotionEvent e=(android.view.MotionEvent)chain.getArgs().get(0);
                if(suspendBar(v))return chain.proceed();
                if(visibleTabIndices(v,true).isEmpty())return chain.proceed();
                int action=e.getActionMasked();boolean enabled=barEnabled()&&glassFlag("glass_bottom_bar_swipe");
                if(!barEnabled())return chain.proceed();
                if(liquidStyle())liquidTouch(v,state,e);
                 if(action==0){state.downX=e.getX();state.downY=e.getY();state.drag=false;state.lastAction=0;state.startIdx=selectedTab(v);state.pendingTargetIdx=-1;if(state.animator!=null)state.animator.cancel();
                    if(liquidStyle())animatePill(v,slotAt(v,e.getX()),false);
                    if(enabled&&v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(true);
                    host.log("Glass pill swipe: action=DOWN from="+state.startIdx+" to="+slotAt(v,e.getX())+" pressedIdx="+slotAt(v,e.getX())+" activity="+state.activity.value+" activityTarget="+state.activity.target+" positionTarget="+state.position.target+" enabled="+enabled+" x="+e.getX()+" barW="+v.getWidth());return chain.proceed();}
                if(liquidStyle()&&(action==1||action==3)){
                    int to=action==3?state.startIdx:slotAt(v,e.getX());
                    boolean wasDrag=state.drag;state.drag=false;
                    if(v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(false);
                    if(!wasDrag){android.view.MotionEvent cancel=android.view.MotionEvent.obtain(e);cancel.setAction(3);try{chain.proceed(new Object[]{cancel});}finally{cancel.recycle();}}
                    if(action==1){state.pendingTargetIdx=to;state.pendingUntilMs=android.os.SystemClock.uptimeMillis()+400L;}
                    animatePill(v,to,true);
                    if(action==1&&to!=state.startIdx)clickTab(v,to);
                    v.postDelayed(()->reconcilePill(v),410L);
                    v.postDelayed(()->reconcilePill(v),16);v.postDelayed(()->reconcilePill(v),120);
                    if(action==3)resetLiquidPress(v,state,"cancel");
                    host.log("Glass pill swipe: action="+(action==1?"UP":"CANCEL")+" from="+state.startIdx+" to="+to);return true;
                }
                if(!enabled&&!state.drag){if(liquidStyle()&&(action==1||action==3)){if(action==1){state.pendingTargetIdx=slotAt(v,e.getX());state.pendingUntilMs=android.os.SystemClock.uptimeMillis()+400L;}animatePill(v,action==3?state.startIdx:slotAt(v,e.getX()),true);v.postDelayed(()->reconcilePill(v),410L);}if((action==1||action==3)&&v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(false);return chain.proceed();}
                if(action==2){float dx=e.getX()-state.downX,dy=e.getY()-state.downY;
                    if(!state.drag&&Math.abs(dx)>android.view.ViewConfiguration.get(v.getContext()).getScaledTouchSlop()&&Math.abs(dx)>Math.abs(dy)*1.1f){
                        state.drag=true;if(v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(true);
                        android.view.MotionEvent cancel=android.view.MotionEvent.obtain(e);cancel.setAction(3);
                        try{chain.proceed(new Object[]{cancel});}finally{cancel.recycle();}
                        host.log("Glass pill swipe: action=DRAG from="+selectedTab(v)+" to="+slotAt(v,e.getX()));
                    }
                    if(state.drag){state.center=clampDragCenter(v,e.getX());state.width=dragSlotWidth(v,state.center);refreshPill(v);return true;}
                }
                if((action==1||action==3)&&state.drag){int from=state.startIdx,to=action==3?state.startIdx:slotAt(v,e.getX());
                    state.drag=false;if(v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(false);
                    if(action==1){state.pendingTargetIdx=to;state.pendingUntilMs=android.os.SystemClock.uptimeMillis()+400L;}
                    animatePill(v,to,true);
                    if(action==1&&to!=from)clickTab(v,to);
                    v.postDelayed(()->reconcilePill(v),410L);
                    v.postDelayed(()->reconcilePill(v),16);v.postDelayed(()->reconcilePill(v),120);
                    host.log("Glass pill swipe: action="+(action==1?"UP":"CANCEL")+" from="+from+" to="+to);return true;
                }
                if(action==1||action==3){if(liquidStyle()&&action==1){state.pendingTargetIdx=slotAt(v,e.getX());state.pendingUntilMs=android.os.SystemClock.uptimeMillis()+400L;v.postDelayed(()->reconcilePill(v),410L);}if(v.getParent()!=null)v.getParent().requestDisallowInterceptTouchEvent(false);state.drag=false;animatePill(v,action==3?state.startIdx:slotAt(v,e.getX()),true);Object result=chain.proceed();v.postDelayed(()->reconcilePill(v),16);v.postDelayed(()->reconcilePill(v),120);return result;}
                return chain.proceed();
            });pillDispatchHooked=true;
        }catch(Throwable error){host.log("Glass pill gesture FAILED: "+error.getClass().getSimpleName());}
    }
    private static final class PillState {float downX,downY,center=Float.NaN,width=Float.NaN;boolean drag;int lastTab=-1,lastAction,startIdx,pendingTargetIdx=-1;long pendingUntilMs;android.animation.ValueAnimator animator;long started;float from,target,targetWidth;boolean pressed,frameQueued;Runnable frameCallback;final LiquidGlassRenderer.Spring barScale=new LiquidGlassRenderer.Spring(1f);long frameNanos;android.view.VelocityTracker velocityTracker;final LiquidGlassRenderer.Spring sx=new LiquidGlassRenderer.Spring(1f),sy=new LiquidGlassRenderer.Spring(1f),position=new LiquidGlassRenderer.Spring(0f);boolean positionReady;final LiquidGlassRenderer.Spring activity=new LiquidGlassRenderer.Spring(0f),direction=new LiquidGlassRenderer.Spring(0f);}
    private void liquidTouch(View bar,PillState state,android.view.MotionEvent event) {
        int action=event.getActionMasked();
        if(action==0){
            state.pressed=true;state.activity.target=1f;
            bar.setPivotX(bar.getWidth()*.5f);bar.setPivotY(bar.getHeight()*.5f);
            state.barScale.target=LiquidGlassRenderer.Rules.barPressScale(liveBounce>=0?liveBounce:host.intValue("glass_bar_bounce"));
            float origin=Float.isNaN(state.center)?slotCenter(bar,selectedTab(bar)):state.center;
            if(Float.isNaN(state.center)){state.center=origin;state.width=slotWidth(bar,selectedTab(bar));}
            state.direction.target=LiquidGlassRenderer.Rules.direction((slotCenter(bar,slotAt(bar,event.getX()))-origin)/Math.max(1f,slotWidth(bar,selectedTab(bar))));
            if(state.velocityTracker!=null)state.velocityTracker.recycle();
            state.velocityTracker=android.view.VelocityTracker.obtain();
            state.sx.target=state.sy.target=bounce().pressScale();
        }
        if(state.velocityTracker!=null){state.velocityTracker.addMovement(event);state.velocityTracker.computeCurrentVelocity(1000,8000f);}
        if(action==2&&state.pressed){
            float velocity=state.velocityTracker==null?0:state.velocityTracker.getXVelocity();
            float normalized=Math.min(1f,Math.abs(velocity)/Math.max(1f,bar.getWidth()*3f));
            state.direction.target=LiquidGlassRenderer.Rules.direction(velocity/Math.max(1f,bar.getWidth()*3f));
            state.sx.target=bounce().pressScale()+bounce().velocityScale()*normalized;state.sy.target=bounce().pressScale()-bounce().velocityScale()*normalized;
            if(state.drag){state.position.value=state.center;state.position.velocity=velocity;state.positionReady=true;}
        }
        if(action==1||action==3){
            state.pressed=false;state.activity.target=0f;state.direction.target=0f;state.sx.target=state.sy.target=1f;state.barScale.target=1f;
            if(state.velocityTracker!=null){if(state.drag){state.position.value=state.center;state.positionReady=true;state.position.velocity=state.velocityTracker.getXVelocity();}state.velocityTracker.recycle();state.velocityTracker=null;}
        }
        if(action==3){resetLiquidPress(bar,state,"cancel");return;}
        queueLiquidFrame(bar,state);
    }
    private void resetLiquidPress(View bar,PillState state,String reason) {
        if(state==null)return;
        if(state.frameCallback!=null)bar.removeCallbacks(state.frameCallback);
        state.frameCallback=null;state.frameQueued=false;state.frameNanos=0;state.pressed=false;state.drag=false;
        LiquidGlassRenderer.Rules.resetBarScale(state.barScale);bar.setScaleX(1f);bar.setScaleY(1f);
        state.sx.value=state.sy.value=state.sx.target=state.sy.target=1f;state.sx.velocity=state.sy.velocity=0f;
        state.activity.value=state.activity.target=state.activity.velocity=state.direction.value=state.direction.target=state.direction.velocity=0f;state.positionReady=false;
        if(state.velocityTracker!=null){state.velocityTracker.recycle();state.velocityTracker=null;}
        host.log("Glass bar scale reset reason="+reason+" barScale=1.0 queued=false");
    }
    private void queueLiquidFrame(View bar,PillState state) {
        if(state.frameQueued)return;state.frameQueued=true;
        state.frameCallback=()->{
            state.frameQueued=false;state.frameCallback=null;
            if(!bar.isAttachedToWindow()||!bar.hasWindowFocus()||!barEnabled()||Boolean.TRUE.equals(suspendedBars.get(bar))||!liquidStyle()){resetLiquidPress(bar,state,"inactive");return;}
            long now=System.nanoTime();float dt=state.frameNanos==0?1f/60f:(now-state.frameNanos)/1_000_000_000f;state.frameNanos=now;
            LiquidGlassRenderer.Rules.Bounce physics=bounce();
            state.sx.step(dt,250f,physics.sxDamping());state.sy.step(dt,250f,physics.syDamping());
            state.activity.step(dt,300f,.85f);state.direction.step(dt,250f,.85f);
            if(!state.drag&&state.positionReady){state.position.step(dt,300f,physics.posDamping());state.center=state.position.value;}
            LiquidGlassRenderer.Rules.stepBarScale(state.barScale,dt,liveBounce>=0?liveBounce:host.intValue("glass_bar_bounce"));
            bar.setScaleX(state.barScale.value);bar.setScaleY(state.barScale.value);
            refreshPill(bar);
            if(!state.barScale.settled()||!state.activity.settled()||!state.direction.settled()||!state.sx.settled()||!state.sy.settled()||(!state.drag&&state.positionReady&&!state.position.settled()))queueLiquidFrame(bar,state);
            else {state.frameNanos=0;host.log("Glass bar scale settled barScale="+state.barScale.value+" pressed="+state.pressed+" queued=false scaleX="+bar.getScaleX()+" scaleY="+bar.getScaleY());}
        };
        bar.postOnAnimation(state.frameCallback);
    }
    private final java.util.Map<ViewGroup,boolean[]> liquidClips=new WeakHashMap<>();
    private void updateLiquidClipping(View bar) {
        if(barEnabled()&&liquidStyle()){
            View view=bar;
            for(int i=0;i<32&&view instanceof ViewGroup;i++){
                ViewGroup group=(ViewGroup)view;
                liquidClips.computeIfAbsent(group,key->new boolean[]{key.getClipChildren(),key.getClipToPadding(),key.getClipToOutline()});
                group.setClipChildren(false);group.setClipToPadding(false);group.setClipToOutline(false);
                view=group.getParent() instanceof View?(View)group.getParent():null;
            }
        }else restoreLiquidClipping();
    }
    private void restoreLiquidClipping(){for(java.util.Map.Entry<ViewGroup,boolean[]> e:liquidClips.entrySet()){ViewGroup v=e.getKey();boolean[] a=e.getValue();v.setClipChildren(a[0]);v.setClipToPadding(a[1]);v.setClipToOutline(a[2]);}liquidClips.clear();}
    // Preserve native indices for page/source identity; filter every geometry and click operation.
    private final java.util.Map<View,java.util.Map<View,String>> nativeTabCache=new WeakHashMap<>();
    private final java.util.Map<View,Integer> nativeTabShapes=new WeakHashMap<>();
    private java.util.Map<View,String> nativeTabs(View bar) {
        java.util.Map<View,String> result=new java.util.LinkedHashMap<>();ViewGroup track=tabTrack(bar);
        int shape=track==null?0:System.identityHashCode(track);if(track!=null)for(int i=0;i<track.getChildCount();i++)shape=31*shape+System.identityHashCode(track.getChildAt(i));
        java.util.Map<View,String> cached=nativeTabCache.get(bar);if(cached!=null&&Integer.valueOf(shape).equals(nativeTabShapes.get(bar)))return cached;
        if(track==null)return result;
        try {
            Method list=track.getClass().getMethod(HostSymbols.publicMethodName(track.getClass(),"getTabButtonList"));
            Object raw=list.invoke(track);
            if(raw instanceof java.util.List<?>)for(Object button:(java.util.List<?>)raw){
                if(button==null)continue;
                Class<?> api=HostSymbols.type("dk7.g",loader);
                View view=(View)api.getMethod(HostSymbols.publicMethodName(api,"getView")).invoke(button);
                Object type=api.getMethod(HostSymbols.publicMethodName(api,"a")).invoke(button);
                if(view!=null)result.put(view,type instanceof Enum<?>?((Enum<?>)type).name():null);
            }
        }catch(Throwable ignored) { }
        if(!result.isEmpty()){nativeTabCache.put(bar,result);nativeTabShapes.put(bar,shape);}
        return result;
    }
    private java.util.List<Integer> visibleTabIndices(View bar,boolean measured) {
        java.util.List<Integer> result=new java.util.ArrayList<>();ViewGroup track=tabTrack(bar);
        if(track==null)return result;java.util.Map<View,String> nativeViews=nativeTabs(bar);
        java.util.Set<String> hidden=new java.util.HashSet<>();for(String key:Rules.TAB_KEYS)if(host.on(key))hidden.add(key);
        java.util.List<View> views=new java.util.ArrayList<>(nativeViews.keySet());
        boolean[] mask=BottomNavigationRules.hidden(host.on("enabled"),new java.util.ArrayList<>(nativeViews.values()),hidden);
        for(int i=0;i<track.getChildCount();i++){
            View child=track.getChildAt(i);int n=views.indexOf(child);
            boolean main=n>=0 || (nativeViews.isEmpty()&&child instanceof ViewGroup&&clickableTab(child,0)!=null);
            if(BottomNavigationRules.visibleSlot(main,child instanceof GlassBackdropView||child instanceof GlassPillView,child.getVisibility(),child.getWidth(),n>=0&&mask[n],measured))result.add(i);
        }
        return result;
    }
    private boolean visibleTab(View bar,int index){return visibleTabIndices(bar,true).contains(index);}
    private float slotWidth(View bar,int index){ViewGroup track=tabTrack(bar);return track!=null&&visibleTab(bar,index)?track.getChildAt(index).getWidth():0f;}
    private void animatePill(View bar,int index,boolean force){
        PillState state=pills.computeIfAbsent(bar,key->new PillState());if(state.drag||!visibleTab(bar,index))return;
        float target=slotCenter(bar,index),width=slotWidth(bar,index);
        if(!force&&state.lastTab==index&&Math.abs(state.target-target)<0.5f&&Math.abs(state.targetWidth-width)<0.5f)return;
        if(state.animator!=null)state.animator.cancel();
        float start=Float.isNaN(state.center)?target:state.center,startWidth=Float.isNaN(state.width)?width:state.width;
        state.lastTab=index;state.target=target;state.targetWidth=width;
        if(liquidStyle()){if(force&&!state.pressed){state.sx.value=Math.max(state.sx.value,LiquidGlassRenderer.Rules.bounceMap(liveBounce>=0?liveBounce:host.intValue("glass_bar_bounce"),1.04f,1.12f,1.18f));state.sy.value=Math.max(state.sy.value,LiquidGlassRenderer.Rules.bounceMap(liveBounce>=0?liveBounce:host.intValue("glass_bar_bounce"),1.04f,1.12f,1.18f));state.sx.target=state.sy.target=1f;}if(!state.positionReady){state.position.value=start;state.positionReady=true;}state.position.target=target;state.width=width;queueLiquidFrame(bar,state);return;}
        android.animation.ValueAnimator animator=android.animation.ValueAnimator.ofFloat(0f,1f);state.animator=animator;animator.setDuration(170);animator.setInterpolator(new android.view.animation.PathInterpolator(0.2f,0f,0f,1f));
        animator.addUpdateListener(value->{float f=(Float)value.getAnimatedValue();state.center=start+(target-start)*f;state.width=startWidth+(width-startWidth)*f;refreshPill(bar);});animator.start();
        host.log("Glass pill snap: tab="+index+" center="+target+" width="+width);
    }
    private int reconciledTab(View bar,PillState state){
        int actual=selectedTab(bar);
        if(state.pendingTargetIdx>=0&&!visibleTab(bar,state.pendingTargetIdx))state.pendingTargetIdx=-1;
        if(state.pendingTargetIdx>=0){
            if(actual==state.pendingTargetIdx){state.pendingTargetIdx=-1;return actual;}
            if(android.os.SystemClock.uptimeMillis()<state.pendingUntilMs)return state.pendingTargetIdx;
            state.pendingTargetIdx=-1;
        }
        return actual;
    }
    private float dragSlotWidth(View bar,float center){return slotWidth(bar,slotAt(bar,center));}
    private float clampDragCenter(View bar,float x){java.util.List<Integer> visible=visibleTabIndices(bar,true);if(visible.isEmpty())return 0f;return Math.max(slotCenter(bar,visible.get(0)),Math.min(slotCenter(bar,visible.get(visible.size()-1)),x));}
    private void reconcilePill(View bar){PillState state=pills.get(bar);if(state!=null&&!state.drag&&!state.pressed)animatePill(bar,reconciledTab(bar,state),false);}
    private View clickableTab(View v,int depth){if(v==null||depth>8||v.getVisibility()!=View.VISIBLE)return null;if(v.hasOnClickListeners())return v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){View hit=clickableTab(g.getChildAt(i),depth+1);if(hit!=null)return hit;}}return v.isClickable()?v:null;}
    private void clickTab(View bar,int index){ViewGroup track=tabTrack(bar);if(track==null||!visibleTab(bar,index))return;View click=clickableTab(track.getChildAt(index),0);boolean clicked=click!=null&&click.performClick();host.log("Glass pill click: target="+index+" clicked="+clicked);}
    private final java.util.Map<View,GlassPillView> pillViews=new WeakHashMap<>();
    private void refreshPill(View bar){GlassPillView pill=pillViews.get(bar);if(pill!=null)pill.postInvalidateOnAnimation();}
    private final class GlassPillView extends View {
        private final View bar;
        GlassPillView(View bar){super(bar.getContext());this.bar=bar;setWillNotDraw(false);setClickable(false);setFocusable(false);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        private final LiquidGlassRenderer liquid = new LiquidGlassRenderer(host::log);
        private RenderNode lensNode;
        void resetLiquid(){PillState state=pills.get(bar);if(state!=null&&state.velocityTracker!=null){state.velocityTracker.recycle();state.velocityTracker=null;}liquid.reset();if(lensNode!=null){lensNode.setRenderEffect(null);lensNode.discardDisplayList();}invalidate();}
        @Override protected void onDetachedFromWindow(){resetLiquid();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){drawPill(canvas,bar,getWidth(),getHeight()-(dockedStyle()?bar.getPaddingBottom():0),isDark(bar));}
    }

    private final java.util.Map<View,java.lang.ref.WeakReference<ViewGroup>> tabTracks=new WeakHashMap<>();
    private ViewGroup tabTrack(View bar){java.lang.ref.WeakReference<ViewGroup> ref=tabTracks.get(bar);ViewGroup cached=ref==null?null:ref.get();if(cached!=null&&cached.isAttachedToWindow()&&cached.getRootView()==bar.getRootView())return cached;View track=find(bar,BOTTOM_BAR,0);if(track instanceof ViewGroup)tabTracks.put(bar,new java.lang.ref.WeakReference<>((ViewGroup)track));return track instanceof ViewGroup?(ViewGroup)track:null;}
    private int selectedTab(View bar){ViewGroup track=tabTrack(bar);java.util.List<Integer> visible=visibleTabIndices(bar,true);
        if(track!=null)for(int i:visible)if(track.getChildAt(i).isSelected()||selectedTitle(track.getChildAt(i),0))return i;
        PillState state=pills.get(bar);return state!=null&&visible.contains(state.lastTab)?state.lastTab:visible.isEmpty()?-1:visible.get(0);}
    private boolean isDescendant(View root,View target,int depth){if(root==target)return true;if(depth>8||!(root instanceof ViewGroup))return false;ViewGroup g=(ViewGroup)root;for(int i=0;i<g.getChildCount();i++)if(isDescendant(g.getChildAt(i),target,depth+1))return true;return false;}
    private float slotCenter(View bar,int index){ViewGroup track=tabTrack(bar);if(track==null||!visibleTab(bar,index))return Float.NaN;
        View child=track.getChildAt(index);int[] cp=new int[2],bp=new int[2];child.getLocationOnScreen(cp);bar.getLocationOnScreen(bp);return cp[0]-bp[0]+child.getWidth()/2f;}
    private int slotAt(View bar,float x){java.util.List<Integer> visible=visibleTabIndices(bar,true);int[] indices=new int[visible.size()];float[] centers=new float[visible.size()];for(int n=0;n<visible.size();n++){indices[n]=visible.get(n);centers[n]=slotCenter(bar,indices[n]);}return BottomNavigationRules.nearest(x,indices,centers);}
    private void drawPill(Canvas canvas,View bar,int w,int h,boolean dark){
        if(!barEnabled()||(!liquidStyle()&&!glassFlag("glass_bottom_bar_swipe")))return;
        ViewGroup track=tabTrack(bar);java.util.List<Integer> visible=visibleTabIndices(bar,true);if(visible.isEmpty())return;int count=track.getChildCount();
        PillState state=pills.computeIfAbsent(bar,key->new PillState());
        int index=reconciledTab(bar,state);if(!visible.contains(index))index=visible.get(0);float target=slotCenter(bar,index);
        if(!state.drag&&!state.pressed)animatePill(bar,index,false);
        float density=bar.getResources().getDisplayMetrics().density,inset=5*density,slot=Float.isNaN(state.width)?slotWidth(bar,index):state.width;
        float half=Math.max(1,slot/2-inset),center=Float.isNaN(state.center)?target:Math.max(inset+half,Math.min(w-inset-half,state.center));
        RectF rect=new RectF(center-half,inset,center+half,h-inset);
        if(liquidStyle()){float sx=Math.max(.82f,Math.min(1.28f,state.sx.value)),sy=Math.max(.82f,Math.min(1.28f,state.sy.value));rect.inset(-rect.width()*(sx-1f)/2f,-rect.height()*(sy-1f)/2f);}
        Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        if(liquidStyle() && drawLiquidPill(canvas,bar,rect,density,dark,state))return;
        paint.setColor(dark?0x2EFFFFFF:0x99FFFFFF);canvas.drawRoundRect(rect,rect.height()/2,rect.height()/2,paint);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(Math.max(1,density));paint.setColor(dark?0x48FFFFFF:0xCCFFFFFF);canvas.drawRoundRect(rect,rect.height()/2,rect.height()/2,paint);
    }
    private boolean drawLiquidPill(Canvas canvas,View bar,RectF rect,float density,boolean dark,PillState state){
        GlassPillView pill=pillViews.get(bar);
        if(pill==null||!(bar instanceof ViewGroup)||!canvas.isHardwareAccelerated())return false;
        if(pill.lensNode!=null)pill.lensNode.discardDisplayList();
        GlassBackdropView backdrop=null;ViewGroup group=(ViewGroup)bar;
        for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i) instanceof GlassBackdropView)backdrop=(GlassBackdropView)group.getChildAt(i);
        if(backdrop==null||!backdrop.sampleReady||backdrop.sourceNode==null||!backdrop.sourceNode.hasDisplayList()){if(pill.lensNode!=null)pill.lensNode.discardDisplayList();return false;}
        int width=Math.max(1,Math.round(rect.width())),height=Math.max(1,Math.round(rect.height()));
        if(pill.lensNode==null)pill.lensNode=new RenderNode("LiquidGlassPill");
        pill.lensNode.setPosition(0,0,width,height);
        if(!pill.liquid.apply(pill.lensNode,width,height,height/2f,density,0,liveAlpha>=0?liveAlpha:host.intValue("glass_bar_alpha"),dark,true,state.activity.value,state.direction.value))return false;
        pill.lensNode.discardDisplayList();
        try {
            RecordingCanvas rc=pill.lensNode.beginRecording(width,height);
            boolean recorded=false;
            try{rc.translate(-rect.left,-rect.top);rc.scale(1f/SCALE,1f/SCALE);rc.drawRenderNode(backdrop.sourceNode);recorded=true;}
            finally{try{pill.lensNode.endRecording();}finally{if(!recorded)pill.lensNode.discardDisplayList();}}
        }catch(Throwable error){pill.lensNode.discardDisplayList();return false;}
        Paint shadow=new Paint(Paint.ANTI_ALIAS_FLAG);shadow.setStyle(Paint.Style.STROKE);
        for(int i=3;i>=1;i--){shadow.setStrokeWidth(density*i);shadow.setColor(dark?0x08000000:0x06000000);canvas.drawRoundRect(rect,rect.height()/2,rect.height()/2,shadow);}
        int save=canvas.save();try{canvas.translate(rect.left,rect.top);canvas.drawRenderNode(pill.lensNode);}finally{canvas.restoreToCount(save);}
        return true;
    }
    private boolean selectedTitle(View v,int depth){if(depth>8)return false;if(v instanceof android.widget.TextView&&v.isSelected())return isMainTabTitle((android.widget.TextView)v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(selectedTitle(g.getChildAt(i),depth+1))return true;}return false;}

    /** 已装滑动切换的底栏。 */
    private static final Set<View> swiped =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));

    /** 按方向切换 pager 的相邻页。 */
    private void switchTab(int dir) {
        try {
            View pager = find(scanRoot != null ? scanRoot : null, PAGER, 0);
            if (pager == null) return;
            Method getCur = pager.getClass().getMethod("getCurrentItem");
            Method setCur = pager.getClass().getMethod("setCurrentItem", int.class);
            int cur = (Integer) getCur.invoke(pager);
            Method getCount = pager.getClass().getMethod("getAdapter");
            Object adapter = getCount.invoke(pager);
            int total = 5;
            if (adapter != null) {
                try {
                    total = (Integer) adapter.getClass().getMethod("getCount").invoke(adapter);
                } catch (Throwable ignored) {
                }
            }
            int next = Math.max(0, Math.min(total - 1, cur + dir));
            if (next != cur) setCur.invoke(pager, next);
            host.log("Glass swipe tab: " + cur + " -> " + next + " / " + total);
        } catch (Throwable error) {
            host.log("Glass swipe tab FAILED: " + error.getClass().getName());
        }
    }

    /** 已挂 padding 守卫的容器。 */
    private static final Set<View> padGuarded =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));

    /**
     * 持续清零内容容器的底部 padding。
     *
     * 实机诊断 ssrl=[305,3074,0,126]：SSRL 自身 bottomMargin 已是 0，
     * 但父容器 paddingBottom=126（正好底栏高），把内容顶在底栏上沿。
     * 宿主会在布局完成后重新设回，所以必须挂监听持续清理，
     * 不能只在扫描时清一次。
     */
    private void watchPagerPadding(final View pager, final View bar) {
        if (isStoreView(pager)) return; // Store owns a scoped viewport synchronizer, no timed blind scans.
        if (!padGuarded.add(pager)) return;
        final Runnable fix = () -> {
            try {
                clearBottomPadding(pager);
                ViewGroup g = pager instanceof ViewGroup ? (ViewGroup) pager : null;
                if (g != null) {
                    for (int i = 0; i < g.getChildCount(); i++) clearBottomPadding(g.getChildAt(i));
                }
                // 顺手把所有 SSRL 再拉一次，防止宿主回写 margin。
                fixRefreshLayouts(pager, bar, 0);
                // 5.3.35 的 clearTabBottomStrips 会误伤 BookMall 内部容器，
                // 直接把「推荐榜」整块清没了，已彻底删除。
                // 内容拉通只保留 SuperSwipeRefreshLayout（5.3.34 已验证正常），
                // 绝不再遍历 ViewPager 内其它未知容器。
                bar.invalidate();
            } catch (Throwable ignored) {
            }
        };

        // Do not replace the host ViewPager's OnHierarchyChangeListener.
        // Lazy page handling is owned by the scoped fragment watcher.
        // 切页时（滑动或点击）再补一次。
        try {
            Method setItem = pager.getClass().getMethod("setCurrentItem", int.class);
            module.hook(setItem).intercept(chain -> {
                Object result = chain.proceed();
                pager.post(fix);
                pager.postDelayed(fix, 300L);
                // 切页后立即重算深浅并重绘，确保切回书城瞬间恢复浅色玻璃。
                // 宿主改 Tab 文字色可能滞后于点击，补两次延迟重绘，
                // 确保切回书城瞬间就换成浅色玻璃。
                for (long d : new long[]{0L, 80L, 220L}) {
                    bar.postDelayed(() -> {
                        bar.invalidate();
                        ViewGroup vg = bar instanceof ViewGroup ? (ViewGroup) bar : null;
                        if (vg != null) {
                            for (int i = 0; i < vg.getChildCount(); i++) {
                                vg.getChildAt(i).invalidate();
                            }
                        }
                    }, d);
                }
                return result;
            });
        } catch (Throwable ignored) {
        }
        pager.post(fix);
        pager.postDelayed(fix, 600L);
    }

    /** 清零底部 padding，并保持裁剪（防止刷新圆溢出）。 */
    private void clearBottomPadding(View v) {
        if (v == null) return;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            if (g.getPaddingBottom() != 0) {
                g.setPadding(g.getPaddingLeft(), g.getPaddingTop(), g.getPaddingRight(), 0);
            }
            g.setClipChildren(true);
            g.setClipToPadding(true);
        }
    }

    /** 给下拉容器内部的第一个可滚动列表加底部安全内边距。 */
    private void applyListSafety(View refresh, View bar) {
        View list = firstScrollable(refresh, 0);
        if (list != null) clearOuterSafety(list);
    }

    private void clearOuterSafety(View list) {
        if(ScrollWorkGate.busy(list))return;
        if (list.getPaddingBottom() != 0)
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), 0);
        if (list instanceof ViewGroup) {
            ((ViewGroup) list).setClipChildren(true);
            ((ViewGroup) list).setClipToPadding(true);
        }
    }

    private boolean hasType(View v, String type) {
        for (Class<?> c = v.getClass(); c != null; c = c.getSuperclass())
            if (type.equals(HostSymbols.canonical(c.getName()))) return true;
        return false;
    }

    private boolean isMainScrollType(View v) {
        return hasType(v, "androidx.recyclerview.widget.RecyclerView")
                || hasType(v, "androidx.core.widget.NestedScrollView");
    }

    private boolean excludedListBranch(View v) {
        for (View cur = v; cur != null;
                cur = cur.getParent() instanceof View ? (View) cur.getParent() : null) {
            String name = cur.getClass().getName();
            if (name.contains("TextRankLayout") || name.contains("AnimatorContainerLayout")) return true;
            if (cur != v && isMainScrollType(cur)) return true;
            if (HOST_REFRESH.equals(name)) break;
        }
        return false;
    }

    private View firstScrollable(View v, int depth) {
        if (v == null || depth > MAX_DEPTH || excludedListBranch(v)) return null;
        if (isMainScrollType(v)) {
            // A horizontal main candidate is a boundary too: never search its items.
            if (hasType(v, "androidx.recyclerview.widget.RecyclerView")) {
                try {
                    Object lm = v.getClass().getMethod("getLayoutManager").invoke(v);
                    if (lm == null || !Boolean.TRUE.equals(lm.getClass()
                            .getMethod("canScrollVertically").invoke(lm))) return null;
                } catch (Throwable ignored) { return null; }
            }
            return v;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = firstScrollable(g.getChildAt(i), depth + 1);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    /** 深度优先按类名查找。 */
    private View find(View root, String cls, int depth) {
        if (root == null || depth > MAX_DEPTH) return null;
        if (cls.equals(HostSymbols.canonical(root.getClass().getName()))) return root;
        if (!(root instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) root;
        for (int i = 0; i < g.getChildCount(); i++) {
            View hit = find(g.getChildAt(i), cls, depth + 1);
            if (hit != null) return hit;
        }
        return null;
    }

    /**
     * 玻璃外观安装。
     *
     * 关键决定：不再 setBackground()。
     *
     * 实机现象「刚进番茄有边框 → 过一会儿边框消失 → 切 Tab 变纯白」，
     * 是因为宿主在皮肤加载完成和每次切 Tab 时会重设底栏背景，把我们的
     * Drawable 直接覆盖掉。所以玻璃的模糊、着色、高光全部改到
     * dispatchDraw 前置绘制里完成，宿主再怎么 setBackground 也盖不到。
     */
    private void paint(View bar, boolean floating){
        try {
            float density = bar.getResources().getDisplayMetrics().density;
            final int corner = dp(liveInt("glass_bar_corner"), density);
            // 背景强制透明：让下方内容（和我们的 GlassBackdropView）露出来。
            bar.setBackgroundColor(Color.TRANSPARENT);
            if (Build.VERSION.SDK_INT >= 21) {
                bar.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override public void getOutline(View v, android.graphics.Outline o) {
                        o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), corner);
                    }
                });
                bar.setClipToOutline(floating && corner > 0);
            }
            installGlassDraw(bar, corner);
            installSwipe(bar);
            watchRedraw(bar);
            host.log("Glass painted: corner=" + liveInt("glass_bar_corner") + "dp"
                    + " alpha=" + host.intValue("glass_bar_alpha")
                    + " blur=" + host.intValue("glass_bar_blur")
                    + " mode=dispatchDraw");
        } catch (Throwable error) {
            host.log("Glass paint FAILED: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    /** 已挂 OnLayoutChangeListener 的下拉容器。 */
    private static final Set<View> guarded =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));

    /** 已挂滚动跟新的底栏。 */
    private static final Set<View> watched =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));

    /** 已装玻璃绘制的底栏。 */
    private static final Set<View> glassed =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));

    /**
     * 给底栏挂上 dispatchDraw 前置玻璃绘制。
     *
     * 绘制顺序（这是本轮架构的核心）：
     *   ① 离屏录制下方内容 → RenderEffect 高斯模糊 → 画进底栏矩形
     *   ② 叠加低不透明度着色层（按 alpha 滑块线性）
     *   ③ 画 1dp 镜面高光描边
     *   ④ chain.proceed() —— 最后才画 5 个 Tab 图标与文字，保证锐利
     */
    /**
     * 在底栏 index 0 插入专属的 GlassBackdropView。
     *
     * 为什么换成独立子 View（而不是 setBackground 或 dispatchDraw）：
     *   - setBackground：宿主换肤/切 Tab 时会整个覆盖掉（实机 1~2 秒后消失）；
     *   - dispatchDraw：ViewGroup 的 dispatchDraw 是 final，只能靠 background 变通；
     *   - 独立子 View 插在 index 0：它是 BottomTabScaleLayout / BottomTabBarLayout
     *     的兄弟节点且排在它们之前，绘制顺序天然在 Tab 文字之下（文字永远锐利），
     *     同时宿主改底栏背景完全影响不到它，边框永久常驻。
     */
    /** 隐藏宿主的 hairline 分割线（胶囊上下那两根细长白条的来源之一）。 */
    private void hideDividers(View root, int depth) {
        if (root == null || depth >= 2) return;
        if (!(root instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) root;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c == null) continue;
            // Main buttons and wrappers are never dividers, even during narrow-screen measurement.
            if(c instanceof ViewGroup || c instanceof GlassBackdropView || c instanceof GlassPillView || c.isClickable() || c.hasOnClickListeners())continue;
            int h = c.getHeight();
            if (h > 0 && h <= dp(6, c.getResources().getDisplayMetrics().density)
                    && c.getBackground() != null) {
                // 极扁且带背景 → 判定为分割线，直接隐藏。
                c.setVisibility(View.GONE);
                host.log("Glass divider hidden: " + c.getClass().getName() + " h=" + h);
            }
            hideDividers(c, depth + 1);
        }
    }

    private void installGlassDraw(final View bar, final int corner){
        if (!glassed.add(bar)) return;
        try {
            if (!(bar instanceof ViewGroup)) return;
            ViewGroup vg = (ViewGroup) bar;
            hideDividers(bar, 0);
            // 已经有就复用，避免重复插入。
            for (int i = 0; i < vg.getChildCount(); i++) {
                if (vg.getChildAt(i) instanceof GlassBackdropView) return;
            }
            GlassBackdropView backdrop = new GlassBackdropView(bar, corner);
            backdrop.setClickable(false);
            backdrop.setFocusable(false);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            vg.addView(backdrop, 0, lp);
            GlassPillView pill=new GlassPillView(bar);
            vg.addView(pill,1,new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT));
            pillViews.put(bar,pill);
            host.log("GlassBackdropView inserted at index 0");
        } catch (Throwable error) {
            host.log("Glass layer FAILED: " + error.getClass().getName() + ": " + error.getMessage());
        }
    }

    /**
     * 玻璃层：离屏录制下方内容 → 高斯模糊 → 圆角裁剪绘制 → 着色 + 高光。
     *
     * 绝不调用 target.setRenderEffect()：那会把承载文字的整个 RenderNode
     * 一起模糊掉（5.3.30 之前 Tab 文字消失就是这个原因）。
     * 模糊只施加在独立的离屏 RenderNode 上，再作为位图画回来。
     */
    /**
     * 底栏专属玻璃背景。
     *
     * 关键约束：
     *  · 它是 BottomTabFrameLayout 的 index 0 子 View，绘制顺序在 Tab 文字之前，
     *    所以绝不可能糊字，也不会被宿主换肤覆盖；
     *  · 模糊只作用在独立的离屏 RenderNode 上，绝不调用 setRenderEffect 到自身或底栏；
     *  · 任何一步失败都打印明确日志，并且仍然画出着色层与高光边框——
     *    绝不降级成完全透明（实机 5.3.31 的「玻璃1~2秒后消失」就是静默失败导致）。
     */
    private final class GlassBackdropView extends View {
        private final View bar;
        private final int corner;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final ThreadLocal<Boolean> busy = ThreadLocal.withInitial(() -> Boolean.FALSE);
        private RenderNode node;
        private RenderNode sourceNode;
        private boolean sampleReady;
        private RenderNode spareNode, spareSourceNode;
        private int sampleTab = -2, sampleWidth, sampleHeight;
        private View sampleRoot;
        private boolean sampleLiquid;
        private boolean transitionDrawLogged;

        private final Set<String> failureReasons = new java.util.HashSet<>();
        private boolean sampleFailure(Canvas canvas, String reason, View src) {
            String key = reason + ":" + canvas.isHardwareAccelerated();
            if (failureReasons.add(key)) host.log("Glass568 sample-fallback reason=" + reason
                    + " hw=" + canvas.isHardwareAccelerated() + " dsvVis=" + (src == null ? -1 : src.getVisibility())
                    + " dsvShown=" + (src != null && src.isShown()) + " barVis=" + bar.getVisibility()
                    + " barShown=" + bar.isShown() + " nodeHasList=" + (node != null && node.hasDisplayList())
                    + " sourceHasList=" + (sourceNode != null && sourceNode.hasDisplayList())
                    + " rootMatch=" + (src != null && src.getRootView() == bar.getRootView())
                    + " scanRootCurrent=" + (scanRoot == bar.getRootView())
                    + " fallback=" + (liquidStyle() ? "transparent-mist" : "legacy"));
            return false;
        }
        private boolean drawRetainedSample(Canvas canvas, int w, int h) {
            if (!canvas.isHardwareAccelerated()) return sampleFailure(canvas, "software-canvas", null);
            if (!sampleReady || sampleRoot != bar.getRootView() || sampleTab != selectedTab(bar)
                    || sampleWidth != w || sampleHeight != h || sampleLiquid != liquidStyle()
                    || node == null || !node.hasDisplayList() || sourceNode == null || !sourceNode.hasDisplayList()) return sampleFailure(canvas, "retained-unavailable", null);
            int saved = canvas.save();
            try { canvas.scale(1f / SCALE, 1f / SCALE); canvas.drawRenderNode(node); }
            finally { canvas.restoreToCount(saved); }
            return true;
        }
        private final LiquidGlassRenderer liquid = new LiquidGlassRenderer(host::log);
        private boolean liquidApplied;
        private int effectStyle = -1;
        void resetLiquid(){liquid.reset();liquidApplied=false;effectStyle=-1;lastBlur=Integer.MIN_VALUE;if(node!=null)node.setRenderEffect(null);invalidate();}
        @Override protected void onDetachedFromWindow(){resetLiquid();if(node!=null)node.discardDisplayList();if(sourceNode!=null)sourceNode.discardDisplayList();if(spareNode!=null)spareNode.discardDisplayList();if(spareSourceNode!=null)spareSourceNode.discardDisplayList();sampleReady=false;sampleRoot=null;super.onDetachedFromWindow();}
        @Override protected void onWindowVisibilityChanged(int visibility) {
            super.onWindowVisibilityChanged(visibility);
            if (visibility == View.VISIBLE) { transitionDrawLogged=false; invalidate(); } // Re-record synchronously in the first visible draw.
        }
        private int lastBlur = Integer.MIN_VALUE;
        private float lastRadius = -1f;
        private int lastW = -1, lastH = -1, lastClipCorner = -1;
        private int lastTint = -1;
        private boolean lastDark = false;

        GlassBackdropView(View bar, int corner) {
            super(bar.getContext());
            this.bar = bar;
            this.corner = corner;
            setWillNotDraw(false);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density));
        }

        @Override protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (Boolean.TRUE.equals(busy.get())) return;
            busy.set(Boolean.TRUE);
            try {
                if (!barEnabled()||Boolean.TRUE.equals(suspendedBars.get(bar))) return;
                // Geometry is reconciled by onPreDraw before this draw.
                updateLiquidClipping(bar);
                // 每次绘制前清掉宿主塞回来的纯白/纯黑背景，保证边框与玻璃常驻。
                int bgCleared = purgeHostBackgrounds();
                boolean dark = isDark(bar);
                if (loggedDark == null || loggedDark.booleanValue() != dark) {
                    loggedDark = dark;
                    host.log("Glass dark state: dark=" + dark + " tab=" + currentTab(bar)
                            + " bgCleared=" + bgCleared);
                }
                drawGlass(canvas, w, h);
            } catch (Throwable error) {
                host.log("GlassBackdrop onDraw FAILED: " + error.getClass().getName()
                        + ": " + error.getMessage());
                // 兜底：即便出错也画一层基础玻璃，绝不透明。
                try {
                    Paint p = new Paint();
                    if (!drawRetainedSample(canvas, w, h)) {
                        p.setColor(isDark(bar) ? 0x331C1C22 : 0x22FFFFFF);
                        canvas.drawRoundRect(new RectF(0, 0, w, h), corner, corner, p);
                    }
                } catch (Throwable ignored) {
                }
            } finally {
                busy.set(Boolean.FALSE);
            }
        }

        private void drawGlass(Canvas canvas, int w, int h) {
            float density = getResources().getDisplayMetrics().density;
            boolean dark = isDark(bar);
            int alpha = Math.max(10, Math.min(90, liveAlpha >= 0 ? liveAlpha : host.intValue("glass_bar_alpha")));
            int blur = liveBlur >= 0 ? liveBlur : host.intValue("glass_bar_blur");
            int corner = dp(liveInt("glass_bar_corner"), density);
            if (w != lastW || h != lastH || corner != lastClipCorner) {
                lastW = w; lastH = h; lastClipCorner = corner;
                clip.reset();
                clip.addRoundRect(new RectF(0, 0, w, h), corner, corner, Path.Direction.CW);
            }

            int save = canvas.save();
            boolean docked=dockedStyle();
            if (!docked && corner > 0) canvas.clipPath(clip);

            boolean blurred = drawBlurred(canvas, w, h, blur, density);
            if(liquidApplied && blurred){canvas.restoreToCount(save);return;}

            // 磨砂着色层：深色页用深炭灰、浅色页用纯白，均按 alpha 滑块线性。
            // 实机反馈「短剧白蒙层看不清白字」→ 深色时必须换深色底。
            // 忠实跟随滑块：alpha=22 → 约 21%，alpha=70 → 约 68%。
            // 5.3.33 的 26+0.60α 让 22 算出 99（近 40%），太闷发灰。
            int base = dark ? 0xFF1A1B20 : 0xFFFFFFFF;
            int a = (int) (alpha / 100f * 255f);
            // Liquid fallback has no retained page pixels and survives RenderNode reclamation.
            // Its low optical density is independent of the opaque legacy frosted fallback.
            if (!blurred && liquidStyle()) a = dark ? 20 : 14;
            else if (!blurred) a = Math.min(255, a + 90);
            fill.setColor((base & 0x00FFFFFF) | (a << 24));
            canvas.drawRect(0, 0, w, h, fill);
            if (lastTint != a || lastDark != dark) {
                lastTint = a;
                lastDark = dark;
                host.log("GlassBackdrop tint: alpha=" + alpha + " tintAlpha=" + a
                        + " dark=" + dark + " recorded=" + blurred + " blurred=" + (blurred && blur > 0));
            }

            // Pill is a separate hardware display list above this expensive backdrop.

            if(docked){rim.setStyle(Paint.Style.STROKE);rim.setStrokeWidth(Math.max(1f,density*0.75f));rim.setColor(dark?0x38FFFFFF:0x55FFFFFF);canvas.drawLine(0,density*0.5f,w,density*0.5f,rim);canvas.restoreToCount(save);return;}
            // 细描边全部向内缩进 1.5dp 并沿圆角走，绝不画 0..w 的直白杠。
            // 之前紧贴边界画横线，会被 clipToOutline 切残成上下两根细长白条。
            float inset = 1.5f * density;
            float radius = Math.max(0f, corner - inset);
            android.graphics.RectF ring = new android.graphics.RectF(
                    inset, inset, w - inset, h - inset);
            Path edge = new Path();
            if (radius > 0) {
                edge.addRoundRect(ring, radius, radius, Path.Direction.CW);
            } else {
                edge.addRect(ring, Path.Direction.CW);
            }
            rim.setStrokeWidth(Math.max(1f, density));
            rim.setStyle(Paint.Style.STROKE);
            if (dark) {
                // 深色页：柔和半透白，单线。
                rim.setColor(0x33FFFFFF);
                canvas.drawPath(edge, rim);
            } else {
                // 浅色页：极淡半透灰外线 + 柔和微白内高光，双线。
                rim.setColor(0x22000000);
                canvas.drawPath(edge, rim);
                float thin = Math.max(1f, density * 0.8f);
                rim.setStrokeWidth(thin);
                rim.setColor(0x55FFFFFF);
                android.graphics.RectF inner = new android.graphics.RectF(
                        inset + thin, inset + thin, w - inset - thin, h - inset - thin);
                Path edge2 = new Path();
                float r2 = Math.max(0f, radius - thin);
                if (r2 > 0) {
                    edge2.addRoundRect(inner, r2, r2, Path.Direction.CW);
                } else {
                    edge2.addRect(inner, Path.Direction.CW);
                }
                canvas.drawPath(edge2, rim);
            }
            canvas.restoreToCount(save);
        }

        /** 离屏录制 InterceptSelectViewPager 与底栏重叠区域并模糊。 */
        private boolean drawBlurred(Canvas canvas, int w, int h, int blur, float density){
            // Keep the last committed frame until a complete dsv recording replaces it.
            // Tab/root/size validation prevents retained bookstore pixels crossing pages.
            if (Build.VERSION.SDK_INT < 31) {
                return sampleFailure(canvas, "api-below-31", null);
            }

            View src = mainContentViewport(bar);
            if (!canvas.isHardwareAccelerated()) return sampleFailure(canvas, "software-canvas", src);
            if(!transitionDrawLogged){transitionDrawLogged=true;host.log("Glass567 first-visible source="+(src!=null)+" sample="+sampleReady+" node="+(node!=null&&node.hasDisplayList())+" hardware="+canvas.isHardwareAccelerated());}
            if (src == null) {
                sampleFailure(canvas, viewportFailureReason(bar), exact(bar.getRootView(),"dsv","android.widget.FrameLayout"));
                return drawRetainedSample(canvas, w, h);
            }
            if (src.getWidth() <= 0 || src.getHeight() <= 0) {
                sampleFailure(canvas, "dsv-zero-size", src);
                return drawRetainedSample(canvas, w, h);
            }
            RenderNode committedNode = node, committedSource = sourceNode;
            boolean committedReady = sampleReady, committedLiquid = liquidApplied;
            node = spareNode; sourceNode = spareSourceNode;
            spareNode = null; spareSourceNode = null;
            try {
                if (node == null) node = new RenderNode("glassSrc");
                node.setPosition(0, 0, Math.max(1, Math.round(w * SCALE)),
                        Math.max(1, Math.round(h * SCALE)));
                int raw = Math.max(0, Math.min(2, blur));
                float radius = (raw == 0 ? 0f : raw == 1 ? 16f : 64f) * density;
                int style=liquidStyle()?2:dockedStyle()?1:0;
                if(style!=effectStyle){resetLiquid();effectStyle=style;}
                boolean previousLiquid=liquidApplied;
                liquidApplied=style==2&&liquid.apply(node,Math.max(1,Math.round(w*SCALE)),Math.max(1,Math.round(h*SCALE)),dp(liveInt("glass_bar_corner"),density)*SCALE,density*SCALE,raw,liveAlpha>=0?liveAlpha:host.intValue("glass_bar_alpha"),isDark(bar),false,false);
                if (!liquidApplied) {
                    node.setRenderEffect(radius == 0f ? null : RenderEffect.createBlurEffect(
                            radius * SCALE, radius * SCALE, Shader.TileMode.CLAMP));
                    lastBlur = raw;
                    lastRadius = radius;
                    host.log("Glass blur config: raw=" + raw + " radiusPx=" + radius + " scale=" + SCALE);
                }
                int[] barPos = new int[2];
                int[] srcPos = new int[2];
                bar.getLocationOnScreen(barPos);
                src.getLocationOnScreen(srcPos);
                float dx = barPos[0] - srcPos[0];
                float dy = barPos[1] - srcPos[1];

                if(sourceNode==null)sourceNode=new RenderNode("glassViewport");
                sourceNode.setPosition(0,0,Math.max(1,Math.round(w*SCALE)),Math.max(1,Math.round(h*SCALE)));
                RecordingCanvas rc = sourceNode.beginRecording(
                        Math.max(1, Math.round(w * SCALE)),
                        Math.max(1, Math.round(h * SCALE)));
                boolean recorded = false;
                try {
                    rc.scale(SCALE, SCALE);
                    // View.draw does not apply the parent dispatchDraw scroll transform.
                    // Record main content viewport (all transitioning children), never child zero.
                    rc.translate(-dx - src.getScrollX(), -dy - src.getScrollY());
                    src.draw(rc);
                    recorded = true;
                } finally {
                    try { sourceNode.endRecording(); } finally { if (!recorded) sourceNode.discardDisplayList(); }
                }
                RecordingCanvas effectCanvas=node.beginRecording(Math.max(1,Math.round(w*SCALE)),Math.max(1,Math.round(h*SCALE)));
                boolean effectRecorded=false;
                try{effectCanvas.drawRenderNode(sourceNode);effectRecorded=true;}
                finally{try{node.endRecording();}finally{if(!effectRecorded)node.discardDisplayList();}}
                sampleReady = true;
                sampleRoot = bar.getRootView(); sampleTab = selectedTab(bar);
                sampleWidth = w; sampleHeight = h; sampleLiquid = liquidStyle();
                spareNode = committedNode; spareSourceNode = committedSource;
                logBackdropSource(src, bar);

                int save = canvas.save();
                canvas.scale(1f / SCALE, 1f / SCALE);
                canvas.drawRenderNode(node);
                canvas.restoreToCount(save);
                return true;
            } catch (Throwable error) {
                if(node!=null)node.discardDisplayList();
                if(sourceNode!=null)sourceNode.discardDisplayList();
                spareNode = node; spareSourceNode = sourceNode;
                node = committedNode; sourceNode = committedSource;
                sampleReady = committedReady; liquidApplied = committedLiquid;
                sampleFailure(canvas, "record-or-draw-" + error.getClass().getSimpleName(), src);
                host.log("GlassBackdrop blur FAILED: " + error.getClass().getName()
                        + ": " + error.getMessage());
                return drawRetainedSample(canvas, w, h);
            }
        }

        /**
         * 清掉宿主重新塞回底栏各层的纯色背景。
         *
         * 宿主会在换肤完成与每次切 Tab 时重设背景；之前一刀切拦截
         * setBottomTabBackground 反而让短剧的黑底无人清理、永久锁死。
         * 这里改成实时清理：只清背景，不碰任何 Tab 子 View。
         */
        private Boolean loggedDark;

        private int purgeHostBackgrounds() {
            int clearedCount = 0;
            try {
                // 只清三层（5.3.32 已实测可用）。
                // 5.3.33 试过递归 purgeTree，结果把底栏自身背景也清了，
                // 反而引入「上下白条 + 切 Tab 锁死纯白」的回归，已移除。
                for (String name : new String[]{BOTTOM, BOTTOM_SCALE, BOTTOM_BAR}) {
                    View v = find(bar, name, 0);
                    if (v != null && v != this && v.getBackground() != null) {
                        v.setBackground(null);
                        clearedCount++;
                    }
                }
                View parent = bar.getParent() instanceof View ? (View) bar.getParent() : null;
                for (int i = 0; i < 3 && parent != null; i++) {
                    if (parent.getHeight() > 0 && parent.getHeight() <= BAR_REGION_MAX_PX
                            && parent.getBackground() != null) {
                        if(!parentBackgrounds.containsKey(parent))parentBackgrounds.put(parent,parent.getBackground());
                        parent.setBackground(null);
                        clearedCount++;
                    }
                    parent = parent.getParent() instanceof View ? (View) parent.getParent() : null;
                }
            } catch (Throwable ignored) {
            }
            return clearedCount;
        }

    }
    private final java.util.Map<View, BarRedrawState> redrawStates = new WeakHashMap<>();
    private static final class BarRedrawState {
        android.view.ViewTreeObserver observer;
        android.view.ViewTreeObserver.OnPreDrawListener listener;
    }
    private void watchRedraw(final View bar){
        watched.add(bar);
        bindBarRedraw(bar);
    }
    private void unbindBarRedraw(View bar) {
        BarRedrawState state=redrawStates.get(bar);
        if(state==null)return;
        if(state.observer!=null&&state.observer.isAlive()&&state.listener!=null)
            state.observer.removeOnPreDrawListener(state.listener);
        state.observer=null;
    }
    private void bindBarRedraw(final View bar) {
        if(!bar.isAttachedToWindow())return;
        BarRedrawState state=redrawStates.computeIfAbsent(bar,key->new BarRedrawState());
        android.view.ViewTreeObserver observer=bar.getViewTreeObserver();
        if(state.observer==observer&&observer.isAlive())return;
        unbindBarRedraw(bar);
        if(state.listener==null)state.listener=new android.view.ViewTreeObserver.OnPreDrawListener(){
            private long last;
            @Override public boolean onPreDraw(){
                long now=android.os.SystemClock.uptimeMillis();
                if(!liquidStyle()&&now-last<32L)return true;
                last=now;
                if(suspendBar(bar))return true;
                BarReadyState ready=barReady.get(bar);if(ready!=null)queueBarReady(bar,ready);
                if(!barEnabled()){resetLiquidPress(bar,pills.get(bar),"disabled");restoreLiquidClipping();restoreBarTree(bar,0);restoreParentBackgrounds();return true;}
                if(!painted.contains(bar))return true;
                if(!ScrollWorkGate.busy(bar)){updateBarStyleGeometry(bar);updateBarWidth(bar);fitTabTrack(bar);}
                if(bar instanceof ViewGroup){ViewGroup group=(ViewGroup)bar;for(int i=0;i<group.getChildCount();i++)if(group.getChildAt(i) instanceof GlassBackdropView)group.getChildAt(i).invalidate();}
                refreshPill(bar);updateLiquidClipping(bar);reconcilePill(bar);logBarTrack(bar);return true;
            }
        };
        try{observer.addOnPreDrawListener(state.listener);state.observer=observer;host.log("Glass watch bound: bar="+System.identityHashCode(bar)+" observer="+System.identityHashCode(observer));}
        catch(Throwable error){host.log("Glass watch FAILED: "+error.getClass().getName());}
    }
    private final java.util.Map<View,Drawable> parentBackgrounds=new WeakHashMap<>();
    private volatile Boolean liveBarEnabled;
    private boolean restoringBar;
    private boolean barEnabled() { return liveBarEnabled != null ? liveBarEnabled : host.on("enabled") && (host.on("glass_bottom_bar_floating") || host.on("glass_bottom_bar_docked")); }
    private final java.util.Map<View, OriginalBarState> originalBars = new WeakHashMap<>();
    private static final class OriginalBarState {
        int width,height,left,top,right,bottom,start,end,gravity,pl,pt,pr,pb,visibility;
        float elevation,z; Drawable background; ViewOutlineProvider outline; boolean clipped,restored;
        OriginalBarState(View v) {
            ViewGroup.LayoutParams lp=v.getLayoutParams(); width=lp==null?0:lp.width;height=lp==null?0:lp.height;
            if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;left=m.leftMargin;top=m.topMargin;right=m.rightMargin;bottom=m.bottomMargin;start=m.getMarginStart();end=m.getMarginEnd();}
            gravity=lp instanceof FrameLayout.LayoutParams?((FrameLayout.LayoutParams)lp).gravity:-1;
            pl=v.getPaddingLeft();pt=v.getPaddingTop();pr=v.getPaddingRight();pb=v.getPaddingBottom();visibility=v.getVisibility();
            background=v.getBackground();outline=v.getOutlineProvider();clipped=v.getClipToOutline();elevation=v.getElevation();z=v.getTranslationZ();
        }
    }
    private void rememberBarTree(View v,int depth){
        if(v==null||depth>12||v instanceof GlassBackdropView||v instanceof GlassPillView)return;
        originalBars.computeIfAbsent(v,OriginalBarState::new);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)rememberBarTree(g.getChildAt(i),depth+1);}
    }
    private void restoreParentBackgrounds(){restoringBar=true;try{for(java.util.Map.Entry<View,Drawable> e:parentBackgrounds.entrySet())e.getKey().setBackground(e.getValue());parentBackgrounds.clear();}finally{restoringBar=false;}}
    private void restoreBarTree(View v,int depth) {
        if(v==null||depth>12)return;
        if(v instanceof GlassBackdropView||v instanceof GlassPillView){v.setVisibility(View.GONE);return;}
        OriginalBarState s=originalBars.get(v);
        if(s!=null&&!s.restored){restoringBar=true;try{
            ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp!=null){lp.width=s.width;lp.height=s.height;
                if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;m.leftMargin=s.left;m.topMargin=s.top;m.rightMargin=s.right;m.bottomMargin=s.bottom;m.setMarginStart(s.start);m.setMarginEnd(s.end);}
                if(lp instanceof FrameLayout.LayoutParams)((FrameLayout.LayoutParams)lp).gravity=s.gravity;v.setLayoutParams(lp);}
            v.setPadding(s.pl,s.pt,s.pr,s.pb);v.setBackground(s.background);v.setOutlineProvider(s.outline);v.setClipToOutline(s.clipped);v.setElevation(s.elevation);v.setTranslationZ(s.z);v.setVisibility(s.visibility);s.restored=true;
        }finally{restoringBar=false;}}
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)restoreBarTree(g.getChildAt(i),depth+1);}
    }
    private void fitTabTrack(View bar){
        if(!barEnabled())return;
        ViewGroup track=tabTrack(bar);if(track==null)return;
        fitVisibleTabs(bar,track);
        if(dockedStyle()){
            for(View v=track;v!=null&&v!=bar;v=v.getParent() instanceof View?(View)v.getParent():null){
                OriginalBarState s=originalBars.get(v);if(s==null)continue;
                ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp==null)continue;
                boolean changed=lp.width!=s.width;lp.width=s.width;
                if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;changed|=m.leftMargin!=s.left||m.rightMargin!=s.right||m.getMarginStart()!=s.start||m.getMarginEnd()!=s.end;m.leftMargin=s.left;m.rightMargin=s.right;m.setMarginStart(s.start);m.setMarginEnd(s.end);}
                if(changed)v.setLayoutParams(lp);
                if(v.getPaddingLeft()!=s.pl||v.getPaddingRight()!=s.pr)v.setPadding(s.pl,v.getPaddingTop(),s.pr,v.getPaddingBottom());
            }
            return;
        }
        // Preserve host tab constraints; remove only the wrapper's horizontal reservation.
        for(View v=track;v!=null;v=v.getParent() instanceof View?(View)v.getParent():null){
            OriginalBarState original=originalBars.get(v);if(original!=null)original.restored=false;
            if(v.getPaddingLeft()!=0||v.getPaddingRight()!=0)v.setPadding(0,v.getPaddingTop(),0,v.getPaddingBottom());
            if(v==bar)break;
            ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp!=null){boolean changed=lp.width!=ViewGroup.LayoutParams.MATCH_PARENT;lp.width=ViewGroup.LayoutParams.MATCH_PARENT;
                if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;changed|=m.leftMargin!=0||m.rightMargin!=0||m.getMarginStart()!=0||m.getMarginEnd()!=0;m.leftMargin=0;m.rightMargin=0;m.setMarginStart(0);m.setMarginEnd(0);}
                if(changed)v.setLayoutParams(lp);}
        }
        if(!Boolean.TRUE.equals(suspendedBars.get(bar))&&bar instanceof ViewGroup){ViewGroup g=(ViewGroup)bar;for(int i=0;i<g.getChildCount();i++)if(g.getChildAt(i) instanceof GlassBackdropView||g.getChildAt(i) instanceof GlassPillView)g.getChildAt(i).setVisibility(View.VISIBLE);}
    }
    private void fitVisibleTabs(View bar,ViewGroup track){
        java.util.List<Integer> visible=visibleTabIndices(bar,false);int count=visible.size(),width=track.getWidth(),height=track.getHeight();if(count==0||width<=0||height<=0)return;
        int available=width-track.getPaddingLeft()-track.getPaddingRight();
        for(int n=0;n<count;n++){View v=track.getChildAt(visible.get(n));int left=track.getPaddingLeft()+BottomNavigationRules.slotEdge(available,n,count),right=track.getPaddingLeft()+BottomNavigationRules.slotEdge(available,n+1,count);
            if(v.getLeft()!=left||v.getRight()!=right||v.getWidth()!=right-left){
                v.measure(View.MeasureSpec.makeMeasureSpec(right-left,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));v.layout(left,0,right,height);
            }
        }
    }
    private final java.util.Map<View,String> trackLogs=new WeakHashMap<>();
    private void logBarTrack(View bar) {
        ViewGroup track=tabTrack(bar);if(track==null||track.getChildCount()==0)return;
        int first=-1,last=-1;for(int i=0;i<track.getChildCount();i++)if(track.getChildAt(i).getVisibility()==View.VISIBLE&&track.getChildAt(i).getWidth()>0){if(first<0)first=i;last=i;}
        if(first<0)return;float inset=dp(5,bar.getResources().getDisplayMetrics().density);
        float pillLeft=slotCenter(bar,first)-slotWidth(bar,first)/2f+inset,pillRight=slotCenter(bar,last)+slotWidth(bar,last)/2f-inset;
        int[] bp=new int[2],tp=new int[2];bar.getLocationOnScreen(bp);track.getLocationOnScreen(tp);
        String geometry="bar="+bp[0]+","+bar.getWidth()+" track="+tp[0]+","+track.getWidth()+" pillEnds="+(bp[0]+pillLeft)+","+(bp[0]+pillRight)+" gaps="+pillLeft+","+(bar.getWidth()-pillRight);
        if(!geometry.equals(trackLogs.put(bar,geometry)))host.log("Glass track geometry: "+geometry);
    }

    private final java.util.Map<View,BarStyleGeometry> styleGeometry=new WeakHashMap<>();
    private static final class BarStyleGeometry {
        int style,left,right,bottom,width,height,padLeft,padTop,padRight,padBottom,gravity;
        boolean clipped;
        ViewOutlineProvider outline;
    }
    private void updateBarStyleGeometry(View bar){
        if(!(bar.getLayoutParams() instanceof ViewGroup.MarginLayoutParams))return;
        boolean docked=dockedStyle();BarStyleGeometry saved=styleGeometry.get(bar);
        if(saved==null){
            if(bar.getWidth()<=0||bar.getHeight()<=0)return;
            saved=new BarStyleGeometry();ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)bar.getLayoutParams();
            saved.left=lp.leftMargin;saved.right=lp.rightMargin;saved.bottom=lp.bottomMargin;saved.width=lp.width;saved.height=lp.height;
            saved.padLeft=bar.getPaddingLeft();saved.padTop=bar.getPaddingTop();saved.padRight=bar.getPaddingRight();saved.padBottom=bar.getPaddingBottom();saved.outline=bar.getOutlineProvider();saved.clipped=bar.getClipToOutline();
            saved.gravity=lp instanceof FrameLayout.LayoutParams?((FrameLayout.LayoutParams)lp).gravity:-1;
            OriginalBarState original=originalBars.get(bar);if(original!=null){saved.height=original.height;saved.padLeft=original.pl;saved.padTop=original.pt;saved.padRight=original.pr;saved.padBottom=original.pb;}
            saved.style=docked?1:0;
            if(docked){float density=bar.getResources().getDisplayMetrics().density;saved.left=dp(liveInt("glass_bar_margin"),density);saved.right=saved.left;saved.bottom=dp(liveInt("glass_bar_bottom"),density);saved.clipped=liveInt("glass_bar_corner")>0;}
            styleGeometry.put(bar,saved);
            if(!docked){host.log("Glass bar style: style=floating swipe="+glassFlag("glass_bottom_bar_swipe"));return;}
            saved.style=-1;
        }
        int next=docked?1:0;if(saved.style==next)return;
        ViewGroup.MarginLayoutParams lp=(ViewGroup.MarginLayoutParams)bar.getLayoutParams();
        if(docked){
            lp.leftMargin=0;lp.rightMargin=0;lp.bottomMargin=0;lp.width=ViewGroup.LayoutParams.MATCH_PARENT;
            if(lp instanceof FrameLayout.LayoutParams)((FrameLayout.LayoutParams)lp).gravity=android.view.Gravity.BOTTOM;
            int inset=0;if(Build.VERSION.SDK_INT>=23&&bar.getRootWindowInsets()!=null)inset=bar.getRootWindowInsets().getSystemWindowInsetBottom();
            if(lp.height>0)lp.height=saved.height+inset;
            bar.setPadding(saved.padLeft,saved.padTop,saved.padRight,saved.padBottom+inset);
            bar.setClipToOutline(false);
        }else{
            lp.leftMargin=saved.left;lp.rightMargin=saved.right;lp.bottomMargin=saved.bottom;lp.width=saved.width;lp.height=saved.height;
            if(lp instanceof FrameLayout.LayoutParams)((FrameLayout.LayoutParams)lp).gravity=saved.gravity;
            bar.setPadding(saved.padLeft,saved.padTop,saved.padRight,saved.padBottom);bar.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View view,Outline outline){outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(liveInt("glass_bar_corner"),view.getResources().getDisplayMetrics().density));}});bar.setClipToOutline(saved.clipped);
        }
        saved.style=next;bar.setLayoutParams(lp);bar.setElevation(0f);bar.setTranslationZ(0f);refreshPill(bar);
        host.log("Glass bar style: style="+(docked?"docked":"floating")+" swipe="+glassFlag("glass_bottom_bar_swipe"));
    }

    /** Recompute from the current parent, never from a previously shortened layout. */
    private void updateBarWidth(View bar){
        if (!barEnabled() || !(bar.getLayoutParams() instanceof ViewGroup.MarginLayoutParams)
                || !(bar.getParent() instanceof View)) return;
        View parent = (View) bar.getParent();
        int parentWidth = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
        if (parentWidth <= 0) return;
        boolean docked = dockedStyle();
        int percent = docked ? 100 : liveWidth >= 0 ? liveWidth : Rules.clampInt("glass_bar_width", host.intValue("glass_bar_width"));
        float density = bar.getResources().getDisplayMetrics().density;
        int margin = docked ? 0 : dp(liveInt("glass_bar_margin"), density);
        int available = Math.max(1, parentWidth - 2 * margin);
        ViewGroup track = tabTrack(bar);
        int tabs = 0;
        if (track != null) for (int i = 0; i < track.getChildCount(); i++)
            if (track.getChildAt(i).getVisibility() == View.VISIBLE) tabs++;
        if (tabs == 0) for (String key : Rules.TAB_KEYS) if (!host.on(key)) tabs++;
        int minSafeWidth = Math.max(1, tabs) * dp(48, density);
        int width = Math.min(available, Math.max(minSafeWidth, Math.round(available * (percent / 100f))));
        int left = (parentWidth - width) / 2, right = parentWidth - width - left;
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) bar.getLayoutParams();
        int gravity = lp instanceof FrameLayout.LayoutParams ? ((FrameLayout.LayoutParams) lp).gravity : -1;
        if (lp instanceof FrameLayout.LayoutParams)
            gravity = ((gravity < 0 ? android.view.Gravity.BOTTOM : gravity) & ~android.view.Gravity.HORIZONTAL_GRAVITY_MASK) | android.view.Gravity.LEFT;
        if (lp.width == width && lp.leftMargin == left && lp.rightMargin == right
                && (!(lp instanceof FrameLayout.LayoutParams) || ((FrameLayout.LayoutParams) lp).gravity == gravity)) return;
        lp.width = width; lp.leftMargin = left; lp.rightMargin = right;
        lp.setMarginStart(left); lp.setMarginEnd(right);
        if (lp instanceof FrameLayout.LayoutParams) ((FrameLayout.LayoutParams) lp).gravity = gravity;
        bar.setLayoutParams(lp);
        host.log("Glass bar width: percent=" + percent + " available=" + available
                + " target=" + width + " minSafe=" + minSafeWidth + " tabs=" + tabs);
    }
    private volatile int liveMargin=-1,liveCorner=-1,liveBottom=-1;
    private int liveInt(String key){int value="glass_bar_margin".equals(key)?liveMargin:"glass_bar_corner".equals(key)?liveCorner:liveBottom;return value>=0?value:host.intValue(key);}
    private volatile int liveBounce = -1;
    private LiquidGlassRenderer.Rules.Bounce bounce(){return LiquidGlassRenderer.Rules.bounce(liveBounce>=0?liveBounce:host.intValue("glass_bar_bounce"));}
    private volatile int liveWidth = -1;
    private volatile int liveStyle = -1;
    private boolean liquidStyle(){return Build.VERSION.SDK_INT>=33&&!dockedStyle()&&(liveStyle>=0?liveStyle:host.intValue("glass_bar_style"))==2;}
    private boolean dockedStyle(){return liveStyle >= 0 ? liveStyle == 1 : host.on("glass_bottom_bar_docked") || host.intValue("glass_bar_style") == 1;}
    private volatile Boolean liveTop,liveSwipe;
    private boolean glassFlag(String key){Boolean value="glass_top_bar".equals(key)?liveTop:liveSwipe;return value!=null?value:host.on(key);}
    private volatile int liveBlur = -1;
    private volatile int liveAlpha = -1;
    private volatile boolean configLoading;
    private boolean pageWatchErrorLogged;
    private android.content.SharedPreferences livePrefs;
    private android.content.SharedPreferences.OnSharedPreferenceChangeListener blurListener;
    private final Set<View> pageWatchers = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private final Set<View> geometryLogged = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private java.util.Map<String, Object> acceptedGlassConfig;
    private synchronized void acceptGlassConfig(java.util.Map<String, ?> values, String source) {
        java.util.Map<String, Object> snapshot = new java.util.HashMap<>();
        for (java.util.Map.Entry<String, ?> entry : values.entrySet()) snapshot.put(entry.getKey(), entry.getValue());
        if (snapshot.equals(acceptedGlassConfig)) return;
        acceptedGlassConfig = snapshot;
        liveBarEnabled=values.get("enabled") instanceof Boolean ? Boolean.TRUE.equals(values.get("enabled")) && (Boolean.TRUE.equals(values.get("glass_bottom_bar_floating")) || Boolean.TRUE.equals(values.get("glass_bottom_bar_docked"))) : null;
        Object blur = values.get("glass_bar_blur"), alpha = values.get("glass_bar_alpha");
        liveMargin=values.get("glass_bar_margin") instanceof Integer?Rules.clampInt("glass_bar_margin",(Integer)values.get("glass_bar_margin")):-1;
        liveCorner=values.get("glass_bar_corner") instanceof Integer?Rules.clampInt("glass_bar_corner",(Integer)values.get("glass_bar_corner")):-1;
        liveBottom=values.get("glass_bar_bottom") instanceof Integer?Rules.clampInt("glass_bar_bottom",(Integer)values.get("glass_bar_bottom")):-1;
        liveBounce=values.get("glass_bar_bounce") instanceof Integer?Rules.clampInt("glass_bar_bounce",(Integer)values.get("glass_bar_bounce")):-1;
        Object width = values.get("glass_bar_width");
        liveWidth = width instanceof Integer ? Rules.clampInt("glass_bar_width", (Integer) width) : -1;
        liveStyle=Boolean.TRUE.equals(values.get("glass_bottom_bar_docked"))?1:values.get("glass_bar_style") instanceof Integer?Rules.clampInt("glass_bar_style",(Integer)values.get("glass_bar_style")):-1;
        liveTop=values.get("glass_top_bar") instanceof Boolean?(Boolean)values.get("glass_top_bar"):null;
        liveSwipe=values.get("glass_bottom_bar_swipe") instanceof Boolean?(Boolean)values.get("glass_bottom_bar_swipe"):null;
        liveBlur = blur instanceof Integer ? Rules.clampInt("glass_bar_blur", (Integer) blur) : -1;
        liveAlpha = alpha instanceof Integer ? Rules.clampInt("glass_bar_alpha", (Integer) alpha) : -1;
        host.log("Glass config source=" + source + " blur="
                + (liveBlur >= 0 ? liveBlur : host.intValue("glass_bar_blur"))
                + " alpha=" + (liveAlpha >= 0 ? liveAlpha : host.intValue("glass_bar_alpha"))
                + " fallbackHost=" + (liveBlur < 0) + " width=" + liveWidth + " barEnabled=" + barEnabled()+" bounce="+(liveBounce>=0?liveBounce:host.intValue("glass_bar_bounce"))+" physics="+bounce());
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            for(View bar:new java.util.ArrayList<>(pills.keySet())){
                if(!barEnabled()||!liquidStyle())resetLiquidPress(bar,pills.get(bar),"style-change");
                if(bar instanceof ViewGroup){ViewGroup group=(ViewGroup)bar;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child instanceof GlassBackdropView)((GlassBackdropView)child).resetLiquid();if(child instanceof GlassPillView)((GlassPillView)child).resetLiquid();}}
                if(!barEnabled()){restoreBarTree(bar,0);restoreParentBackgrounds();continue;}
                OriginalBarState original=originalBars.get(bar);
                if(original!=null&&original.restored){arranged.remove(bar);cleared.clear();styleGeometry.remove(bar);applyToBar(bar,true);}
                styleGeometry.remove(bar);updateBarStyleGeometry(bar);
                ViewGroup.LayoutParams params=bar.getLayoutParams();if(params instanceof ViewGroup.MarginLayoutParams){((ViewGroup.MarginLayoutParams)params).bottomMargin=dockedStyle()?0:dp(liveInt("glass_bar_bottom"),bar.getResources().getDisplayMetrics().density);bar.setLayoutParams(params);}
                bar.setOutlineProvider(new ViewOutlineProvider(){@Override public void getOutline(View v,Outline o){o.setRoundRect(0,0,v.getWidth(),v.getHeight(),dp(liveInt("glass_bar_corner"),v.getResources().getDisplayMetrics().density));}});bar.setClipToOutline(!dockedStyle());
                updateBarWidth(bar);fitTabTrack(bar);bar.requestLayout();bar.invalidate();refreshPill(bar);
            }
        });
    }

    private void refreshGlassConfig(android.app.Activity activity) {
        if (configLoading) return;
        configLoading = true;
        // Keep accepted optics while the asynchronous refresh is pending.
        host.log("Glass config pending: fallbackHost=true blur=" + host.intValue("glass_bar_blur"));
        new Thread(() -> {
            try {
                android.content.SharedPreferences prefs = module.getRemotePreferences(Rules.PREFS);
                java.util.Map<String, ?> values = prefs == null ? null : prefs.getAll();
                if (values != null && values.get("enabled") instanceof Boolean) {
                    acceptGlassConfig(values, "remote");
                    if (livePrefs != prefs) {
                        if (livePrefs != null && blurListener != null)
                            livePrefs.unregisterOnSharedPreferenceChangeListener(blurListener);
                        livePrefs = prefs;
                        blurListener = (changed, key) -> {
                            if (key == null || "glass_bar_margin".equals(key) || "glass_bar_corner".equals(key) || "glass_bar_bottom".equals(key) || "glass_bar_width".equals(key) || "enabled".equals(key) || "glass_bottom_bar_floating".equals(key) || "glass_bar_bounce".equals(key) || "glass_bar_blur".equals(key) || "glass_bar_alpha".equals(key) || "glass_top_bar".equals(key) || "glass_bottom_bar_swipe".equals(key) || "glass_bar_style".equals(key) || "glass_bottom_bar_docked".equals(key)) {
                                try { acceptGlassConfig(changed.getAll(), "remote-change"); }
                                catch (Throwable error) {
                                    liveBlur = -1; liveAlpha = -1;
                                    host.log("Glass config fallbackHost=true reason=" + error.getClass().getSimpleName());
                                }
                            }
                        };
                        prefs.registerOnSharedPreferenceChangeListener(blurListener);
                    }
                } else {
                    android.os.Bundle data = activity.getContentResolver().call(
                            android.net.Uri.parse("content://" + SettingsProvider.AUTHORITY),
                            SettingsProvider.METHOD, null, null);
                    java.util.Map<String, Object> entries = new java.util.HashMap<>();
                    if (data != null && data.containsKey("enabled")) {
                        for (String key : new String[]{"glass_bar_margin", "glass_bar_corner", "glass_bar_bottom", "glass_bar_bounce", "glass_bar_blur", "glass_bar_alpha", "glass_top_bar", "glass_bottom_bar_swipe", "glass_bar_style", "glass_bar_width", "enabled", "glass_bottom_bar_floating", "glass_bottom_bar_docked"})
                            if (data.containsKey(key)) entries.put(key, data.get(key));
                    }
                    acceptGlassConfig(entries, "provider");
                }
            } catch (Throwable error) {
                liveBlur = -1; liveAlpha = -1;
                host.log("Glass config fallbackHost=true reason=" + error.getClass().getSimpleName()
                        + " blur=" + host.intValue("glass_bar_blur"));
            } finally { configLoading = false; }
        }, "Pulp-glass-config").start();
    }

    private String resourceName(View v) {
        try { return HostSymbols.canonicalResource(v.getResources().getResourceEntryName(v.getId())); }
        catch (Throwable ignored) { return ""; }
    }

    private View exact(View root, String id, String cls) {
        if (root == null) return null;
        int value = root.getResources().getIdentifier(HostSymbols.resourceName(id), "id", root.getContext().getPackageName());
        View v = value == 0 ? null : root.findViewById(value);
        return v != null && hasType(v, cls) ? v : null;
    }

    private void watchTargetPages(android.app.Activity activity) {
        View decor = activity.getWindow().getDecorView();
        if (!pageWatchers.add(decor)) return;
        decor.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            private long last;
            @Override public boolean onPreDraw() {
                long now = android.os.SystemClock.uptimeMillis();
                if (now - last < 300 || ScrollWorkGate.busy(decor)) return true;
                last = now;
                try {
                    Object manager = activity.getClass().getMethod("getSupportFragmentManager").invoke(activity);
                    java.util.List<?> fragments = (java.util.List<?>) manager.getClass()
                            .getMethod("getFragments").invoke(manager);
                    View bar = cachedBar(decor);
                    for (Object fragment : fragments) processTargetFragment(fragment, bar);
                } catch (Throwable error) {
                    if (!pageWatchErrorLogged) {
                        pageWatchErrorLogged = true;
                        host.log("Glass target watch FAILED: " + error.getClass().getName());
                    }
                }
                return true;
            }
        });
    }

    private void processTargetFragment(Object fragment, View bar) throws Exception {
        if (fragment == null) return;
        Class<?> type = fragment.getClass();
        View root = (View) type.getMethod("getView").invoke(fragment);
        if (root == null || !root.isShown()) return;
        String tag = (String) type.getMethod("getTag").invoke(fragment);
        if ("NewBookMallFragment".equals(tag)) {

            syncStorePages(root);
            updateStoreTop(root);
            return;
        }
        if (bar == null || !(host.on("glass_bottom_bar_floating") || host.on("glass_bottom_bar_docked"))) return;
        if ("MultiTabPolarisFragment".equals(tag)) {

            extendTarget(exact(root, "kn0", "android.widget.FrameLayout"), bar);
            extendTarget(exact(root, "alt", PAGER), bar);
            applyTargetSafety(root, bar);
            liftEarnFloat(root, bar);
        } else if ("MultiTabShelfFragment".equals(tag)) {

            extendTarget(exact(root, "ao1", "com.dragon.read.widget.InterceptFrameLayout"), bar);
            extendTarget(exact(root, "fa_", "com.dragon.read.component.biz.impl.bookshelf.tab.NoInterceptCoordinatorLayout"), bar);
            View outer = targetScrollable(root, 0);
            if (outer != null) clearOuterSafety(outer);
        } else if ("FanqieMineFragmentV2".equals(tag)) {

            if (root.getClass() == FrameLayout.class) extendTarget(root, bar);
            extendTarget(exact(root, "g5o", "android.widget.LinearLayout"), bar);
            View scroll = exact(root, "ihn", "com.dragon.read.widget.nestedrecycler.ParentNestedScrollView");
            extendTarget(scroll, bar);
            if (scroll != null) applySafetyToList(scroll, bar);
        } else if ("SeriesMallFragment".equals(tag)) {

            extendTarget(exact(root, "kmy", "com.dragon.read.component.shortvideo.impl.v2.SingleViewPager2Container"), bar);
            extendTarget(exact(root, "l1", "androidx.viewpager2.widget.ViewPager2"), bar);
            View slot = exact(root, "as8", "android.view.View");
            if (slot != null && slot.getClass() == View.class && bottomReservation(slot.getHeight(), bar))
                slot.setVisibility(View.GONE);
            liftDramaCaptions(root, bar, 0);
        }
    }

    private final java.util.Map<View, Float> earnFloatOrigins = new WeakHashMap<>();
    private final Set<View> earnFloatLogged = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private void liftEarnFloat(View root, View bar) {
        View lynx = find(root, "com.lynx.tasm.LynxView", 0);
        if (!(lynx instanceof ViewGroup)) return;
        ViewGroup page = (ViewGroup) lynx;
        float density = bar.getResources().getDisplayMetrics().density;
        for (int i = 0; i < page.getChildCount(); i++) {
            View v = page.getChildAt(i);
            if (!"com.lynx.tasm.behavior.ui.scroll.AndroidScrollView".equals(v.getClass().getName())
                    || v.getWidth() <= 0 || v.getWidth() > dp(160, density)
                    || v.getHeight() <= 0 || v.getHeight() > dp(160, density)
                    || v.getLeft() < page.getWidth() * 0.65f
                    || v.getTop() < page.getHeight() * 0.65f
                    || find(v, "com.bytedance.ies.xelement.bytedlottie.LynxBytedLottieAnimationView", 0) == null) continue;
            int[] bp = new int[2], rp = new int[2];
            bar.getLocationOnScreen(bp);
            View screen = bar.getRootView();
            screen.getLocationOnScreen(rp);
            int clearance = Math.max(0, rp[1] + screen.getHeight() - bp[1]) + dp(10, density);
            float original = earnFloatOrigins.computeIfAbsent(v, key -> key.getTranslationY());
            if (v.getTranslationY() != original - clearance) v.setTranslationY(original - clearance);
            if (earnFloatLogged.add(v)) host.log("Glass earn float lifted: class=" + v.getClass().getName()
                    + " id=" + resourceName(v) + " clearance=" + clearance);
        }
    }

    private final java.util.Map<View, StoreTopGlassView> storeTopViews = new WeakHashMap<>();

    private void updateStoreTop(View root) {
        if (!(root instanceof FrameLayout)) return;
        StoreTopGlassView layer = storeTopViews.get(root);
        if (!glassFlag("glass_top_bar")) {
            if (layer != null) layer.setVisibility(View.GONE);
            return;
        }
        View appbar = exact(root, "ik", APP_BAR);
        View source = exact(root, "alt", PAGER);
        if (source == null) return;
        View refresh=find(source,HOST_REFRESH,0);
        if(refresh==null||!refresh.isShown())return;
        int[] rp = new int[2], ap = new int[2];
        root.getLocationOnScreen(rp);refresh.getLocationOnScreen(ap);
        int height=dp(64,root.getResources().getDisplayMetrics().density);
        int statusId=root.getResources().getIdentifier("status_bar_height","dimen","android");
        int status=statusId==0?0:root.getResources().getDimensionPixelSize(statusId);
        int anchor=Math.max(status,ap[1]-rp[1]);
        View search=exact(root,"gat","androidx.appcompat.widget.StretchLeftLayout");
        View channel=exact(root,"h6","androidx.appcompat.widget.Toolbar");
        boolean coverStatus=search!=null&&channel!=null&&search.getVisibility()==View.GONE&&channel.getVisibility()==View.GONE;
        if(coverStatus){height+=anchor;anchor=0;}
        if (layer == null) {
            layer = new StoreTopGlassView(root, source, appbar);
            layer.setClickable(false);
            layer.setFocusable(false);
            layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            ((FrameLayout) root).addView(layer, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height));
            storeTopViews.put(root, layer);
            host.log("Glass store top layer: parent=" + root.getClass().getName()
                    + " screenTop=" + rp[1] + " height=" + height + " source=alt text-exclusion=true");
        } else if (layer.getLayoutParams().height != height) {
            ViewGroup.LayoutParams lp = layer.getLayoutParams(); lp.height = height; layer.setLayoutParams(lp);
        }
        if(layer.getTranslationY()!=anchor)layer.setTranslationY(anchor);
        bindTopScrollCalibration(root,refresh,layer);
        float feather=featherAlpha(refresh);
        layer.setAlpha(feather);
        float maxZ=0f;FrameLayout parent=(FrameLayout)root;for(int i=0;i<parent.getChildCount();i++)if(parent.getChildAt(i)!=layer)maxZ=Math.max(maxZ,parent.getChildAt(i).getZ());
        if(layer.getTranslationZ()!=maxZ+1f)layer.setTranslationZ(maxZ+1f);
        logTopScroll(refresh,layer,feather);
        layer.setVisibility(View.VISIBLE);
        layer.postInvalidateOnAnimation();
    }

    private final java.util.Map<View,TopCalibration> topCalibrations=new WeakHashMap<>();
    private boolean topIdleHookInstalled;
    private final class TopCalibration {
        private final java.lang.ref.WeakReference<View> root,refresh;
        private final java.lang.ref.WeakReference<StoreTopGlassView> layer;
        TopCalibration(View root,View refresh,StoreTopGlassView layer){this.root=new java.lang.ref.WeakReference<>(root);this.refresh=new java.lang.ref.WeakReference<>(refresh);this.layer=new java.lang.ref.WeakReference<>(layer);}
        void update(){View r=root.get(),v=refresh.get();StoreTopGlassView l=layer.get();if(r==null||v==null||l==null||!r.isShown())return;float value=glassFlag("glass_top_bar")?featherAlpha(v):0f;l.setAlpha(value);l.postInvalidateOnAnimation();}
        void updateAlphaOnly(){View r=root.get(),v=refresh.get();StoreTopGlassView l=layer.get();if(r!=null&&v!=null&&l!=null&&r.isShown()){float value=glassFlag("glass_top_bar")?featherAlpha(v):0f;if(l.getAlpha()!=value)l.setAlpha(value);}}
        void idle(){update();StoreTopGlassView l=layer.get();if(l!=null)l.postDelayed(this::update,100L);}
    }
    private void bindTopScrollCalibration(View root,View refresh,StoreTopGlassView layer){
        TopCalibration calibration=topCalibrations.get(refresh);
        if(calibration==null){
            calibration=new TopCalibration(root,refresh,layer);topCalibrations.put(refresh,calibration);
            final TopCalibration observer=calibration;
            root.getViewTreeObserver().addOnScrollChangedListener(observer::update);
            // Pre-draw performs a final top correction even when nested scroll dispatch is coalesced.
            layer.getViewTreeObserver().addOnPreDrawListener(()->{observer.updateAlphaOnly();return true;});
        }
        View outer=firstScrollable(refresh,0);if(outer!=null){topCalibrations.put(outer,calibration);bindTopLeafCalibrations(outer,calibration);}
        if(topIdleHookInstalled)return;
        try{
            Class<?> recycler=HostSymbols.type("androidx.recyclerview.widget.RecyclerView",loader);
            Method dispatch=recycler.getDeclaredMethod("dispatchOnScrollStateChanged",int.class);
            module.hook(dispatch).intercept(chain->{Object result=chain.proceed();TopCalibration target=topCalibrations.get(chain.getThisObject());if(target!=null&&Integer.valueOf(0).equals(chain.getArgs().get(0)))target.idle();return result;});
            topIdleHookInstalled=true;host.log("Glass top calibration: scroll observer + idle + 100ms");
        }catch(Throwable error){host.log("Glass top idle observer FAILED: "+error.getClass().getSimpleName());}
    }
    private void bindTopLeafCalibrations(View outer,TopCalibration calibration){
        if(!(outer instanceof ViewGroup))return;ViewGroup group=(ViewGroup)outer;
        for(int i=0;i<group.getChildCount();i++){View item=group.getChildAt(i);if(!"com.dragon.read.widget.nestedrecycler.NestedChildLayout".equals(item.getClass().getName()))continue;View leaf=find(item,"com.dragon.read.widget.nestedrecycler.ChildRecyclerView",0);if(leaf!=null)topCalibrations.put(leaf,calibration);}
    }
    private long lastTopLog;
    private void logTopScroll(View refresh,View layer,float alpha){long now=android.os.SystemClock.uptimeMillis();if(now-lastTopLog<1500)return;lastTopLog=now;int offset=topScrollOffset(refresh);int[] pos=new int[2];layer.getLocationOnScreen(pos);host.log("Glass top scroll: scrollY="+offset+" alpha="+alpha+" bounds="+pos[0]+","+pos[1]+"-"+(pos[0]+layer.getWidth())+","+(pos[1]+layer.getHeight())+" z="+layer.getZ());}
    private int topScrollOffset(View refresh){
        View outer=firstScrollable(refresh,0);if(outer==null)return 0;
        if(!outer.canScrollVertically(-1))return 0;
        if(outer instanceof ViewGroup&&((ViewGroup)outer).getChildCount()>0){
            View child=((ViewGroup)outer).getChildAt(0);
            try{int position=((Number)outer.getClass().getMethod("getChildAdapterPosition",View.class).invoke(outer,child)).intValue();if(position==0&&child.getTop()>=outer.getPaddingTop()-2)return 0;}catch(Throwable ignored){}
        }
        int offset=scrollOffset(outer);
        if(outer instanceof ViewGroup){ViewGroup g=(ViewGroup)outer;for(int j=0;j<g.getChildCount();j++){View item=g.getChildAt(j);if(item.getClass().getName().equals("com.dragon.read.widget.nestedrecycler.NestedChildLayout")&&item.getTop()<=outer.getPaddingTop()){View leaf=find(item,"com.dragon.read.widget.nestedrecycler.ChildRecyclerView",0);if(leaf!=null&&leaf.isShown())offset+=scrollOffset(leaf);}}}
        return offset;
    }
    private float featherAlpha(View refresh){int offset=topScrollOffset(refresh);float density=refresh.getResources().getDisplayMetrics().density;float t=Math.max(0f,Math.min(1f,(offset-24f*density)/(72f*density)));return t*t*(3f-2f*t);}
    private int scrollOffset(View v){try{return Math.max(0,((Number)v.getClass().getMethod("computeVerticalScrollOffset").invoke(v)).intValue());}catch(Throwable ignored){return Math.max(0,v.getScrollY());}}
    private int topSurfaceRgb(View top,boolean night){if(top!=null&&top.getBackground() instanceof android.graphics.drawable.ColorDrawable){int color=((android.graphics.drawable.ColorDrawable)top.getBackground()).getColor();if(Color.alpha(color)>180)return color&0xFFFFFF;}return night?0x00101014:0x00FFFFFF;}

    private final class StoreTopGlassView extends View {
        private final View root, source, appbar;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RenderNode blurNode;
        StoreTopGlassView(View root, View source, View appbar) {
            super(root.getContext()); this.root = root; this.source = source; this.appbar = appbar;
            setWillNotDraw(false);
        }
        @Override protected void onDraw(Canvas canvas) {
            if (!glassFlag("glass_top_bar") || !root.isShown() || getWidth() <= 0 || getHeight() <= 0) return;
            int save = canvas.save();
            // The layer begins below the real header, so controls and status icons are outside it.
            boolean night = hostNightMode();
            int rgb = topSurfaceRgb(appbar,night);
            int h = getHeight(), w = getWidth();
            int compositing = canvas.saveLayer(0, 0, w, h, null);
            if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated()) {
                try {
                    float density = getResources().getDisplayMetrics().density;
                    if (blurNode == null) blurNode = new RenderNode("PulpStoreTop");
                    blurNode.setPosition(0, 0, Math.max(1, Math.round(w * SCALE)), Math.max(1, Math.round(h * SCALE)));
                    blurNode.setRenderEffect(RenderEffect.createBlurEffect(18f * density * SCALE,
                            18f * density * SCALE, Shader.TileMode.CLAMP));
                    int[] pos = new int[2], src = new int[2];
                    getLocationOnScreen(pos); source.getLocationOnScreen(src);
                    RecordingCanvas rc = blurNode.beginRecording(Math.max(1, Math.round(w * SCALE)), Math.max(1, Math.round(h * SCALE)));
                    rc.scale(SCALE, SCALE); rc.translate(src[0] - pos[0], src[1] - pos[1]);
                    source.draw(rc); blurNode.endRecording();
                    int nodeSave = canvas.save(); canvas.scale(1f / SCALE, 1f / SCALE);
                    canvas.drawRenderNode(blurNode); canvas.restoreToCount(nodeSave);
                } catch (Throwable ignored) { }
            }
            paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                    new int[]{rgb | 0x70000000, rgb | 0x28000000, rgb}, new float[]{0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                    new int[]{0xFFFFFFFF,0xE8FFFFFF,0x9AFFFFFF,0x35FFFFFF,0x00FFFFFF},new float[]{0f,0.18f,0.45f,0.75f,1f}, Shader.TileMode.CLAMP));
            paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));
            canvas.drawRect(0, 0, w, h, paint); paint.setXfermode(null); paint.setShader(null);
            canvas.restoreToCount(compositing); canvas.restoreToCount(save);
            // Refreshes are driven by the scoped page watcher, not a self-sustaining draw loop.
        }
        private void excludeControls(Canvas canvas, View v, int depth) {
            if (v == null || depth > 15 || !v.isShown()) return;
            String cls = v.getClass().getName(), id = resourceName(v);
            if (v instanceof android.widget.TextView || v instanceof android.widget.ImageView
                    || cls.contains("ComposeView") || "gat".equals(id)) {
                int[] vp = new int[2], lp = new int[2]; v.getLocationOnScreen(vp); getLocationOnScreen(lp);
                canvas.clipOutRect(vp[0] - lp[0], vp[1] - lp[1], vp[0] - lp[0] + v.getWidth(), vp[1] - lp[1] + v.getHeight());
                return;
            }
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0; i < g.getChildCount(); i++) excludeControls(canvas, g.getChildAt(i), depth + 1);
            }
        }
    }

    private boolean isStoreView(View v) {
        for (View cur = v; cur != null;
                cur = cur.getParent() instanceof View ? (View) cur.getParent() : null) {
            String cls = cur.getClass().getName();
            if (HEADER.equals(cls) || "com.dragon.read.component.biz.impl.bookmall.widge.BookMallCoordinatorLayout".equals(cls)) return true;
        }
        return false;
    }

    private final Set<View> storeViewportListeners = Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    private void syncStorePages(View root) {
        View pager = exact(root, "alt", PAGER);
        if (!(pager instanceof ViewGroup)) return;
        ViewGroup pages = (ViewGroup) pager;
        for (int i = 0; i < pages.getChildCount(); i++) {
            View page = pages.getChildAt(i);
            View refresh = exact(page, "ii8", HOST_REFRESH);
            if (refresh == null) continue;
            // Lazy channel pages can retain their original bottom reservation.
            clearRefreshBottom(refresh);
            if (refresh instanceof ViewGroup) ((ViewGroup) refresh).setClipChildren(true);
            if (page instanceof ViewGroup) ((ViewGroup) page).setClipChildren(true);
            View list = firstScrollable(refresh, 0);
            if (list == null || !"com.dragon.read.widget.nestedrecycler.a".equals(list.getClass().getName())) continue;
            if (storeViewportListeners.add(list)) {
                list.addOnLayoutChangeListener((v, l, t, rr, b, ol, ot, or_, ob) -> syncStoreViewport(v));
            }
            syncStoreViewport(list);
        }
    }

    private void syncStoreViewport(View list) {
        int height = list.getHeight();
        if (height <= 0 || !(list instanceof ViewGroup)) return;
        ViewGroup outer = (ViewGroup) list;
        // Only examine direct attached viewport items. Never enter rank ViewHolders.
        for (int i = 0; i < outer.getChildCount(); i++) {
            View item = outer.getChildAt(i);
            if (!"com.dragon.read.widget.nestedrecycler.NestedChildLayout".equals(item.getClass().getName())) continue;
            syncFixedViewportHeight(item, height);
            syncStoreViewportWrappers(item, height, 0);
        }
    }

    private void syncFixedViewportHeight(View v, int height) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null || lp.height <= 0 || lp.height == height) return;
        int before = lp.height;
        lp.height = height;
        v.setLayoutParams(lp);
        host.log("Glass store viewport sync: class=" + v.getClass().getName() + " id=" + resourceName(v)
                + " before=" + before + " parentH=" + height + " delta=" + (height - before));
    }

    private void syncStoreViewportWrappers(View root, int height, int depth) {
        if (!(root instanceof ViewGroup) || depth > 8) return;
        ViewGroup group = (ViewGroup) root;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            String cls = child.getClass().getName();
            String id = resourceName(child);
            if ("com.dragon.read.widget.nestedrecycler.ChildRecyclerView".equals(cls) && "l1".equals(id)) {
                syncFixedViewportHeight(child, height);
                continue; // Stop at the leaf list, never inspect feed cards.
            }
            boolean wrapper = child.getClass() == FrameLayout.class
                    || "com.dragon.read.widget.CommonLayout".equals(cls)
                    || ("androidx.viewpager2.widget.ViewPager2".equals(cls) && "kmw".equals(id))
                    || cls.startsWith("androidx.viewpager2.widget.ViewPager2$");
            if (!wrapper) continue;
            syncFixedViewportHeight(child, height);
            syncStoreViewportWrappers(child, height, depth + 1);
        }
    }

    private final java.util.Map<View, Float> captionTranslations = new WeakHashMap<>();
    private void liftDramaCaptions(View v, View bar, int depth) {
        if (v == null || depth > MAX_DEPTH || !v.isShown()) return;
        // hv6 is the common overlay parent of captions, collection strip and i01/hj3 seek bar.
        // Its video sibling (i1w/hv5) must never be translated.
        if ("hv6".equals(resourceName(v))
                && hasType(v, "androidx.constraintlayout.widget.ConstraintLayout")) {
            float original = captionTranslations.computeIfAbsent(v, key -> key.getTranslationY());
            int[] barPosition = new int[2], rootPosition = new int[2];
            bar.getLocationOnScreen(barPosition);
            View screen = bar.getRootView();
            screen.getLocationOnScreen(rootPosition);
            int clearance = Math.max(0, rootPosition[1] + screen.getHeight() - barPosition[1])
                    + dp(10, bar.getResources().getDisplayMetrics().density);
            float raised = original - clearance;
            if (v.getTranslationY() != raised) v.setTranslationY(raised);
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int j = 0; j < g.getChildCount(); j++) liftDramaCaptions(g.getChildAt(j), bar, depth + 1);
        }
    }

    private boolean bottomReservation(int value, View bar) {
        float density = bar.getResources().getDisplayMetrics().density;
        int height = bar.getHeight() > 0 ? bar.getHeight() : dp(48, density);
        return value >= height - dp(8, density) && value <= height + dp(16, density);
    }

    private void extendTarget(View v, View bar) {
        if (v == null || ScrollWorkGate.busy(v)) return;
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (lp == null) return;
        View parent = v.getParent() instanceof View ? (View) v.getParent() : null;
        int mb = lp instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) lp).bottomMargin : 0;
        int pad = parent != null ? parent.getPaddingBottom() : 0;
        if (geometryLogged.add(v)) host.log("Glass target geometry: class=" + v.getClass().getName()
                + " id=" + resourceName(v) + " height=" + lp.height + " bottomMargin=" + mb
                + " parentPadB=" + pad + " bottom=" + v.getBottom());
        boolean changed = lp.height != ViewGroup.LayoutParams.MATCH_PARENT;
        if (lp instanceof ViewGroup.MarginLayoutParams && bottomReservation(mb, bar)) {
            ((ViewGroup.MarginLayoutParams) lp).bottomMargin = 0;
            changed = true;
        }
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        if (changed) v.setLayoutParams(lp);
        if (parent != null && bottomReservation(pad, bar))
            parent.setPadding(parent.getPaddingLeft(), parent.getPaddingTop(), parent.getPaddingRight(), 0);
        if (v instanceof ViewGroup) ((ViewGroup) v).setClipChildren(true);
    }

    private void applyTargetSafety(View root, View bar) {
        View list = targetScrollable(root, 0);
        if (list != null) applySafetyToList(list, bar);
    }

    private View targetScrollable(View v, int depth) {
        if (v == null || depth > MAX_DEPTH || excludedListBranch(v)) return null;
        if (isMainScrollType(v)) return firstScrollable(v, 0);
        if (hasType(v, "android.widget.ScrollView")) return v;
        if (hasType(v, "androidx.viewpager2.widget.ViewPager2")) return null;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View hit = targetScrollable(g.getChildAt(i), depth + 1);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private void applySafetyToList(View list, View bar) {
        if (hasType(list, "com.dragon.read.widget.nestedrecycler.ParentNestedScrollView")
                || hasType(list, "com.dragon.read.widget.nestedrecycler.a")) {
            clearOuterSafety(list);
            return;
        }
        float density = bar.getResources().getDisplayMetrics().density;
        int safe = (bar.getHeight() > 0 ? bar.getHeight() : dp(48, density))
                + dp(liveInt("glass_bar_bottom") + 16, density);
        if (list.getPaddingBottom() != safe)
            list.setPadding(list.getPaddingLeft(), list.getPaddingTop(), list.getPaddingRight(), safe);
        if (list instanceof ViewGroup) ((ViewGroup) list).setClipToPadding(false);
    }

    private static int dp(int value, float density) { return Math.round(value * density); }

    private Method skinNightMethod;
    private boolean skinNightResolved;

    private boolean isDark(View view) {
        if (view == null) return false;
        if (hostNightMode()) return true;
        View bar = tabTrack(view);
        android.widget.TextView selected = uniqueSelectedTitle(bar);
        if (selected != null) return "短剧".contentEquals(selected.getText());
        int index = currentTab(view);
        if (bar instanceof ViewGroup && index >= 0) {
            ViewGroup tabs = (ViewGroup) bar;
            if (index < tabs.getChildCount()) {
                android.widget.TextView title = mainTabTitle(tabs.getChildAt(index), false, 0);
                return title != null && "短剧".contentEquals(title.getText());
            }
        }
        return false;
    }

    private boolean hostNightMode() {
        try {
            if (!skinNightResolved) {
                skinNightResolved = true;
                skinNightMethod = HostSymbols.type("com.dragon.read.base.skin.SkinManager", loader)
                        .getMethod("isNightMode");
            }
            return skinNightMethod != null && Boolean.TRUE.equals(skinNightMethod.invoke(null));
        } catch (Throwable ignored) { return false; }
    }

    /** Runtime 73932: dsv is the main Fragment FrameLayout, NOT a pager.
     * Its own draw preserves all visible transitioning pages and nested pager viewport transforms.
     * Never record decor/root or a fragment chosen by isShown iteration (would include/compete with bar). */
    private final java.util.Map<View,java.lang.ref.WeakReference<View>> contentViewports=new WeakHashMap<>();
    private View mainContentViewport(View bar) {
        View root=bar.getRootView();java.lang.ref.WeakReference<View> ref=contentViewports.get(root);View content=ref==null?null:ref.get();
        if(content==null||!content.isAttachedToWindow()||content.getRootView()!=root){content=exact(root,"dsv","android.widget.FrameLayout");if(content!=null)contentViewports.put(root,new java.lang.ref.WeakReference<>(content));}
        if(content==null||content.getWidth()<=0||content.getHeight()<=0
                ||content.getRootView()!=root||!visibleContentPath(content,root))return null;
        for(View ancestor=bar;ancestor!=null;ancestor=ancestor.getParent() instanceof View?(View)ancestor.getParent():null)
            if(ancestor==content)return null; // Recursion guard: source must never contain the bottom bar.
        return content;
    }
    /** Ignore only the top window visibility: onDraw is already executing in that window. */
    private boolean visibleContentPath(View content, View root) {
        for (View child = content; child != root; ) {
            if (child == null || child.getVisibility() != View.VISIBLE) return false;
            child = child.getParent() instanceof View ? (View) child.getParent() : null;
        }
        return true;
    }
    private String viewportFailureReason(View bar) {
        View root = bar.getRootView();
        View content = exact(root,"dsv","android.widget.FrameLayout");
        if (content == null) return "dsv-missing-current-root";
        if (content.getRootView() != root) return "dsv-root-mismatch";
        if (content.getWidth() <= 0 || content.getHeight() <= 0) return "dsv-zero-size";
        if (!visibleContentPath(content,root)) return "dsv-subtree-hidden";
        return "dsv-recursion-guard";
    }

    private void appendVisiblePages(View source,StringBuilder pages,int depth){
        if(depth>2||!(source instanceof ViewGroup))return;
        ViewGroup group=(ViewGroup)source;
        for(int i=0;i<group.getChildCount();i++){
            View page=group.getChildAt(i);
            if(page.getVisibility()!=View.VISIBLE||page.getAlpha()<=0f)continue;
            if(pages.length()>0)pages.append(',');
            pages.append(i).append(':').append(page.getClass().getSimpleName()).append('@').append(System.identityHashCode(page))
                    .append(" x=").append(page.getX()).append(" alpha=").append(page.getAlpha());
            View pager=find(page,PAGER,0);
            if(pager!=null)pages.append(" pager=").append(resourceName(pager)).append(" scrollX=").append(pager.getScrollX()).append(" scrollY=").append(pager.getScrollY());
        }
    }
    private long sourceLogAt; private int sourceLogTab=-2,sourceLogX=Integer.MIN_VALUE,sourceLogId;
    private void logBackdropSource(View src,View bar) {
        long now=android.os.SystemClock.uptimeMillis();int tab=selectedTab(bar),x=src.getScrollX(),id=System.identityHashCode(src);
        if(now-sourceLogAt<160L)return;
        if(tab==sourceLogTab&&x==sourceLogX&&id==sourceLogId&&now-sourceLogAt<1500L)return;
        sourceLogAt=now;sourceLogTab=tab;sourceLogX=x;sourceLogId=id;
        StringBuilder pages=new StringBuilder();appendVisiblePages(src,pages,0);
        host.log("Glass sample: tab="+tab+" source="+src.getClass().getName()+"@"+id+" id="+resourceName(src)+" scrollX="+x+" scrollY="+src.getScrollY()+" pages=["+pages+"]");
    }

    private int currentTab(View view) {
        try {
            View mainBar=find(view.getRootView(),BOTTOM,0);
            if(mainBar!=null)return selectedTab(mainBar);

        } catch (Throwable ignored) { }
        return -1;
    }

    private boolean isMainTabTitle(android.widget.TextView tv) {
        String text = tv.getText().toString().trim();
        if (text.contains("福利") || text.contains("现金") || text.contains("赚钱")) return true;
        return "书城".equals(text) || "短剧".equals(text) || "赚钱".equals(text)
                || "书架".equals(text) || "我的".equals(text);
    }

    private android.widget.TextView mainTabTitle(View v, boolean selectedOnly, int depth) {
        if (v == null || depth > 8 || v.getVisibility() != View.VISIBLE) return null;
        if (v instanceof android.widget.TextView) {
            android.widget.TextView tv = (android.widget.TextView) v;
            if (isMainTabTitle(tv) && (!selectedOnly || tv.isSelected())) return tv;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                android.widget.TextView hit = mainTabTitle(g.getChildAt(i), selectedOnly, depth + 1);
                if (hit != null) return hit;
            }
        }
        return null;
    }

    private android.widget.TextView uniqueSelectedTitle(View bar) {
        if (!(bar instanceof ViewGroup)) return null;
        ViewGroup tabs = (ViewGroup) bar;
        android.widget.TextView result = null;
        for (int i = 0; i < tabs.getChildCount(); i++) {
            android.widget.TextView hit = mainTabTitle(tabs.getChildAt(i), true, 0);
            if (hit != null) {
                if (result != null) return null;
                result = hit;
            }
        }
        return result;
    }

    /** 相对亮度（0..1）。 */
    private static float luminance(int color) {
        float r = Color.red(color) / 255f;
        float g = Color.green(color) / 255f;
        float b = Color.blue(color) / 255f;
        return 0.299f * r + 0.587f * g + 0.114f * b;
    }

    /** 玻璃本体：圆形裁切 + 模糊 + 着色 + 高光。 */
    private final class GlassDrawable extends Drawable {
        private final View target;
        private final int corner;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int lastBlur = -1;

        GlassDrawable(View target, boolean floating, int corner) {
            this.target = target;
            this.corner = corner;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1f, target.getResources().getDisplayMetrics().density));
        }

        @Override public void draw(Canvas canvas) {
            int w = target.getWidth(), h = target.getHeight();
            if (w <= 0 || h <= 0) return;
            boolean dark = isDark(target);
            int alpha = Math.max(15, Math.min(90, host.intValue("glass_bar_alpha")));
            applyBlur(host.intValue("glass_bar_blur"),
                    target.getResources().getDisplayMetrics().density);

            int save = canvas.save();
            if (corner > 0) {
                android.graphics.Path path = new android.graphics.Path();
                path.addRoundRect(new RectF(0, 0, w, h), corner, corner,
                        android.graphics.Path.Direction.CW);
                canvas.clipPath(path);
            }

            // 着色层只做柔和压暗，不能盖住封面颜色：
            // alpha 滑块 10%~90% 线性映射到 8%~72% 的实际不透明度。
            int base = dark ? 0xFF202024 : 0xFFF2F2F6;
            int a = (int) ((8 + alpha * 0.64f) / 100f * 255f);
            int top = (base & 0x00FFFFFF) | (a << 24);
            fill.setShader(new android.graphics.LinearGradient(0, 0, 0, h,
                    shift(top, dark ? 10 : 6), shift(top, dark ? -6 : -10), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, fill);
            fill.setShader(null);

            rim.setColor(dark ? 0x33FFFFFF : 0x77FFFFFF);
            canvas.drawLine(0, rim.getStrokeWidth() / 2f, w, rim.getStrokeWidth() / 2f, rim);
            rim.setColor(dark ? 0x18FFFFFF : 0x33FFFFFF);
            canvas.drawLine(0, h - rim.getStrokeWidth() / 2f, w, h - rim.getStrokeWidth() / 2f, rim);
            canvas.restoreToCount(save);
        }

        /**
         * 不再对底栏自身调用 setRenderEffect。
         *
         * RenderEffect 作用在 View 的整个 RenderNode 上，会把它的子 View
         * （BottomTabScaleLayout / BottomTabBarLayout 里的 5 个 Tab 图标与文字）
         * 一起模糊——实机表现就是「底栏文字全部消失」。
         *
         * 真正的磨砂感改由下方内容自身半透明叠加 + 渐变着色 + 高光描边呈现：
         * 底栏背景保持完全透明，彩色封面直接透过来，
         * 再用低不透明度着色层把它压成柔和的毛玻璃观感，Tab 文字始终锐利。
         */
        private void applyBlur(int blur, float density) {
            // 有意留空：模糊不能施加在承载文字的容器上。
            lastBlur = blur;
        }

        private int shift(int c, int delta) {
            int r = Math.max(0, Math.min(255, Color.red(c) + delta));
            int g = Math.max(0, Math.min(255, Color.green(c) + delta));
            int b = Math.max(0, Math.min(255, Color.blue(c) + delta));
            return Color.argb(Color.alpha(c), r, g, b);
        }

        @Override public void setAlpha(int a) { }
        @Override public void setColorFilter(android.graphics.ColorFilter f) { }
        @Override public int getOpacity() { return android.graphics.PixelFormat.TRANSLUCENT; }
    }
}