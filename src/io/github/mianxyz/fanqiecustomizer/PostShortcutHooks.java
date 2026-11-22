package io.github.mianxyz.fanqiecustomizer;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.View;
import android.view.ViewTreeObserver;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/** Hide the main-page editor root before its first frame, without hooking generic View calls. */
final class PostShortcutHooks {
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    PostShortcutHooks(XposedModule module,FeatureHooks.Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}

    void install(){
        if(!host.on(PostShortcutRules.KEY))return;
        try{
            Class<?> root=HostSymbols.type(PostShortcutRules.ROOT,loader);
            Constructor<?> constructor=root.getDeclaredConstructor(Context.class);
            module.hook(constructor).intercept(chain->{
                Object result=chain.proceed();
                try{
                    Object receiver=chain.getThisObject();
                    if(receiver instanceof View&&target((View)receiver)){
                        View view=(View)receiver;
                        // Constructor completion precedes insertion into either the bookmall or Mine tree.
                        hide(view);
                        view.addOnAttachStateChangeListener(new DrawGuard(view));
                        host.log("Post shortcut hidden before first attachment");
                    }
                }catch(Throwable error){host.log("Post shortcut initialization failed: "+PrivacyRules.errorType(error));}
                return result;
            });
            host.log("Post shortcut constructor hook registered");
        }catch(Throwable error){host.log("Post shortcut constructor unavailable: "+PrivacyRules.errorType(error));}
        try{
            Method visibility=HostSymbols.type(PostShortcutRules.ROOT,loader).getDeclaredMethod(HostSymbols.methodName(PostShortcutRules.ROOT,"setVisibilitySafely"),int.class);
            if(visibility.getReturnType()!=void.class)throw new NoSuchMethodException();
            module.hook(visibility).intercept(chain->{
                Object[] args=chain.getArgs().toArray();
                try{
                    Object receiver=chain.getThisObject();
                    if(receiver instanceof View){
                        View view=(View)receiver;
                        String owner=activity(view.getContext()),name=HostSymbols.canonical(view.getClass().getName());
                        boolean enabled=host.on(PostShortcutRules.KEY);
                        args[0]=PostShortcutRules.visibility(enabled,owner,name,(Integer)args[0]);
                        if(PostShortcutRules.suppress(enabled,owner,name))hide(view);
                    }
                }catch(Throwable error){host.log("Post shortcut visibility failed: "+PrivacyRules.errorType(error));}
                return chain.proceed(args);
            });
            host.log("Post shortcut visibility hook registered");
        }catch(Throwable error){host.log("Post shortcut visibility unavailable: "+PrivacyRules.errorType(error));}
        // Static show/hide helpers call the private visibility method and may have inlined it.
        deoptimize("a");deoptimize("c");
    }
    private boolean target(View view){
        return PostShortcutRules.suppress(host.on(PostShortcutRules.KEY),activity(view.getContext()),HostSymbols.canonical(view.getClass().getName()));
    }
    private static String activity(Context context){
        for(int i=0;i<12&&context!=null;i++){
            if(context instanceof Activity)return HostSymbols.canonical(context.getClass().getName());
            if(!(context instanceof ContextWrapper))break;
            Context base=((ContextWrapper)context).getBaseContext();if(base==context)break;context=base;
        }
        return null;
    }
    private void hide(View view){
        // This root is unattached in the constructor; setting visibility now avoids a delayed first frame.
        if(view.getVisibility()!=View.GONE)view.setVisibility(View.GONE);
        host.forceHide(view,"post shortcut before draw");
    }
    private void deoptimize(String name){
        try{for(Method method:HostSymbols.type(PostShortcutRules.ROOT,loader).getDeclaredMethods())if(HostSymbols.matches(method,name))module.deoptimize(method);}
        catch(Throwable error){host.log("Post shortcut caller unavailable: "+PrivacyRules.errorType(error));}
    }
    private final class DrawGuard implements View.OnAttachStateChangeListener,ViewTreeObserver.OnPreDrawListener {
        private final View view;
        private ViewTreeObserver observer;
        DrawGuard(View view){this.view=view;}
        @Override public void onViewAttachedToWindow(View attached){
            if(target(view))hide(view);
            observer=view.getViewTreeObserver();if(observer.isAlive())observer.addOnPreDrawListener(this);
        }
        @Override public void onViewDetachedFromWindow(View detached){
            if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(this);observer=null;
        }
        @Override public boolean onPreDraw(){
            // Native animations also write visibility directly. Guard only this registered floating root.
            if(target(view)&&view.getVisibility()!=View.GONE)hide(view);
            return true;
        }
    }
}
