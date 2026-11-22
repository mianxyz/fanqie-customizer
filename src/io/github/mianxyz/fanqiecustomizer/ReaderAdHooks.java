package io.github.mianxyz.fanqiecustomizer;

import android.app.Dialog;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Native reader ad insertion and popup queue boundaries for 7.3.9.32. */
final class ReaderAdHooks {
    private static final String AD="com.dragon.read.component.biz.impl.NsAdImpl";
    private static final String CLIENT="com.dragon.reader.lib.ReaderClient";
    private static final String TICKET="com.dragon.read.pop.IPopProxy$IPopTicket";
    private static final String DYNAMIC="com.dragon.read.ad.dynamicrequest.facade.ReadFlowAdDynamicInsertFacade";
    private static final String AD_PAGE="com.dragon.read.ad.brand.model.ReaderAdPageData";
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    private final Set<Method> livePushMethods=ConcurrentHashMap.newKeySet();
    private final Map<Dialog,WeakReference<Object>> liveDialogOwners=Collections.synchronizedMap(new WeakHashMap<>());
    ReaderAdHooks(XposedModule module,FeatureHooks.Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}

    void install(){
        if(host.on("debug"))diagnostics();
        if(!host.on("hide_reader_ads"))return;
        dynamicPages();
        adWindows();
        livePromotions();
        // This interceptor inserts cached or newly-created ad pages before/after text.
        for(String name:new String[]{"b","c"})hook("com.dragon.read.reader.depend.interceptors.b",name,"boolean",new String[]{"qo7.b"},
            (receiver,args)->reader(field(receiver,"f"))?Decision.stop(false,"Reader full-page ad interceptor bypassed: "+name):Decision.pass());
        hook("com.dragon.read.reader.ad.readflow.ReadFlowVerticalMiniAdHelper","a",
            "com.dragon.read.ad.onestop.readflow.ReadFlowOneStopMiniAdLine",new String[]{"com.dragon.read.reader.ad.model.AtRequestArgs",CLIENT},
            (receiver,args)->readerClient(args[1])?Decision.stop(null,"Reader mini ad line omitted"):Decision.pass());
        hook(AD,"tryAddBrandSplashOriginAdForReader","void",new String[]{"java.lang.String",CLIENT,"android.app.Activity"},
            (receiver,args)->reader(args[2])||readerClient(args[1])?Decision.stop(null,"Reader brand full-page ad insertion suppressed"):Decision.pass());
        hook("com.dragon.read.component.NsUtilsDependImpl","canShowScreenAd","boolean",new String[]{"java.lang.Object"},
            (receiver,args)->reader(args[0])||host.readerVisible()?Decision.stop(false,"Reader automatic screen ad refused"):Decision.pass());
        hook("mb3.f","d","void",new String[]{"com.dragon.read.base.AbsFragment"},
            (receiver,args)->reader(field(receiver,"a"))||host.readerVisible()?Decision.stop(null,"Reader automatic screen ad scheduling suppressed"):Decision.pass());

        popupRequests();
        hook("com.dragon.read.pop.PopProxy","enqueue","void",new String[]{"android.app.Activity","com.dragon.read.pop.IProperties",
            "com.dragon.read.pop.IPopProxy$IRunnable","com.dragon.read.pop.IPopProxy$IListener","java.lang.String"},this::popupDecision);
        // If a known ad was queued before the reader opened, finish only its popup ticket.
        queued("mb3.c",receiver->reader(field(field(receiver,"a"),"a"))||host.readerVisible(),false);
        queued("kh6.b$a",receiver->host.readerVisible(),true);
        queued("com.dragon.read.reader.ad.noad.InspireNoAdsDialog$Companion$a",receiver->reader(field(receiver,"a"))||host.readerVisible(),false);
        deoptimize(AD,"chapterChanged");
        deoptimize("kh6.b","a");
    }

