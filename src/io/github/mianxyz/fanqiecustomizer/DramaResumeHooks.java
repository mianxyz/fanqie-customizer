package io.github.mianxyz.fanqiecustomizer;

import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** Stop the automatic video-history pendant before attachment, player preparation or callbacks. */
final class DramaResumeHooks {
    private static final String MANAGER="pd6.e0";
    private static final String MODEL="com.dragon.read.pages.bookmall.model.RecentReadModel";
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    DramaResumeHooks(XposedModule module,FeatureHooks.Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}

    void install(){
        if(!host.on(DramaResumeRules.KEY))return;
        // false player mode is the automatic home history pendant; true is the explicit player.
        hook("b",new String[]{MODEL,"boolean","kotlin.jvm.functions.Function1","kotlin.jvm.functions.Function0"},args->
            DramaResumeRules.automatic(host.on(DramaResumeRules.KEY),video(args[0]),Boolean.TRUE.equals(args[1])));
        // Delayed lifecycle/cached requests can reach attachment without submitting a new request.
        hook("e",new String[]{"android.app.Activity",MODEL,"boolean"},args->
            DramaResumeRules.attach(host.on(DramaResumeRules.KEY),video(args[1]),Boolean.TRUE.equals(args[2]),args[0]==null?null:HostSymbols.canonical(args[0].getClass().getName())));
        deoptimize(MANAGER,"b");deoptimize(MANAGER,"i");
        deoptimize("pd6.f0","run");deoptimize("pd6.e0$c","onActivityResumed");
    }
    private boolean video(Object model)throws ReflectiveOperationException{
        return model!=null&&model.getClass().getField(HostSymbols.publicFieldName(model.getClass(),"isVideo")).getBoolean(model);
    }
    private interface Filter {boolean block(Object[] args)throws Throwable;}
    private void hook(String name,String[] parameters,Filter filter){
        int count=0;
        try{
            Class<?>[] args=new Class<?>[parameters.length];
            for(int i=0;i<args.length;i++)args[i]="boolean".equals(parameters[i])?boolean.class:HostSymbols.type(parameters[i],loader);
            Method method=HostSymbols.type(MANAGER,loader).getDeclaredMethod(HostSymbols.methodName(MANAGER,name),args);
            if(method.getReturnType()!=void.class)throw new NoSuchMethodException();
            module.hook(method).intercept(chain->{
                Object[] values=chain.getArgs().toArray();
                try{if(filter.block(values)){host.log("Drama automatic resume pendant suppressed: "+name);return null;}}
                catch(Throwable error){host.log("Drama resume filter failed: "+PrivacyRules.errorType(error));}
                return chain.proceed(values);
            });count++;
        }catch(Throwable error){host.log("Drama resume hook unavailable: "+name+" "+PrivacyRules.errorType(error));}
        host.log("Drama resume hooks registered: "+MANAGER+"."+name+" = "+count);
    }
    private void deoptimize(String name,String method){
        try{for(Method value:HostSymbols.type(name,loader).getDeclaredMethods())if(HostSymbols.matches(value,method))module.deoptimize(value);}
        catch(Throwable error){host.log("Drama resume caller unavailable: "+name+" "+PrivacyRules.errorType(error));}
    }
}
