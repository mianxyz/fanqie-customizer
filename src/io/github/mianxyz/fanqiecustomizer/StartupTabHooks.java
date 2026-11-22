package io.github.mianxyz.fanqiecustomizer;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedModule;

/** Change the initial native destination, rather than clicking an invisible tab later. */
final class StartupTabHooks {
    private static final String MAIN="com.dragon.read.pages.main.MainFragmentActivity";
    private static final String TYPE="com.dragon.read.rpc.model.BottomTabBarItemType";
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    private final Map<Activity,State> states=Collections.synchronizedMap(new WeakHashMap<>());
    private static final class State {boolean initializing=true,finished;}
    StartupTabHooks(XposedModule module,FeatureHooks.Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}
    void install() {
        if(!host.on("enabled"))return;
        boolean needed=false;
        for(String key:StartupTabRules.KEYS)needed|=host.on(key);
        for(String key:Rules.TAB_KEYS)needed|=host.on(key);
        if(!needed)return;
        try {
            Class<?> main=HostSymbols.type(MAIN,loader),type=HostSymbols.type(TYPE,loader);
            Method create=main.getDeclaredMethod(HostSymbols.methodName(main,"onCreate"),Bundle.class);
            module.hook(create).intercept(chain->{
                Activity activity=(Activity)chain.getThisObject();State state=null;
                try {
                    Intent intent=activity.getIntent();Integer numeric=intent!=null&&intent.hasExtra("key_default_tab")?intent.getIntExtra("key_default_tab",-1):null;
                    String data=intent==null?null:intent.getDataString();
                    boolean eligible=StartupTabRules.normalEntry(chain.getArg(0)!=null,intent==null?null:intent.getAction(),data,
                        intent==null?null:intent.getStringExtra("tabName"),numeric);
                    host.log("Startup tab entry: eligible="+eligible+"; restored="+(chain.getArg(0)!=null)+"; nativeMain="+StartupTabRules.internalMain(data));
                    if(eligible){state=new State();states.put(activity,state);}
                }catch(Throwable error){host.log("Startup tab entry classification failed: "+PrivacyRules.errorType(error));}
                try{return chain.proceed();}finally{if(state!=null)state.initializing=false;}
            });
            module.hook(main.getDeclaredMethod(HostSymbols.methodName(main,"X1"))).intercept(chain->{
                Object result=chain.proceed();Activity activity=(Activity)chain.getThisObject();State state=states.get(activity);
                if(state==null||!state.initializing||state.finished)return result;
                try{return resolve(result);}catch(Throwable error){host.log("Startup tab resolution failed: "+PrivacyRules.errorType(error));return result;}
            });
            module.hook(main.getDeclaredMethod(HostSymbols.methodName(main,"x2"),type,boolean.class,boolean.class,String.class,boolean.class)).intercept(chain->{
                Object[] args=chain.getArgs().toArray();State state=states.get((Activity)chain.getThisObject());
                if(state!=null&&!state.finished){
                    if(Boolean.TRUE.equals(args[1]))state.finished=true; // A user click takes precedence over pending startup work.
                    else if("default".equals(args[3])){
                        state.finished=true;
                        try{args[0]=resolve(args[0]);}catch(Throwable error){host.log("Startup tab dispatch failed: "+PrivacyRules.errorType(error));}
                    }
                }
                return chain.proceed(args);
            });
            module.hook(main.getDeclaredMethod(HostSymbols.methodName(main,"onNewIntent"),Intent.class)).intercept(chain->{
                State state=states.get((Activity)chain.getThisObject());if(state!=null)state.finished=true;
                return chain.proceed();
            });
            module.deoptimize(create);
            module.deoptimize(main.getDeclaredMethod(HostSymbols.methodName(main,"w2"),type,boolean.class,boolean.class,String.class));
            host.log("Startup tab native hooks registered");
        }catch(Throwable error){host.log("Startup tab hooks unavailable: "+PrivacyRules.errorType(error));}
    }
    private Object resolve(Object original)throws Throwable {
        Field tabs=HostSymbols.type("com.dragon.read.pages.main.m4",loader).getDeclaredField(HostSymbols.fieldName("com.dragon.read.pages.main.m4","e"));tabs.setAccessible(true);
        Object value=tabs.get(null);if(!(value instanceof List<?>))return original;
        Map<String,Object> actual=new LinkedHashMap<>();Set<String> hidden=new HashSet<>();
        for(Object tab:(List<?>)value)if(tab instanceof Enum<?>){
            String name=((Enum<?>)tab).name();actual.put(name,tab);
            String key=Rules.tabKey(name);if(key!=null&&host.on(key))hidden.add(name);
        }
        String preferred=null;for(int i=0;i<StartupTabRules.KEYS.length;i++)if(host.on(StartupTabRules.KEYS[i])){preferred=StartupTabRules.TYPES[i];break;}
        String old=original instanceof Enum<?>?((Enum<?>)original).name():null;
        String next=StartupTabRules.choose(host.on("enabled"),preferred,new ArrayList<>(actual.keySet()),hidden,old);
        Object result=actual.get(next);
        if(result==null||result==original)return original;
        host.log("Startup tab selected: "+next);
        return result;
    }
}