    private boolean blockDynamic(Object owner){
        return ReaderAdRules.blockDynamicPage(host.on("hide_reader_ads"),reader(owner)?"com.dragon.read.reader.ui.ReaderActivity":null);
    }
    private void dynamicPages(){
        for(String name:new String[]{"b","c"})hook("com.dragon.read.reader.depend.interceptors.c",name,"boolean",new String[]{"qo7.b"},
            (receiver,args)->blockDynamic(field(receiver,"d"))?Decision.stop(false,"Reader alternate ad page interceptor bypassed: "+name):Decision.pass());
        hook(DYNAMIC,"d","void",new String[]{CLIENT,"boolean"},(receiver,args)->{
            Object context=args[0]==null?null:args[0].getClass().getMethod(HostSymbols.publicMethodName(args[0].getClass(),"getContext")).invoke(args[0]);
            if(!blockDynamic(context))return Decision.pass();
            // The native no-ad branch clears these same pending ad-only fields.
            try{
                Class<?> type=HostSymbols.type(DYNAMIC,loader);
                for(String name:new String[]{"b","c","d"})type.getField(HostSymbols.publicFieldName(type,name)).set(null,null);
                type.getField(HostSymbols.publicFieldName(type,"i")).setInt(null,0);
                host.log("Reader pending dynamic ad cache cleared");
            }catch(Throwable error){host.log("Reader dynamic ad cache cleanup failed: "+error.getClass().getSimpleName());}
            return Decision.stop(null,"Reader asynchronous ad page insertion suppressed");
        });
        hook("com.dragon.read.reader.ad.ReaderAdManager","canLoadAd","boolean",new String[]{"java.lang.String"},
            (receiver,args)->host.on("hide_reader_ads")&&host.readerVisible()?Decision.stop(false,"Reader ad acquisition refused"):Decision.pass());
        deoptimize("com.dragon.read.reader.ad.ReaderAdManager","l");
    }

