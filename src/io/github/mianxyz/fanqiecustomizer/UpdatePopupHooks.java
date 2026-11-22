package io.github.mianxyz.fanqiecustomizer;

import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

/** Hook native automatic upgrade paths before a window or popup ticket is created. */
final class UpdatePopupHooks {
    private static final String MANAGER="com.dragon.read.component.biz.impl.update.UpdateManager";
    private static final String PROPERTIES="com.dragon.read.pop.IProperties";
    private static final String LISTENER="com.dragon.read.pop.IPopProxy$IListener";
    private static final String RUNNABLE="com.dragon.read.pop.IPopProxy$IRunnable";
    private static final String TICKET="com.dragon.read.pop.IPopProxy$IPopTicket";
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    UpdatePopupHooks(XposedModule module,FeatureHooks.Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}

    void install() {
        if(!host.on("hide_update_popup"))return;
        // h is automatic presentation; the manual UpdateManager$b.a(int) dialog is separate.
        hook(MANAGER,"h","com.dragon.read.widget.dialog.DialogBase",new String[]{},args->{
            if(!host.on("hide_update_popup"))return Decision.pass();
            host.log("Automatic upgrade presentation suppressed");return Decision.stop(null);
        });
        // Reject eligibility too, so first-launch protection does not wait for this window.
        hook(MANAGER,"f","boolean",new String[]{PROPERTIES},args->{
            if(!blocked(args[0]))return Decision.pass();
            host.log("Automatic upgrade eligibility suppressed");return Decision.stop(false);
        });
        for(String action:new String[]{RUNNABLE,"th0.b"})for(boolean origin:new boolean[]{false,true}) {
            String[] types=origin?new String[]{"android.app.Activity",PROPERTIES,action,LISTENER,"java.lang.String"}
                :new String[]{"android.app.Activity",PROPERTIES,action,LISTENER};
            hook("com.dragon.read.pop.PopProxy","popup",RUNNABLE.equals(action)?"void":TICKET,types,this::popup);
        }
        hook("com.dragon.read.pop.PopProxy","enqueue","void",
            new String[]{"android.app.Activity",PROPERTIES,RUNNABLE,LISTENER,"java.lang.String"},this::popup);
        deoptimize(MANAGER,"a");
        deoptimize(MANAGER+"$a","b");
        deoptimize(MANAGER+"$c","a");
        deoptimize(MANAGER+"$c$a","subscribe");
        deoptimize(MANAGER+"$c$a$a","c");
    }
    private boolean blocked(Object properties) {
        if(!(properties instanceof Enum<?>))return false;
        Enum<?> pop=(Enum<?>)properties;
        return UpdatePopupRules.block(host.on("hide_update_popup"),HostSymbols.canonical(pop.getDeclaringClass().getName()),pop.name());
    }
    private Decision popup(Object[] args)throws Throwable {
        if(!blocked(args[1]))return Decision.pass();
        // The native popup rejection branch calls intercept; no ticket has been created yet.
        if(args[3]!=null)try{HostSymbols.type(LISTENER,loader).getMethod(HostSymbols.publicMethodName(HostSymbols.type(LISTENER,loader),"intercept")).invoke(args[3]);}
            catch(Throwable error){host.log("Automatic upgrade rejection listener failed: "+PrivacyRules.errorType(error));}
        host.log("Automatic upgrade popup request refused: "+((Enum<?>)args[1]).name());
        return Decision.stop(null);
    }
    private interface Before {Decision apply(Object[] args)throws Throwable;}
    private static final class Decision {
        final boolean stop;final Object result;
        private Decision(boolean stop,Object result){this.stop=stop;this.result=result;}
        static Decision pass(){return new Decision(false,null);}
        static Decision stop(Object result){return new Decision(true,result);}
    }
    private void hook(String className,String name,String returns,String[] types,Before before) {
        int installed=0;
        try {
            Class<?> type=HostSymbols.type(className,loader);
            for(Method method:type.getDeclaredMethods()) {
                if(!HostSymbols.matches(method,name)||!HostSymbols.name(returns).equals(method.getReturnType().getName())||method.getParameterCount()!=types.length)continue;
                boolean match=true;Class<?>[] args=method.getParameterTypes();
                for(int i=0;i<types.length;i++)match&=HostSymbols.name(types[i]).equals(args[i].getName());
                if(!match)continue;
                module.hook(method).intercept(chain->{
                    Object[] values=chain.getArgs().toArray();
                    try{Decision decision=before.apply(values);if(decision.stop)return decision.result;}
                    catch(Throwable error){host.log("Automatic upgrade adaptation failed: "+className+"."+name+" "+PrivacyRules.errorType(error));}
                    return chain.proceed(values);
                });installed++;
            }
        }catch(Throwable error){host.log("Automatic upgrade hook unavailable: "+className+"."+name+" "+PrivacyRules.errorType(error));}
        host.log("Automatic upgrade hooks registered: "+className+"."+name+" = "+installed);
    }
    private void deoptimize(String className,String name) {
        try{for(Method method:HostSymbols.type(className,loader).getDeclaredMethods())if(HostSymbols.matches(method,name))module.deoptimize(method);}
        catch(Throwable error){host.log("Automatic upgrade caller adaptation unavailable: "+className+" "+PrivacyRules.errorType(error));}
    }
}
