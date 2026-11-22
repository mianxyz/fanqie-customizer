package io.github.mianxyz.fanqiecustomizer;

import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Reader-only series promotion boundaries established from the shipped APKs. */
final class ReaderDramaPromotionHooks {
    private static final String PREFIX="com.dragon.read.component.shortvideo.impl.reader.depend.";
    private static final String SOURCE="com.dragon.read.GenreCardRequestSource";
    private static final String PENDANT="com.dragon.base.ssconfig.template.ReaderSeriesConsumedShowPendantViewConfig$a";
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    private final Set<Method> gates=ConcurrentHashMap.newKeySet();
    private final Set<String> events=ConcurrentHashMap.newKeySet();
    private void once(String text){if(events.add(text))host.log(text);}

    ReaderDramaPromotionHooks(XposedModule module,FeatureHooks.Host host,ClassLoader loader){
        this.module=module;this.host=host;this.loader=loader;
    }
    static boolean promotionFactory(String name){
        return (PREFIX+"ShortSeriesParagraphInsertDependProvider").equals(name)
            ||(PREFIX+"ShortSeriesReadGenreCardDependProvider").equals(name);
    }
    static boolean requestSignature(String name,String result,String... parameters){
        return "e".equals(name)&&"boolean".equals(result)&&parameters!=null&&parameters.length==3
            &&"java.lang.String".equals(parameters[0])&&SOURCE.equals(parameters[1])&&"boolean".equals(parameters[2]);
    }
    static boolean preLayoutSignature(String result,String... parameters){return result!=null&&!"void".equals(result)&&!"boolean".equals(result)&&parameters!=null&&(parameters.length==1||parameters.length==2)&&"com.dragon.read.rpc.model.CellViewData".equals(parameters[0])&&(parameters.length==1||"com.dragon.reader.lib.ReaderClient".equals(parameters[1]));}
    static boolean blockRequest(boolean master,boolean enabled,boolean trustedFactory){return master&&enabled&&trustedFactory;}
    static boolean blockPendant(boolean master,boolean enabled,boolean readerVisible){return master&&enabled&&readerVisible;}

    void install(){
        for(String simple:new String[]{"ShortSeriesParagraphInsertDependProvider","ShortSeriesReadGenreCardDependProvider"}){
            String name=PREFIX+simple;
            try{
                Class<?> type=Class.forName(name,false,loader);
                Method factory=type.getDeclaredMethod("createDepend");
                if(factory.getReturnType().isPrimitive()||Modifier.isStatic(factory.getModifiers()))continue;
                module.hook(factory).intercept(chain->{
                    Object result=chain.proceed();
                    if(result!=null)try{bindRequest(name,result.getClass());}
                    catch(Throwable error){host.log("Reader drama request adaptation failed: "+error.getClass().getSimpleName());}
                    return result;
                });
                host.log("Reader drama factory observer registered: "+name);
            }catch(Throwable error){host.log("Reader drama factory unavailable: "+name+" "+error.getClass().getSimpleName());}
        }
        try{
            Method getter=Class.forName(PENDANT,false,loader).getDeclaredMethod("c");
            if(getter.getReturnType()!=boolean.class||!Modifier.isStatic(getter.getModifiers()))throw new NoSuchMethodException();
            module.hook(getter).intercept(chain->{
                if(blockPendant(host.on("enabled"),host.on("hide_banner"),host.readerVisible())){
                    once("Reader series single-ad pendant eligibility suppressed");
                    return false;
                }
                return chain.proceed();
            });
            host.log("Reader series single-ad pendant hook registered");
            deoptimize("com.dragon.read.component.shortvideo.impl.reader.view.AutoPlaySeriesLine","o1");
            deoptimize("com.dragon.read.component.shortvideo.impl.reader.view.i0","invoke");
            deoptimize("com.dragon.read.pendant.other.ExperimentConsumeRecordImpl","k");
            deoptimize("com.dragon.read.pendant.other.OnlineConsumeRecordImpl","k");
        }catch(Throwable error){host.log("Reader series pendant hook unavailable: "+error.getClass().getSimpleName());}
        deoptimize("com.dragon.read.component.biz.impl.request.GenreCardRequestManager","c");
    }
    private void bindRequest(String factoryName,Class<?> type)throws Throwable{
        if(!promotionFactory(factoryName))return;
        for(Method candidate:type.getDeclaredMethods()){
            Class<?>[] parameters=candidate.getParameterTypes();
            if(preLayoutSignature(candidate.getReturnType().getName(),java.util.Arrays.stream(parameters).map(Class::getName).toArray(String[]::new))&&!candidate.getReturnType().isPrimitive()&&!Modifier.isStatic(candidate.getModifiers())&&gates.add(candidate)){
                module.hook(candidate).intercept(chain->{
                    if(blockRequest(host.on("enabled"),host.on("hide_reader_ads"),promotionFactory(factoryName))){once("Reader drama pre-layout model omitted: "+type.getName());return null;}
                    return chain.proceed();
                });
                host.log("Reader drama pre-layout hook registered: "+type.getName()+"."+candidate.getName());
            }
        }
        Method gate=type.getDeclaredMethod("e",String.class,Class.forName(SOURCE,false,type.getClassLoader()),boolean.class);
        Class<?>[] args=gate.getParameterTypes();
        if(!requestSignature(gate.getName(),gate.getReturnType().getName(),args[0].getName(),args[1].getName(),args[2].getName())
            ||Modifier.isStatic(gate.getModifiers())||Modifier.isAbstract(gate.getModifiers())||!gates.add(gate))return;
        try{
            module.hook(gate).intercept(chain->{
                if(blockRequest(host.on("enabled"),host.on("hide_reader_ads"),promotionFactory(factoryName))){
                    once("Reader drama promotion request suppressed: "+type.getName());
                    return false;
                }
                return chain.proceed();
            });
            host.log("Reader drama request hook registered: "+type.getName()+".e");
        }catch(Throwable error){gates.remove(gate);throw error;}
    }
    private void deoptimize(String name,String method){
        try{for(Method value:Class.forName(name,false,loader).getDeclaredMethods())
            if(method.equals(value.getName())&&!Modifier.isAbstract(value.getModifiers())&&!Modifier.isNative(value.getModifiers()))module.deoptimize(value);
        }catch(Throwable error){host.log("Reader drama caller adaptation unavailable: "+name+" "+error.getClass().getSimpleName());}
    }
}