    private boolean blockLive(Object owner){
        return ReaderAdRules.blockLivePromotion(host.on("hide_reader_ads"),
            reader(owner)?"com.dragon.read.reader.ui.ReaderActivity":null,host.readerVisible());
    }
    private void adWindows(){
        String fragmentName="com.bytedance.ad.live.component.sif.sif.SifDialogFragment";
        try{
            Class<?> type=HostSymbols.type(fragmentName,loader);
            module.hook(type.getDeclaredMethod(HostSymbols.methodName(type,"onCreateDialog"),android.os.Bundle.class)).intercept(chain->{
                Object result=chain.proceed();
                if(result instanceof Dialog)liveDialogOwners.put((Dialog)result,new WeakReference<>(chain.getThisObject()));
                return result;
            });
            host.log("Reader live ad dialog owner observer registered");
        }catch(Throwable error){host.log("Reader live ad dialog owner observer unavailable: "+error.getClass().getSimpleName());}
        hook("lh.a","show","void",new String[]{},(receiver,args)->{
            Dialog dialog=(Dialog)receiver;
            if(!blockWindow(dialog,"lh.a"))return Decision.pass();
            WeakReference<Object> reference=liveDialogOwners.remove(dialog);
            Object fragment=reference==null?null:reference.get();
            if(fragment!=null)new Handler(Looper.getMainLooper()).post(()->{
                try{fragment.getClass().getMethod(HostSymbols.publicMethodName(fragment.getClass(),"dismissAllowingStateLoss")).invoke(fragment);}
                catch(Throwable error){host.log("Reader live ad fragment cleanup failed: "+error.getClass().getSimpleName());}
            });
            return Decision.stop(null,"Reader live ad preview window refused");
        });
        hook("com.dragon.read.widget.dialog.AbsQueueDialog","show","void",new String[]{},(receiver,args)->{
            boolean advertising=false;
            for(Class<?> type=receiver.getClass();type!=null;type=type.getSuperclass())
                if(HostSymbols.name("mb3.b").equals(type.getName())){advertising=true;break;}
            Dialog dialog=(Dialog)receiver;
            if(!advertising||!blockWindow(dialog,"mb3.b"))return Decision.pass();
            // Its native dismiss path cleans timers, mutex windows and the popup ticket.
            try{dialog.dismiss();}
            catch(Throwable error){
                host.log("Reader ad window cleanup failed: "+error.getClass().getSimpleName());
                try{Object ticket=field(receiver,"popTicket");if(ticket!=null)call(TICKET,ticket,"onFinish");}
                catch(Throwable ticketError){host.log("Reader ad window ticket cleanup failed: "+ticketError.getClass().getSimpleName());}
            }
            return Decision.stop(null,"Reader screen ad window refused: "+HostSymbols.canonical(receiver.getClass().getName()));
        });
        deoptimize("com.dragon.read.component.NsUtilsDependImpl","showAdDialog");
    }
    private boolean blockWindow(Dialog dialog,String adClass){
        Object owner=dialog.getOwnerActivity();
        if(!reader(owner))owner=dialog.getContext();
        return ReaderAdRules.blockAdWindow(host.on("hide_reader_ads"),
            reader(owner)?"com.dragon.read.reader.ui.ReaderActivity":null,host.readerVisible(),adClass);
    }
    private void livePromotions(){
        // These routes belong to ads, including the OneStop live ad service.
        hook(AD,"enterAdLiveRoom","void",new String[]{"android.content.Context","org.json.JSONObject","java.lang.String","java.lang.String"},
            (receiver,args)->blockLive(args[0])?Decision.stop(null,"Reader live ad route refused: NsAdImpl"):Decision.pass());
        hook("com.dragon.read.ad.tomato.common.impl.LiveAdImpl","enterLiveRoom","void",new String[]{"android.app.Activity","js1.b"},
            (receiver,args)->blockLive(args[0])?Decision.stop(null,"Reader live ad route refused: OneStop"):Decision.pass());
        hook("com.dragon.read.ad.util.AdUtil","b","void",new String[]{"lc3.a"},
            (receiver,args)->args[0]!=null&&blockLive(field(args[0],"a"))
                ?Decision.stop(null,"Reader live ad launch refused: AdUtil"):Decision.pass());
        // The live implementation can arrive later from a separate plugin loader.
        try{livePush(HostSymbols.type("com.dragon.read.plugin.common.safeproxy.LivePluginProxy",loader));}
        catch(Throwable error){host.log("Reader live push proxy unavailable: "+error.getClass().getSimpleName());}
        try{
            Class<?> manager=HostSymbols.type("com.dragon.read.plugin.common.PluginServiceManager",loader);
            module.hook(manager.getDeclaredMethod(HostSymbols.methodName(manager,"getLivePlugin"))).intercept(chain->{
                Object plugin=chain.proceed();
                try{
                    if(plugin!=null){
                        livePush(plugin.getClass());
                        if(HostSymbols.canonical(plugin.getClass().getName()).equals("com.dragon.read.plugin.common.safeproxy.LivePluginProxy")){
                            Object real=plugin.getClass().getMethod(HostSymbols.publicMethodName(plugin.getClass(),"getReal")).invoke(plugin);
                            if(real!=null)livePush(real.getClass());
                        }
                    }
                }catch(Throwable error){host.log("Reader live plugin adaptation failed: "+error.getClass().getSimpleName());}
                return plugin;
            });
            host.log("Reader live plugin observer registered");
        }catch(Throwable error){host.log("Reader live plugin observer unavailable: "+error.getClass().getSimpleName());}
        deoptimize(AD,"enterAdLiveRoom");
        deoptimize("com.dragon.read.ad.tomato.common.impl.LiveAdImpl","enterLiveRoom");
        deoptimize("com.dragon.read.ad.exciting.video.BdpLiveAdServiceImpl","realOpenLive");
    }
    private void livePush(Class<?> type)throws Throwable{
        Method method=type.getMethod(HostSymbols.publicMethodName(type,"showPush",android.app.Activity.class,HostSymbols.type("kotlin.jvm.functions.Function1",loader)),android.app.Activity.class,HostSymbols.type("kotlin.jvm.functions.Function1",loader));
        if(method.getReturnType()!=void.class||Modifier.isAbstract(method.getModifiers())||!livePushMethods.add(method))return;
        try{
            module.hook(method).intercept(chain->{
                if(blockLive(chain.getArg(0))){
                    // Match the safe proxy's absent-plugin branch; do not invent callback results.
                    host.log("Reader live promotion refused: "+method.getDeclaringClass().getName());
                    return null;
                }
                return chain.proceed();
            });
            host.log("Reader live promotion hook registered: "+method.getDeclaringClass().getName());
        }catch(Throwable error){livePushMethods.remove(method);throw error;}
    }

