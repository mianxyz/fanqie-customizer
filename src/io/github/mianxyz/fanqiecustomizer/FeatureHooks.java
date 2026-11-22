package io.github.mianxyz.fanqiecustomizer;

import android.app.Activity;
import android.view.View;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import io.github.libxposed.api.XposedModule;

/** Version-specific model filtering. Failed adaptations leave the original call in place. */
final class FeatureHooks {
    interface Host {
        boolean on(String key);
        void log(String text);
        void forceHide(View view,String reason);
        void dataChanged();
        default boolean readerVisible(){return false;}
        default boolean tabletLayout(){return false;}
        /** 整型参数（滑块）。默认返回 Rules 的缺省值，旧实现无需改动。 */
        default int intValue(String key){return Rules.intDefault(key);}
    }
    private final XposedModule module;
    private final Host host;
    private final ClassLoader loader;
    private final Set<String> abNames=Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String,String> counts=Collections.synchronizedMap(new LinkedHashMap<String,String>(64,0.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,String> e){return size()>128;}
    });
    interface Action { void apply(Object value)throws Throwable; }
    interface ArgAction { void apply(Object[] args)throws Throwable; }
    FeatureHooks(XposedModule module,Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}

    void install(){
        if(any(Rules.MINE_KEYS)||any(TabletRules.KEYS)||host.on("hide_income")||host.on("hide_frequent_games")){
            deserialize("com.dragon.read.rpc.model.GetMyTabPlanRespData",this::filterMinePlan);
            before("com.dragon.read.kmp.mine.tab.data.MineTabCardDataManager","d",args->{if(args.length>0)filterMinePlan(args[0]);});
            before("com.dragon.read.component.biz.impl.mine.card.model.QuickAccessCardRenderer","p",args->{if(args.length==1&&args[0] instanceof List<?>)args[0]=filterNativeItems((List<?>)args[0]);});
            constructors("com.dragon.read.component.biz.impl.mine.card.model.QuickAccessCardRenderer",args->{if(args.length>0&&args[0] instanceof List<?>)args[0]=filterNativeItems((List<?>)args[0]);});
        }
        if(host.on("hide_frequent_games"))deserialize("com.dragon.read.rpc.model.MineGameEntranceData",v->{
            set(v,"gameItems",new ArrayList<>());
        });
        if(host.on("hide_income"))afterResult("com.dragon.read.component.biz.impl.mine.card.model.PolarisCardWrapper","b",v->{if(v instanceof View)host.forceHide((View)v,"income card");});
        if(host.on("hide_reader_video_entry"))after("l23.c0","j1",v->{
            if(Boolean.TRUE.equals(invoke(v,"n1"))){Object view=invoke(v,"g1");if(view instanceof View)host.forceHide((View)view,"chapter video reward entry");}
        });
        if(host.on("hide_welfare_float"))blockVoid("com.dragon.read.polaris.cold.start.l","tryShowLowTakeCashFloatingView");
        if(host.on("raw_read_counts")){
            deserialize("com.dragon.read.rpc.model.BookDetailData",this::recordCount);
        }
        if(host.on("hide_reader_audio")||host.on("hide_post_float")||host.on("trace_ab"))installConfig();
    }

    String countFor(String id){return counts.get(id);}
    private boolean any(String[] keys){for(String key:keys)if(host.on(key))return true;return false;}
    private String menuKey(String title,String type){String key=Rules.mineKey(title,type);return key!=null?key:TabletRules.key(host.tabletLayout(),title,type);}
    private void guarded(String operation,Runnable work){try{work.run();}catch(Throwable error){host.log(operation+": "+error.getClass().getSimpleName());}}
    private Class<?> cls(String name)throws ClassNotFoundException{return HostSymbols.type(name,loader);}
    private void deserialize(String name,Action action){guarded("model "+name,()->{
        try{
            for(Class<?> nested:cls(name).getDeclaredClasses())for(Method method:nested.getDeclaredMethods())
                if(HostSymbols.matches(method,"deserialize")&&method.getParameterCount()==1){
                    module.hook(method).intercept(chain->{Object result=chain.proceed();try{if(result!=null&&HostSymbols.canonical(result.getClass().getName()).equals(name))action.apply(result);}catch(Throwable e){host.log("model adaptation "+name+": "+e.getClass().getSimpleName());}return result;});
                }
        }catch(Throwable e){throw new IllegalStateException(e);}
    });}
    private void before(String name,String methodName,ArgAction action){guarded("before "+name,()->{
        try{for(Method method:cls(name).getDeclaredMethods())if(HostSymbols.matches(method,methodName))
            module.hook(method).intercept(chain->{Object[] args=chain.getArgs().toArray();try{action.apply(args);}catch(Throwable e){host.log("filter "+name+": "+e.getClass().getSimpleName());}return chain.proceed(args);});
        }catch(Throwable e){throw new IllegalStateException(e);}
    });}
    private void constructors(String name,ArgAction action){guarded("constructor "+name,()->{
        try{for(Constructor<?> ctor:cls(name).getDeclaredConstructors())module.hook(ctor).intercept(chain->{Object[] args=chain.getArgs().toArray();try{action.apply(args);}catch(Throwable e){host.log("constructor filter failed");}return chain.proceed(args);});}
        catch(Throwable e){throw new IllegalStateException(e);}
    });}
    private void after(String name,String methodName,Action action){guarded("after "+name,()->{
        try{for(Method method:cls(name).getDeclaredMethods())if(HostSymbols.matches(method,methodName))module.hook(method).intercept(chain->{Object result=chain.proceed();try{action.apply(chain.getThisObject());}catch(Throwable e){host.log("view adaptation failed: "+name);}return result;});}
        catch(Throwable e){throw new IllegalStateException(e);}
    });}
    private void afterResult(String name,String methodName,Action action){guarded("result "+name,()->{
        try{for(Method method:cls(name).getDeclaredMethods())if(HostSymbols.matches(method,methodName)&&method.getParameterCount()==0)module.hook(method).intercept(chain->{Object result=chain.proceed();try{action.apply(result);}catch(Throwable e){host.log("result adaptation failed: "+name);}return result;});}
        catch(Throwable e){throw new IllegalStateException(e);}
    });}
    private void blockVoid(String name,String methodName){guarded("block "+name,()->{
        try{for(Method method:cls(name).getDeclaredMethods())if(HostSymbols.matches(method,methodName)&&method.getReturnType()==void.class)module.hook(method).intercept(chain->null);}
        catch(Throwable e){throw new IllegalStateException(e);}
    });}
    private void installConfig(){guarded("AB config",()->{
        try{for(Method method:cls("com.dragon.read.base.ssconfig.SsConfigMgr").getDeclaredMethods())
            if(HostSymbols.matches(method,"getABValue")&&method.getParameterCount()>0&&method.getParameterTypes()[0]==String.class){
                module.hook(method).intercept(chain->{Object result=chain.proceed();String key=(String)chain.getArg(0);
                    try{
                        if(host.on("trace_ab")&&PrivacyRules.knownConfigName(key)&&abNames.add(key))host.log("AB key: "+key);
                        if(result!=null&&host.on("hide_reader_audio")&&"reader_hiden_audio_btn_v561".equals(key))set(result,"enable",true);
                        if(result!=null&&host.on("hide_post_float")&&"new_post_bookstore_entrance_config_v717".equals(key))set(result,"enable",false);
                    }catch(Throwable e){host.log("AB adaptation failed: "+PrivacyRules.errorType(e));}return result;
                });
            }
        }catch(Throwable e){throw new IllegalStateException(e);}
    });}

    private void filterChannels(Object data)throws Throwable {
        Object source=get(data,"tabItem");if(!(source instanceof List<?>))return;
        List<?> original=(List<?>)source;List<Object> kept=new ArrayList<>();
        int oldIndex=((Number)get(data,"tabIndex")).intValue();
        Object oldSelection=oldIndex>=0&&oldIndex<original.size()?original.get(oldIndex):null;
        for(Object item:original){String title=string(get(item,"title"));int type=((Number)get(item,"tabType")).intValue();String key=Rules.channelKey(title,type);if(key==null||!host.on(key))kept.add(item);}
        if(kept.isEmpty()&&!original.isEmpty())kept.add(original.get(0));
        if(kept.size()==original.size())return;
        set(data,"tabItem",kept);int next=kept.indexOf(oldSelection);set(data,"tabIndex",Math.max(0,next));
        host.log("Filtered bookmall channels: "+original.size()+" -> "+kept.size());
    }
    private void filterMinePlan(Object plan)throws Throwable {
        if(plan==null)return;Object source=get(plan,"cellViewList");if(source instanceof List<?>)set(plan,"cellViewList",mineCells((List<?>)source,0));
    }
    private List<Object> mineCells(List<?> source,int depth)throws Throwable {
        if(depth>8)return new ArrayList<>(source);
        List<Object> out=new ArrayList<>();
        for(Object cell:source){
            if(cell==null){out.add(null);continue;}
            String title=string(get(cell,"myTabCellName"));Object type=get(cell,"myTabCellType");String kind=type instanceof Enum<?>?((Enum<?>)type).name():null;
            String key=menuKey(title,kind);if(key!=null&&host.on(key))continue;
            Object sub=get(cell,"myTabSubCellData");if(sub instanceof List<?>)set(cell,"myTabSubCellData",mineCells((List<?>)sub,depth+1));
            Object enums=get(cell,"myTabSubCell");if(enums instanceof List<?>){List<Object> kept=new ArrayList<>();for(Object value:(List<?>)enums){String subKey=value instanceof Enum<?>?menuKey(null,((Enum<?>)value).name()):null;if(subKey==null||!host.on(subKey))kept.add(value);}set(cell,"myTabSubCell",kept);}
            out.add(cell);
        }
        return out;
    }
    private List<Object> filterNativeItems(List<?> source)throws Throwable {
        List<Object> out=new ArrayList<>();
        for(Object item:source){boolean drop=false;
            if(item!=null)for(Field field:item.getClass().getFields())if(CharSequence.class.isAssignableFrom(field.getType())||field.getType()==String.class){
                Object label=field.get(item);String key=label instanceof CharSequence?menuKey(label.toString(),null):null;if(key!=null&&host.on(key)){drop=true;break;}
            }
            if(!drop)out.add(item);
        }
        return out;
    }
    void recordCount(Object data)throws Throwable {
        String id=string(get(data,"bookId"));if(id==null||id.isEmpty())return;
        String name=string(get(data,"bookName"));String read=string(get(data,"readCount"));String all=string(get(data,"readCountAll"));
        String message="接口原始阅读数据"+(name==null?"":" · "+name)+"\nread_count: "+missing(read)+"\nread_count_all: "+missing(all)+"\n统计口径由服务器决定，不代表实时真实人数";
        if(!message.equals(counts.put(id,message)))host.dataChanged();
    }
    private String missing(String value){return value==null||value.trim().isEmpty()?"未提供":value;}
    private static String string(Object value){return value instanceof CharSequence?value.toString():null;}
    private static Object get(Object object,String field)throws Throwable {Field f=object.getClass().getField(HostSymbols.publicFieldName(object.getClass(),field));return f.get(object);}
    private static void set(Object object,String field,Object value)throws Throwable {Field f=object.getClass().getField(HostSymbols.publicFieldName(object.getClass(),field));f.setAccessible(true);f.set(object,value);}
    private static Object invoke(Object object,String method)throws Throwable {Method m=object.getClass().getMethod(HostSymbols.publicMethodName(object.getClass(),method));m.setAccessible(true);return m.invoke(object);}
}
