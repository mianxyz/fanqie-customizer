package io.github.mianxyz.fanqiecustomizer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewTreeObserver;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

/** Apply visibility before first drawing, without removing native buttons or changing their indices. */
final class BottomNavigationHooks {
    private static final String MAIN="com.dragon.read.pages.main.MainFragmentActivity";
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    private final Set<String> hiddenKeys=new HashSet<>();
    private final Set<View> watched=Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<View,Boolean>()));
    private final Map<View,Integer> originals=Collections.synchronizedMap(new WeakHashMap<>());
    private Method buttons,buttonView,buttonType;
    BottomNavigationHooks(XposedModule module,FeatureHooks.Host host,ClassLoader loader){
        this.module=module;this.host=host;this.loader=loader;
        for(String key:Rules.TAB_KEYS)if(host.on(key))hiddenKeys.add(key);
    }
    void install(){
        if(!host.on("enabled")||hiddenKeys.isEmpty())return;
        try{
            Class<?> bar=HostSymbols.type(BottomNavigationRules.BAR,loader),button=HostSymbols.type("dk7.g",loader);
            buttons=bar.getDeclaredMethod(HostSymbols.methodName(bar,"getTabButtonList"));
            buttonView=button.getMethod(HostSymbols.publicMethodName(button,"getView"));buttonType=button.getMethod(HostSymbols.publicMethodName(button,"a"));
            if(buttons.getReturnType()!=List.class||buttonView.getReturnType()!=View.class||!buttonType.getReturnType().isEnum())throw new NoSuchMethodException();
            Constructor<?> constructor=bar.getDeclaredConstructor(Context.class,AttributeSet.class,int.class);
            module.hook(constructor).intercept(chain->{
                Object result=chain.proceed();attach(chain.getThisObject());return result;
            });
            after(bar.getDeclaredMethod(HostSymbols.methodName(bar,"Q1"),button));
            after(bar.getDeclaredMethod(HostSymbols.methodName(bar,"R1"),button,boolean.class,boolean.class));
            host.log("Bottom navigation early binding hooks registered");
        }catch(Throwable error){failure("installation",error);}
        // The app also draws a separate five-icon Lottie row beside the native bar.
        // Select its existing static-icon branch while individual entries are hidden.
        try{
            Class<?> main=HostSymbols.type(MAIN,loader);Method animated=main.getDeclaredMethod(HostSymbols.methodName(main,"r2"));
            if(animated.getReturnType()!=boolean.class||!java.lang.reflect.Modifier.isStatic(animated.getModifiers()))throw new NoSuchMethodException();
            module.hook(animated).intercept(chain->{
                if(!BottomNavigationRules.animation(host.on("enabled"),hiddenKeys,true))return false;
                return chain.proceed();
            });
            for(Method method:main.getDeclaredMethods())if(HostSymbols.matches(method,"c2")||HostSymbols.matches(method,"onCreate"))module.deoptimize(method);
            host.log("Bottom navigation startup icon hook registered");
        }catch(Throwable error){failure("animation",error);}
    }
    private void after(Method method){
        if(method.getReturnType()!=void.class)throw new IllegalArgumentException("Unexpected navigation method");
        module.hook(method).intercept(chain->{Object result=chain.proceed();attach(chain.getThisObject());return result;});
    }
    private void attach(Object receiver){
        try{
            if(!(receiver instanceof View))return;View bar=(View)receiver;
            if(!HostSymbols.name(BottomNavigationRules.BAR).equals(bar.getClass().getName()))return;
            if(watched.add(bar)){
                DrawGuard guard=new DrawGuard(bar);bar.addOnAttachStateChangeListener(guard);
                if(bar.isAttachedToWindow())guard.onViewAttachedToWindow(bar);
            }
            apply(bar);
        }catch(Throwable error){failure("binding",error);}
    }
    /** The old delayed traversal and the early hooks intentionally share this exact implementation. */
    boolean apply(View bar){
        if(!HostSymbols.name(BottomNavigationRules.BAR).equals(bar.getClass().getName()))return false;
        try{
            if(buttons==null){
                buttons=bar.getClass().getMethod(HostSymbols.publicMethodName(bar.getClass(),"getTabButtonList"));
                Class<?> button=HostSymbols.type("dk7.g",loader);buttonView=button.getMethod(HostSymbols.publicMethodName(button,"getView"));buttonType=button.getMethod(HostSymbols.publicMethodName(button,"a"));
            }
            Object list=buttons.invoke(bar);if(!(list instanceof List<?>))return false;
            List<View> views=new ArrayList<>();List<String> types=new ArrayList<>();
            // Read only: Main uses this same backing list and child order for native click indices.
            for(Object button:(List<?>)list){
                if(button==null)continue;Object rawView=buttonView.invoke(button);if(!(rawView instanceof View))continue;
                Object type=buttonType.invoke(button);views.add((View)rawView);types.add(type instanceof Enum<?>?((Enum<?>)type).name():null);
            }
            boolean[] hidden=BottomNavigationRules.hidden(host.on("enabled"),types,hiddenKeys);
            boolean changed=false;
            for(int i=0;i<views.size();i++){
                View view=views.get(i);
                if(hidden[i]){
                    if(view.getVisibility()!=View.GONE){originals.put(view,view.getVisibility());view.setVisibility(View.GONE);changed=true;}
                }else{
                    Integer original=originals.remove(view);
                    if(original!=null&&view.getVisibility()==View.GONE){view.setVisibility(original);changed=true;}
                }
            }
            if(changed)host.log("Bottom navigation visibility applied before draw");
            return changed;
        }catch(Throwable error){failure("visibility",error);return false;}
    }
    private void failure(String phase,Throwable error){host.log("Bottom navigation "+phase+" failed: "+PrivacyRules.errorType(error));}
    private final class DrawGuard implements View.OnAttachStateChangeListener,ViewTreeObserver.OnPreDrawListener {
        private final View bar;private ViewTreeObserver observer;
        DrawGuard(View bar){this.bar=bar;}
        @Override public void onViewAttachedToWindow(View attached){
            apply(bar);observer=bar.getViewTreeObserver();if(observer.isAlive())observer.addOnPreDrawListener(this);
        }
        @Override public void onViewDetachedFromWindow(View detached){
            if(observer!=null&&observer.isAlive())observer.removeOnPreDrawListener(this);observer=null;
        }
        @Override public boolean onPreDraw(){return !apply(bar);}
    }
}
