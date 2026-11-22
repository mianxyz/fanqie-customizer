package io.github.mianxyz.fanqiecustomizer;

import android.view.View;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.*;

/** Native 7.3.9.32 paths confirmed by the supplied LSPosed log and APK. */
final class NativeUiHooks {
    interface Host extends FeatureHooks.Host { void mineRoot(View root); default void pageShown(String name){} }
    interface Before { boolean apply(Object receiver,Object[] args)throws Throwable; }
    interface After { void apply(Object receiver,Object result)throws Throwable; }
    private final XposedModule module;
    private final Host host;
    private final ClassLoader loader;
    private final Map<Object,Boolean> changedDefault=Collections.synchronizedMap(new WeakHashMap<>());
    private final Map<Integer,String> rpcKinds=new HashMap<>();
    NativeUiHooks(XposedModule module,Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}

    void install(){
        if(host.on("hide_search_bar"))new DramaSearchVisibility(module,host).install();
        if(any(Rules.MINE_KEYS)||any(TabletRules.KEYS)||host.on("hide_income")){
            // The dispatcher also receives local defaults and cached plans, bypassing serializers.
            hook("sd4.c","g",(receiver,args)->{
                if(args.length!=1||args[0]==null)return false;
                Object card=args[0];String kind=enumName(get(card,"a"));
                if(hiddenCard(kind)){host.log("Native card removed: "+kind);return true;}
                args[0]=filterCard(card,0);return false;
            },null);
            // Both old and inlined converters consume a card containing the menu list.
            hook("sd4.h","b",(receiver,args)->{if(args.length==4&&args[0]!=null)args[0]=filterCard(args[0],0);return false;},null);
            // This converter is used when an already-created native card refreshes its menu.
            if(HostSymbols.isBaseline()||!HostSymbols.methodName("sd4.h","a").contains("__missing_host_symbol__"))hook("sd4.h","a",(receiver,args)->{
                for(Object arg:args)if(arg instanceof Enum<?> && HostSymbols.canonical(arg.getClass().getName()).equals("com.dragon.read.component.biz.api.model.CardType")){
                    String kind=enumName(arg);if(hiddenCard(kind)){host.log("Native menu removed: "+kind);return true;}
                }
                return false;
            },null);
            if(HostSymbols.isBaseline())deoptimize("com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2$g","accept");
        }
        if(any(Rules.MINE_KEYS)||any(TabletRules.KEYS))hook("be4.e","a",null,(receiver,result)->{if(result!=null)filterSidebarMenu(result);});
        if(any(Rules.MINE_KEYS)||host.on("hide_income")||host.on("hide_frequent_games")){
            hook("com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2","onCreateContent",null,(receiver,result)->{
                if(result instanceof View)host.mineRoot((View)result);
            });
        }
        { // Register the channel UI boundary once; live business flags are checked per call.
            // Copy at the final UI boundary so hidden channels are not written into the app cache.
            hook("com.dragon.read.component.biz.impl.NewBookMallFragment","Jh",(receiver,args)->{
                if(args.length!=1||args[0]==null)return false;
                Object source=args[0];int oldSelection=((Number)get(source,"selectIndex")).intValue();
                List<?> oldTabs=(List<?>)get(source,"bookMallTabDataList");
                Object selected=oldSelection>=0&&oldSelection<oldTabs.size()?oldTabs.get(oldSelection):null;
                Object filtered=filterBookmall(source);args[0]=filtered;
                List<?> next=(List<?>)get(filtered,"bookMallTabDataList");
                changedDefault.put(receiver,filtered!=source&&!next.contains(selected));
                return false;
            },null);
            // When the default channel was removed, request the replacement's own feed.
            hook("y14.b","a",(receiver,args)->{
                if(args.length==6&&Boolean.TRUE.equals(changedDefault.get(args[0]))&&Boolean.TRUE.equals(args[3])){
                    args[3]=false;host.log("Bookmall replacement channel loads its own feed");
                }
                return false;
            },null);
        }
        if(host.on("hide_income")){
            for(String name:new String[]{"com.dragon.read.component.biz.impl.mine.card.model.b1","com.dragon.read.component.biz.impl.mine.card.model.PolarisCardWrapper"})try{
                Class<?> type=HostSymbols.type(name,loader);Method getter=HostSymbols.methodReturning(type,"b",View.class);
                module.hook(getter).intercept(chain->{Object result=chain.proceed();if(result instanceof View)host.forceHide((View)result,"native income card");return result;});
                host.log("Income getter registered: "+type.getName()+"."+getter.getName()+"()View");
            }catch(Throwable error){host.log("Income getter error: "+name+" "+rootError(error));}
        }
        if(host.on("hide_frequent_games")){
            hook("com.dragon.read.kmp.mine.polaris.l2","d",(receiver,args)->{
                if(args.length==1&&args[0]!=null)args[0]=filterGameEntrances(args[0]);return false;
            },null);
            // Use the composable's existing empty-list branch; it closes its own Composer groups.
            hook("dz5.x","c",(receiver,args)->{
                if(args.length==4&&args[0] instanceof List<?>){args[0]=Collections.emptyList();host.log("Sidebar frequent games area suppressed");}return false;
            },null);
            hook("be4.e","b",null,(receiver,result)->{if(result!=null)clearList(result,"b");});
        }
        if(host.on("mine_message")){
            hook("dz5.x","h",(receiver,args)->{if(args.length==3&&args[0] instanceof List<?>)args[0]=Collections.emptyList();return false;},null);
        }
        if(host.on("hide_welfare_float")){
            block("com.bytedance.ug.sdk.kmp.novel.pendant.manager.PendantController","t");
            // This method installs both LuckyDog and the newer Lucky scene pendant on Main.
            block("com.dragon.read.pages.main.MainFragmentActivity","I2");
            block("com.bytedance.ug.sdk.luckydog.api.LuckyDogSDK","showPendantInFrameLayout");
            block("com.bytedance.ug.sdk.luckydog.api.LuckyDogSDK","showPendant");
        }
        if(host.on("raw_read_counts")){
            // Native detail responses may be decoded by Gson/protobuf rather than Kotlin serializers.
            hook("com.dragon.read.pages.detail.BookDetailModel","d",null,(receiver,result)->{
                if(result!=null)recordBoundCount(get(result,"bookInfo"));
            });
        }
        if(host.on("debug")||host.on("glass_global_enabled")||host.on("auto_expand_shelf")){
            for(String name:new String[]{"com.dragon.read.component.biz.impl.NewBookMallFragment","com.dragon.read.component.biz.impl.mine.FanqieMineFragmentV2","com.dragon.read.component.biz.impl.mine.functionpage.FanqieMineFunctionFragment"})
                hook(name,"onVisible",null,(receiver,result)->host.pageShown(name));
        }
    }

