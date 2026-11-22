package io.github.mianxyz.fanqiecustomizer;

import android.content.Context;
import android.content.ContextWrapper;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;

/** Prevent the native bottom banner from reserving space and translating reader content. */
final class ReaderBannerHooks {
    private static final String ASSISTANT="com.dragon.read.reader.extend.banner.a";
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;

    ReaderBannerHooks(XposedModule module,FeatureHooks.Host host,ClassLoader loader){
        this.module=module;this.host=host;this.loader=loader;
    }

    void install(){
        if(!host.on("hide_banner"))return;
        hook("com.dragon.read.component.biz.impl.NsAdImpl","getReaderBannerView","android.widget.FrameLayout",new String[]{
            "com.dragon.read.component.biz.interfaces.NsReaderActivity","com.dragon.reader.lib.ReaderClient"
        },(receiver,args)->reader(args[0])?Decision.stop(null,"Reader bottom banner creation suppressed"):Decision.pass());

        // d() creates the banner, sets config.J(height), and repaginates the text.
        hook(ASSISTANT,"d","void",new String[0],(receiver,args)->assistantReader(receiver)?
            Decision.stop(null,"Reader banner attachment and repagination suppressed"):Decision.pass());
        hook(ASSISTANT,"c","boolean",new String[0],(receiver,args)->assistantReader(receiver)?
            Decision.stop(false,"Reader banner switch kept closed"):Decision.pass());
        // o() starts the animation; n() sends negative text offsets to its listeners.
        hook(ASSISTANT,"o","void",new String[]{"long","boolean"},(receiver,args)->assistantReader(receiver)?
            Decision.stop(null,"Reader banner animation suppressed"):Decision.pass());
        hook(ASSISTANT,"n","void",new String[]{"float"},(receiver,args)->assistantReader(receiver)?
            Decision.stop(null,"Reader banner text translation suppressed"):Decision.pass());

        // H()/J(int) read/write an in-memory height used when pages are measured.
        // Scope to the native ReaderActivity, leaving other reader clients untouched.
        hook("uo7.z0","H","int",new String[0],(receiver,args)->configReader(receiver)?
            Decision.stop(0,"Reader banner reserved height kept at zero"):Decision.pass());
        hook("uo7.z0","J","void",new String[]{"int"},(receiver,args)->{
            if(configReader(receiver)){args[0]=0;host.log("Reader banner reserved height write cleared");}
            return Decision.pass();
        });

        // These callers can contain inlined reads of the short height/switch getters.
        deoptimize("com.dragon.read.reader.ui.ReaderActivity$g","onReceive");
        deoptimize(ASSISTANT,"d","f");
        deoptimize("ul6.a","d","f");
    }

    private static final class Decision {
        final boolean stop;final Object value;final String message;
        Decision(boolean stop,Object value,String message){this.stop=stop;this.value=value;this.message=message;}
        static Decision pass(){return new Decision(false,null,null);}
        static Decision stop(Object value,String message){return new Decision(true,value,message);}
    }
    private interface Before {Decision apply(Object receiver,Object[] args)throws Throwable;}
    private void hook(String className,String methodName,String resultType,String[] parameterTypes,Before before){
        int count=0;
        try{
            Class<?> type=HostSymbols.type(className,loader);
            for(Method method:type.getDeclaredMethods()){
                if(!HostSymbols.matches(method,methodName)||!method.getReturnType().getName().equals(HostSymbols.name(resultType)))continue;
                Class<?>[] parameters=method.getParameterTypes();if(parameters.length!=parameterTypes.length)continue;
                boolean matches=true;for(int i=0;i<parameters.length;i++)matches&=parameters[i].getName().equals(HostSymbols.name(parameterTypes[i]));
                if(!matches)continue;
                module.hook(method).intercept(chain->{
                    Object[] args=chain.getArgs().toArray();
                    try{Decision decision=before.apply(chain.getThisObject(),args);if(decision.stop){host.log(decision.message);return decision.value;}}
                    catch(Throwable error){host.log("Reader banner adaptation failed: "+className+"."+methodName+" "+error.getClass().getSimpleName());}
                    return chain.proceed(args);
                });count++;
            }
        }catch(Throwable error){host.log("Reader banner hook unavailable: "+className+"."+methodName+" "+error.getClass().getSimpleName());}
        host.log("Reader banner hooks registered: "+className+"."+methodName+" = "+count);
    }
    private void deoptimize(String className,String... names){
        try{for(Method method:HostSymbols.type(className,loader).getDeclaredMethods())for(String name:names)
            if(HostSymbols.matches(method,name)&&!Modifier.isAbstract(method.getModifiers())&&!Modifier.isNative(method.getModifiers()))module.deoptimize(method);
        }catch(Throwable error){host.log("Reader banner caller adaptation unavailable: "+className+" "+error.getClass().getSimpleName());}
    }
    private static boolean assistantReader(Object receiver)throws Throwable{return reader(field(receiver,"c"));}
    private static boolean configReader(Object receiver)throws Throwable{
        Object owner=field(receiver,"a");
        for(int i=0;i<12&&owner instanceof Context;i++){
            if(reader(owner))return true;
            if(!(owner instanceof ContextWrapper))return false;
            Context next=((ContextWrapper)owner).getBaseContext();if(next==owner)return false;owner=next;
        }
        return false;
    }
    private static boolean reader(Object owner){return owner!=null&&BookRules.isReaderPage(HostSymbols.canonical(owner.getClass().getName()));}
    private static Object field(Object receiver,String name)throws Throwable{Field field=receiver.getClass().getField(HostSymbols.publicFieldName(receiver.getClass(),name));field.setAccessible(true);return field.get(receiver);}
}
