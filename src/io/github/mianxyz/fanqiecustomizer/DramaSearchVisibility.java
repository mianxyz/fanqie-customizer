package io.github.mianxyz.fanqiecustomizer;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import io.github.libxposed.api.XposedModule;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Synchronous, reversible visibility ownership; no layout-height or cached model writes. */
final class DramaSearchVisibility {
    private static final String COMMON = "com.dragon.read.widget.search.CommonSearchBar";
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final Map<View,Integer> hidden = new WeakHashMap<>();
    private final Set<View> watched = Collections.newSetFromMap(new WeakHashMap<View,Boolean>());
    private final Set<View> known = Collections.newSetFromMap(new WeakHashMap<View,Boolean>());
    private final ThreadLocal<Boolean> writing = new ThreadLocal<>();
    private long visibilityRevision;

    DramaSearchVisibility(XposedModule module, FeatureHooks.Host host) {
        this.module=module;this.host=host;
    }

    void install() {
        if(!HostSymbols.requireCapability("DramaSearchVisibility",new String[]{"androidx.appcompat.widget.StretchLeftLayout","com.dragon.read.widget.search.CommonSearchBar"},new String[]{},new String[]{},new String[]{"gat","ixs"}))return;
        hook(View.class,"setVisibility",true);
        hook(View.class,"dispatchAttachedToWindow",false);
        hook(View.class,"onFinishInflate",false);
        hook(ViewGroup.class,"addView",false);
        hook(Activity.class,"onResume",false);
    }

    private void hook(Class<?> type,String name,boolean visibility) {
        int hits=0;
        for(Method method:type.getDeclaredMethods()) {
            if(!method.getName().equals(HostSymbols.methodName(type,name)))continue;
            if(visibility&&(method.getParameterTypes().length!=1||method.getParameterTypes()[0]!=int.class))continue;
            try {
                module.hook(method).intercept(chain->{
                    Object self=chain.getThisObject();
                    if(visibility&&self instanceof View&&!Boolean.TRUE.equals(writing.get())) {
                        View view=(View)self;
                        if(isTarget(view)||hidden.containsKey(view)) {
                            known.add(view);watch(view.getRootView());
                            Object[] args=chain.getArgs().toArray();
                            int requested=(Integer)args[0];
                            if(active()&&isTarget(view)) {
                                // Preserve even INVISIBLE/GONE requests made while we own visibility.
                                hidden.put(view,requested);args[0]=View.GONE;
                            } else hidden.remove(view);
                            return chain.proceed(args);
                        }
                    }
                    Object result=chain.proceed();
                    if(Boolean.TRUE.equals(writing.get()))return result;
                    try {
                        if(self instanceof Activity) {
                            Activity activity=(Activity)self;
                            if(activity.getWindow()!=null)bindTree(activity.getWindow().getDecorView());
                        } else if(self instanceof View) {
                            View view=(View)self;
                            if("addView".equals(name)) {
                                for(Object arg:chain.getArgs())if(arg instanceof View) {
                                    View child=(View)arg;
                                    if(isTarget(child)||"ixs".equals(id(child)))bindTree(child);
                                }
                            } else if(isTarget(view)||"ixs".equals(id(view)))bindTree(view);
                        }
                    } catch(Throwable error) {host.log("Search visibility sync failed: "+PrivacyRules.errorType(error));}
                    return result;
                });
                hits++;
            } catch(Throwable error) {host.log("Search visibility hook unavailable: "+name+" "+PrivacyRules.errorType(error));}
        }
        host.log("Search visibility hooks: "+type.getSimpleName()+"."+name+"="+hits);
    }

    private boolean active(){return host.on("enabled")&&host.on("hide_search_bar");}

    private boolean isTarget(View view) {
        for(Class<?> type=view.getClass();type!=null;type=type.getSuperclass())
            if(HostSymbols.name(COMMON).equals(type.getName()))return true;
        // 73932 evidence: ixs FrameLayout > gat StretchLeftLayout > hlg SearchWordDisplayView.
        if(!"gat".equals(id(view))||!HostSymbols.name("androidx.appcompat.widget.StretchLeftLayout").equals(view.getClass().getName()))return false;
        for(View parent=view.getParent() instanceof View?(View)view.getParent():null;parent!=null;
                parent=parent.getParent() instanceof View?(View)parent.getParent():null) {
            if("ixs".equals(id(parent)))return true;
        }
        return false;
    }

    private void bindTree(View root) {
        ArrayDeque<View> queue=new ArrayDeque<>();queue.add(root);int visited=0;
        while(!queue.isEmpty()&&visited++<5000) {
            View view=queue.removeFirst();
            if(isTarget(view)) {known.add(view);sync(view);watch(view.getRootView());}
            if("ixs".equals(id(view)))watch(view.getRootView());
            if(view instanceof ViewGroup) {
                ViewGroup group=(ViewGroup)view;
                for(int i=0;i<group.getChildCount();i++)queue.addLast(group.getChildAt(i));
            }
        }
    }

    private void sync(View view) {
        boolean hide=active()&&isTarget(view);
        Integer original=hidden.get(view);
        if(hide) {
            if(original==null){hidden.put(view,view.getVisibility());host.log("Search visibility hidden: id="+id(view)+" original="+view.getVisibility());}
            write(view,View.GONE);
        } else if(original!=null) {
            hidden.remove(view);write(view,original);
            host.log("Search visibility restored: id="+id(view)+" visibility="+original);
        }
    }

    private void write(View view,int visibility) {
        if(view.getVisibility()==visibility)return;
        writing.set(true);
        try {view.setVisibility(visibility);visibilityRevision++;} finally {writing.remove();}
    }

    private void watch(View root) {
        if(!watched.add(root))return;
        WeakReference<View> reference=new WeakReference<>(root);
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
            public boolean onPreDraw() {
                View decor=reference.get();if(decor==null)return true;
                long before=visibilityRevision;
                // A hidden target does not draw, so restoration belongs to its root's pre-draw.
                for(View view:new ArrayList<>(known))if(view!=null&&view.getRootView()==decor)sync(view);
                int containerId=decor.getResources().getIdentifier(HostSymbols.resourceName("ixs"),"id",decor.getContext().getPackageName());
                View container=containerId==0?null:decor.findViewById(containerId);
                if(container!=null)syncDramaChildren(container,0);
                // A GONE change requests layout: skip this stale-layout frame.
                return before==visibilityRevision;
            }
        });
    }

    private void syncDramaChildren(View view,int depth) {
        if(depth>8)return;
        if(isTarget(view))sync(view);
        if(view instanceof ViewGroup) {
            ViewGroup group=(ViewGroup)view;
            for(int i=0;i<group.getChildCount();i++)syncDramaChildren(group.getChildAt(i),depth+1);
        }
    }

    private static String id(View view) {
        if(view.getId()==View.NO_ID)return "";
        try{return HostSymbols.canonicalResource(view.getResources().getResourceEntryName(view.getId()));}catch(Throwable ignored){return "";}
    }
}