    interface CountSink { void record(Object info)throws Throwable; }
    CountSink countSink;
    private void recordBoundCount(Object info)throws Throwable {if(info!=null&&countSink!=null){countSink.record(info);host.log("Native detail reading fields captured");}}
    private boolean any(String[] keys){for(String key:keys)if(host.on(key))return true;return false;}
    private String menuKey(String title,String kind){String key=Rules.mineKey(title,kind);return key!=null?key:TabletRules.key(host.tabletLayout(),title,kind);}
    private boolean hiddenCard(String kind){String key=menuKey(null,kind);return key!=null&&host.on(key);}
    private static String enumName(Object value){return value instanceof Enum<?>?((Enum<?>)value).name():null;}

    private void block(String name,String method){
        hook(name,method,(receiver,args)->{host.log("Blocked pendant: "+name+"."+method);return true;},null,true);
    }
    private void hook(String name,String method,Before before,After after){hook(name,method,before,after,false);}
    private void hook(String name,String method,Before before,After after,boolean voidOnly){
        int installed=0;
        try{
            for(Method m:HostSymbols.type(name,loader).getDeclaredMethods()){
                if(!HostSymbols.matches(m,method)||(voidOnly&&m.getReturnType()!=void.class))continue;
                module.hook(m).intercept(chain->{
                    Object[] args=chain.getArgs().toArray();boolean skip=false;
                    host.log("Hook hit: "+name+"."+method);
                    if(before!=null)try{skip=before.apply(chain.getThisObject(),args);}catch(Throwable error){host.log("Hook filter error: "+name+"."+method+" "+rootError(error));}
                    if(skip)return null;
                    Object result=chain.proceed(args);
                    if(after!=null)try{after.apply(chain.getThisObject(),result);}catch(Throwable error){host.log("Hook result error: "+name+"."+method+" "+rootError(error));}
                    return result;
                });installed++;
            }
        }catch(Throwable error){host.log("Hook install error: "+name+"."+method+" "+rootError(error));}
        host.log("Hooks registered: "+name+"."+method+" = "+installed);
    }
    private void deoptimize(String name,String method){
        try{int count=0;for(Method m:HostSymbols.type(name,loader).getDeclaredMethods())if(HostSymbols.matches(m,method)){module.deoptimize(m);count++;}
            host.log("Caller deoptimized: "+name+"."+method+" = "+count);
        }catch(Throwable error){host.log("Caller deoptimization unavailable: "+name+" "+rootError(error));}
    }
    private static String rootError(Throwable error){return PrivacyRules.errorType(error);}

    /** Return a copy only when submenus change; do not modify cached native cards. */
    Object filterCard(Object card,int depth)throws Throwable {
        if(card==null||depth>8)return card;
        Object children=get(card,"b");if(!(children instanceof List<?>))return card;
        List<?> original=(List<?>)children;List<Object> next=new ArrayList<>();boolean changed=false;
        for(Object child:original){
            if(child!=null&&hiddenCard(enumName(get(child,"a")))){changed=true;host.log("Native submenu removed: "+enumName(get(child,"a")));continue;}
            Object filtered=filterCard(child,depth+1);changed|=filtered!=child;next.add(filtered);
        }
        if(!changed)return card;
        Object type=get(card,"a");Constructor<?> ctor=card.getClass().getDeclaredConstructor(((Enum<?>)type).getDeclaringClass(),List.class);
        ctor.setAccessible(true);Object copy=ctor.newInstance(type,next);
        for(Field f:card.getClass().getFields())if(!Modifier.isStatic(f.getModifiers())&&!HostSymbols.matches(f,"a")&&!HostSymbols.matches(f,"b")){
            f.setAccessible(true);f.set(copy,f.get(card));
        }
        return copy;
    }

