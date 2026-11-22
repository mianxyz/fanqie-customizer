package io.github.mianxyz.fanqiecustomizer;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.TextView;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * 书城局部精简：搜索栏。
 *
 * 方法名来自 7.4.5.32 真实 APK 反汇编：
 *   CommonSearchBar.initView           搜索栏初始化
 *   NewRankListHolder.onBind           排行榜条目绑定
 *   NewRankCategorySiftHolderV2.onBind 排行榜筛选条目绑定
 *
 * 性能注意（这一版重写的原因）：
 * 旧实现里 shouldHide(child) 会递归收集该子树的全部文字，而没有命中时
 * 又对每个子节点重复同样的收集，整体是 O(n²)；挂在 onBind 这种列表滚动
 * 热路径上，会直接把首屏和滑动拖慢数秒。
 * 现在改成一次后序遍历：文字自底向上汇总，每个节点只被访问一次。
 */
final class BookmallCleanup {
    private static final String[] BIND = {"onBind", "initView"};
    /** 递归上限。再深就不是频道/推荐流的层级了。 */
    private static final int MAX_DEPTH = 8;

    private static final Set<View> handled =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<View, Boolean>()));

    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;

    BookmallCleanup(XposedModule module, FeatureHooks.Host host, ClassLoader loader) {
        this.module = module;
        this.host = host;
        this.loader = loader;
    }

    void install() {
        if(!HostSymbols.requireCapability("BookmallCleanup",new String[]{"com.dragon.read.widget.search.CommonSearchBar"},new String[]{},new String[]{},new String[]{}))return;
        int hits = 0;
        if (host.on("hide_search_bar")) hits += hook("com.dragon.read.widget.search.CommonSearchBar");
        // 频道名隐藏已下线：BookMallChannelTabProvider 不是 View（反汇编确认），
        // 渲染顶部频道 Tab 的真实 View 类尚未定位，不做臆造落点。
        host.log("Bookmall cleanup hooks registered: " + hits);
    }

    private int hook(String className) {
        int count = 0;
        try {
            Class<?> cls = HostSymbols.type(className,loader);
            for (Method method : cls.getDeclaredMethods()) {
                if (!HostSymbols.matches(method,"onBind") && !HostSymbols.matches(method,"initView")) continue;
                // 注意：initView 是无参方法，不能要求必须有参数，
                // 否则搜索栏这一支永远不会被挂上。
                module.hook(method).intercept(chain -> {
                    Object result = chain.proceed();
                    try {
                        // onBind 的签名是 (Object,int)，参数里没有 View！
                        // holder 自己就是 View，必须从 this 取，否则永远拿不到根节点——
                        // 这正是上一版搜索栏/排行榜全部无效的直接原因。
                        Object self = chain.getThisObject();
                        if (self instanceof View) {
                            cleanup((View) self);
                        } else {
                            View root = findViewField(self);
                            if (root != null) cleanup(root);
                        }
                        for (Object arg : chain.getArgs()) {
                            if (arg instanceof View) cleanup((View) arg);
                        }
                    } catch (Throwable error) {
                        host.log("Bookmall cleanup failed: " + error.getClass().getName() + ": " + error.getMessage());
                    }
                    return result;
                });
                count++;
            }
        } catch (Throwable missing) {
            host.log("Bookmall class missing: " + className);
        }
        return count;
    }

    /**
     * holder 不是 View 时，找它的根 View。
     *
     * 反汇编确认：NewRankListHolder / NewRankCategorySiftHolderV2 的父类是
     * holder/m2，再往上到 AbsRecyclerViewHolder →
     * fluency.monitor.recyclerview.AbsMonitorRecyclerViewHolder →
     * RecyclerView.ViewHolder。itemView 定义在最后的 ViewHolder 上，
     * 因此必须逐层向上遍历父类，getDeclaredFields() 只看本类会永远拿不到。
     *
     * 优先取 m2 上的 viewDataBinding（那是根布局），其次才是 itemView。
     */
    private View findViewField(Object holder) {
        if (holder == null) return null;
        Class<?> cls = holder.getClass();
        // 第一优先：ViewDataBinding 的 getRoot() —— 反汇编确认 m2 持有它。
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!f.getType().getName().startsWith("androidx.databinding.")) continue;
                try {
                    f.setAccessible(true);
                    Object binding = f.get(holder);
                    if (binding == null) continue;
                    Object root = binding.getClass().getMethod(HostSymbols.publicMethodName(binding.getClass(),"getRoot")).invoke(binding);
                    if (root instanceof View) return (View) root;
                } catch (Throwable ignored) {
                }
            }
        }
        // 第二优先：沿父类链找 View 类型字段，尤其是 itemView。
        for (Class<?> c = cls; c != null && c != Object.class; c = c.getSuperclass()) {
            for (java.lang.reflect.Field f : c.getDeclaredFields()) {
                if (!View.class.isAssignableFrom(f.getType())) continue;
                try {
                    f.setAccessible(true);
                    Object v = f.get(holder);
                    if (v instanceof View) return (View) v;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean contains(String[] arr, String v) {
        for (String s : arr) if (s.equals(v)) return true;
        return false;
    }

    private void cleanup(View root) {
        if (root == null || handled.contains(root)) return;
        handled.add(root);
        visit(root, 0);
    }

    /**
     * 单次后序遍历：返回本子树拼接后的文字（上限 limit 字符），
     * 顺便在确定「这一整块该隐藏」时把它收掉。
     *
     * 关键点：父节点拿到的文字是子节点返回值拼起来的，不再重复遍历子树。
     */
    /**
     * 纯结构遍历：只按类名判定，不再汇总文字。
     * 这样每个节点只被访问一次，且判定与文案无关。
     */
    private void visit(View view, int depth) {
        if (!(view instanceof ViewGroup) || depth > MAX_DEPTH) return;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child == null || child.getVisibility() == View.GONE) continue;
            if (shouldHide(child)) {
                child.setVisibility(View.GONE);
                ViewGroup.LayoutParams lp = child.getLayoutParams();
                if (lp != null) {
                    lp.height = 0;
                    child.setLayoutParams(lp);
                }
                continue;
            }
            visit(child, depth + 1);
        }
    }

    /**
     * 只按类名判定，不做任何中文文字匹配。
     *
     * 之前用 text.contains("搜索") 会误伤：书城里任何带「搜索」字样的推荐位、
     * 搜索历史卡片、热搜榜都会被一起隐藏。类名是稳定标识，文案会变。
     */
    private boolean shouldHide(View view) {
        if (view == null) return false;
        if (host.on("hide_search_bar") && classChainContains(view, "CommonSearchBar")) return true;
        return false;
    }

    /** 沿继承链检查类名，避免只看最外层漏掉子类。 */
    private boolean classChainContains(View view, String token) {
        for (Class<?> c = view.getClass(); c != null; c = c.getSuperclass()) {
            if (HostSymbols.canonical(c.getName()).contains(token)) return true;
        }
        return false;
    }

}