    private void popupRequests(){
        String[] actions={"com.dragon.read.pop.IPopProxy$IRunnable","th0.b"};
        for(String action:actions)for(boolean withOrigin:new boolean[]{false,true}){
            String result=action.equals("th0.b")?TICKET:"void";
            String[] parameters=withOrigin?new String[]{"android.app.Activity","com.dragon.read.pop.IProperties",action,"com.dragon.read.pop.IPopProxy$IListener","java.lang.String"}
                :new String[]{"android.app.Activity","com.dragon.read.pop.IProperties",action,"com.dragon.read.pop.IPopProxy$IListener"};
            hook("com.dragon.read.pop.PopProxy","popup",result,parameters,this::popupDecision);
        }
    }
    private Decision popupDecision(Object receiver,Object[] args){
        String name=args[1] instanceof Enum<?>?((Enum<?>)args[1]).name():null;
        String owner=args[0]==null?null:HostSymbols.canonical(args[0].getClass().getName());
        if(!ReaderAdRules.blockPopup(host.on("hide_reader_ads"),owner,host.readerVisible(),name))return Decision.pass();
        // Match PopProxy's own rejection branch, including its optional listener.
        if(args[3]!=null)try{call("com.dragon.read.pop.IPopProxy$IListener",args[3],"intercept");}
            catch(Throwable error){host.log("Reader ad rejection listener: "+error.getClass().getSimpleName());}
        return Decision.stop(null,"Reader ad popup request refused: "+name);
    }
    private interface Scope {boolean reading(Object receiver)throws Throwable;}
    private void queued(String className,Scope scope,boolean clearDelayedData){
        hook(className,"run","void",new String[]{TICKET},(receiver,args)->{
            if(!scope.reading(receiver)||args[0]==null)return Decision.pass();
            if(clearDelayedData)try{
                Class<?> api=HostSymbols.type("com.dragon.read.NsUtilsDepend",loader);
                api.getMethod(HostSymbols.publicMethodName(api,"clearDelayHomepagePopData")).invoke(api.getField(HostSymbols.publicFieldName(api,"IMPL")).get(null));
            }catch(Throwable error){host.log("Reader delayed ad cleanup: "+error.getClass().getSimpleName());}
            call(TICKET,args[0],"onFinish");
            return Decision.stop(null,"Reader queued ad cancelled: "+className);
        });
    }
    private void call(String interfaceName,Object receiver,String methodName)throws Throwable{
        Method method=HostSymbols.type(interfaceName,loader).getMethod(HostSymbols.publicMethodName(HostSymbols.type(interfaceName,loader),methodName));method.setAccessible(true);method.invoke(receiver);
    }
    private void diagnostics(){
        try{
            module.hook(Dialog.class.getDeclaredMethod("show")).intercept(chain->{
                Object result=chain.proceed();Object dialog=chain.getThisObject();
                if(host.readerVisible()&&dialog!=null)host.log("Reader dialog shown: "+HostSymbols.canonical(dialog.getClass().getName()));
                return result;
            });
        }catch(Throwable error){host.log("Reader dialog diagnostics unavailable: "+error.getClass().getSimpleName());}
        if(!HostSymbols.isBaseline()&&!HostSymbols.hasMethodMapping("com.dragon.reader.lib.parserlevel.model.page.a","Z","(Ljava/lang/String;)V"))return;
        try{
            Class<?> pageType=HostSymbols.type(AD_PAGE,loader);
            for(Constructor<?> constructor:pageType.getDeclaredConstructors())module.hook(constructor).intercept(chain->{
                Object result=chain.proceed();
                try{
                    Object line=chain.getArg(1);
                    if(readerClient(chain.getArg(0))&&line!=null)host.log("Reader ad page built: "+HostSymbols.canonical(line.getClass().getName()));
                }catch(Throwable error){host.log("Reader ad page diagnostics failed: "+error.getClass().getSimpleName());}
                return result;
            });
            Class<?> basePage=HostSymbols.type("com.dragon.reader.lib.parserlevel.model.page.a",loader);
            module.hook(basePage.getDeclaredMethod(HostSymbols.methodName(basePage,"Z"),String.class)).intercept(chain->{
                Object result=chain.proceed();
                try{
                    if(ReaderAdRules.adExitTitle((String)chain.getArg(0))){
                        Object page=chain.getThisObject();
                        String lineName="unknown";
                        if(pageType.isInstance(page)){Object line=field(page,"f");if(line!=null)lineName=HostSymbols.canonical(line.getClass().getName());}
                        host.log("Reader ad exit title assigned: page="+HostSymbols.canonical(page.getClass().getName())+"; line="+lineName);
                    }
                }catch(Throwable error){host.log("Reader ad title diagnostics failed: "+error.getClass().getSimpleName());}
                return result;
            });
            deoptimize("com.dragon.read.reader.depend.interceptors.b","h");
            host.log("Reader ad page and title diagnostics registered");
        }catch(Throwable error){host.log("Reader ad page diagnostics unavailable: "+error.getClass().getSimpleName());}
    }
    private static final class Decision {
        final boolean stop;final Object result;final String message;
        Decision(boolean stop,Object result,String message){this.stop=stop;this.result=result;this.message=message;}
        static Decision pass(){return new Decision(false,null,null);}
        static Decision stop(Object result,String message){return new Decision(true,result,message);}
    }
    private interface Before {Decision apply(Object receiver,Object[] args)throws Throwable;}
    private void hook(String className,String methodName,String returnType,String[] parameterTypes,Before before){
        int count=0;
        try{for(Method method:HostSymbols.type(className,loader).getDeclaredMethods()){
            if(!HostSymbols.matches(method,methodName)||!method.getReturnType().getName().equals(HostSymbols.name(returnType)))continue;
            Class<?>[] types=method.getParameterTypes();if(types.length!=parameterTypes.length)continue;
            boolean match=true;for(int i=0;i<types.length;i++)match&=types[i].getName().equals(HostSymbols.name(parameterTypes[i]));if(!match)continue;
            module.hook(method).intercept(chain->{
                Object[] args=chain.getArgs().toArray();
                try{Decision decision=before.apply(chain.getThisObject(),args);if(decision.stop){host.log(decision.message);return decision.result;}}
                catch(Throwable error){host.log("Reader ad adaptation failed: "+className+"."+methodName+" "+error.getClass().getSimpleName());}
                return chain.proceed(args);
            });count++;
        }}catch(Throwable error){host.log("Reader ad hook unavailable: "+className+"."+methodName+" "+error.getClass().getSimpleName());}
        host.log("Reader ad hooks registered: "+className+"."+methodName+"("+String.join(",",parameterTypes)+") = "+count);
    }
    private void deoptimize(String className,String name){
        try{for(Method method:HostSymbols.type(className,loader).getDeclaredMethods())if(HostSymbols.matches(method,name))module.deoptimize(method);}
        catch(Throwable error){host.log("Reader ad caller adaptation unavailable: "+className+" "+error.getClass().getSimpleName());}
    }
    private boolean readerClient(Object client)throws Throwable{return client!=null&&reader(client.getClass().getMethod(HostSymbols.publicMethodName(client.getClass(),"getContext")).invoke(client));}
    private static boolean reader(Object owner){
        if(owner==null)return false;
        for(int i=0;i<12&&owner!=null;i++){
            if(BookRules.isReaderPage(HostSymbols.canonical(owner.getClass().getName())))return true;
            if(!(owner instanceof ContextWrapper))return false;
            Context next=((ContextWrapper)owner).getBaseContext();if(next==owner)return false;owner=next;
        }
        return false;
    }
    private static Object field(Object receiver,String name)throws Throwable{Field field=receiver.getClass().getField(HostSymbols.publicFieldName(receiver.getClass(),name));field.setAccessible(true);return field.get(receiver);}
}