    Object filterBookmall(Object source)throws Throwable {
        List<?> original=(List<?>)get(source,"bookMallTabDataList");if(original.isEmpty())return source;
        int oldIndex=((Number)get(source,"selectIndex")).intValue();
        Object selected=oldIndex>=0&&oldIndex<original.size()?original.get(oldIndex):null;
        List<Object> kept=new ArrayList<>();
        for(Object tab:original){
            String title=(String)get(tab,"tabName");int type=((Number)get(tab,"tabType")).intValue();
            String key=Rules.channelKey(title,type);if(key==null||!host.on(key))kept.add(tab);
        }
        if(kept.isEmpty())kept.add(original.get(0));
        if(kept.size()==original.size())return source;
        Object copy=copyBean(source);int newIndex=kept.indexOf(selected);boolean changed=newIndex<0;
        if(changed)newIndex=0;
        set(copy,"bookMallTabDataList",kept);set(copy,"selectIndex",newIndex);
        Object originals=get(source,"originalDataList");if(originals!=null){
            Object originalCopy=copyBean(originals);List<Object> raw=new ArrayList<>();
            for(Object tab:kept)raw.add(get(tab,"originalTabData"));
            set(originalCopy,"tabItem",raw);set(originalCopy,"tabIndex",newIndex);set(copy,"originalDataList",originalCopy);
        }
        if(changed){Object tab=kept.get(newIndex);set(copy,"defaultTabType",get(tab,"tabType"));set(copy,"defaultTabClientTemplate",get(tab,"clientTemplate"));set(copy,"defaultTabDataList",new ArrayList<>());}
        host.log("Native bookmall channels filtered: "+original.size()+" -> "+kept.size()+"; default replaced="+changed);
        return copy;
    }

    Object filterGameEntrances(Object source)throws Throwable {
        Object value=get(source,"mineEcomEntranceData");if(!(value instanceof List<?>))return source;
        List<?> original=(List<?>)value;List<Object> kept=new ArrayList<>();
        for(Object item:original){if(item!=null&&"Game".equals(enumName(get(item,"entranceType"))))continue;kept.add(item);}
        if(kept.size()==original.size())return source;
        Object copy=copyBean(source);set(copy,"mineEcomEntranceData",kept);host.log("Mine game entrance removed before rendering");return copy;
    }
    void filterSidebarMenu(Object model)throws Throwable {
        Object value=get(model,"a");if(!(value instanceof List<?>))return;
        List<?> original=(List<?>)value;List<Object> kept=new ArrayList<>();
        for(Object item:original){
            if(item==null){kept.add(null);continue;}
            String title=(String)get(item,"b");int type=((Number)get(item,"a")).intValue();
            String key=menuKey(title,rpcKind(type));if(key==null||!host.on(key))kept.add(item);
        }
        if(kept.size()!=original.size()){
            // be4.e.a creates a fresh ArrayList and returns it to Compose state. The source cache is untouched.
            @SuppressWarnings("unchecked") List<Object> mutable=(List<Object>)original;
            mutable.clear();mutable.addAll(kept);host.log("Sidebar menu filtered");
        }
    }
    private String rpcKind(int value){
        try{
            if(rpcKinds.isEmpty()){
                Class<?> type=HostSymbols.type("com.dragon.read.rpc.model.MyTabCellType",loader);Method getter=type.getDeclaredMethod(HostSymbols.methodName(type,"getValue"));getter.setAccessible(true);
                for(Object item:type.getEnumConstants())rpcKinds.put(((Number)getter.invoke(item)).intValue(),enumName(item));
            }
        }catch(Throwable ignored){}return rpcKinds.get(value);
    }
    private static void clearList(Object model,String field)throws Throwable {Object value=get(model,field);if(value instanceof List<?>)((List<?>)value).clear();}
    private static Object copyBean(Object source)throws Throwable {
        Constructor<?> ctor=source.getClass().getDeclaredConstructor();ctor.setAccessible(true);Object copy=ctor.newInstance();
        for(Field field:source.getClass().getFields())if(!Modifier.isStatic(field.getModifiers())){field.setAccessible(true);field.set(copy,field.get(source));}
        return copy;
    }
    private static Object get(Object object,String name)throws Throwable {Field field=object.getClass().getField(HostSymbols.fieldName(object.getClass(),name));field.setAccessible(true);return field.get(object);}
    private static void set(Object object,String name,Object value)throws Throwable {Field field=object.getClass().getField(HostSymbols.fieldName(object.getClass(),name));field.setAccessible(true);field.set(object,value);}
}
