package io.github.mianxyz.fanqiecustomizer;

import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/** Bounded, read-only model/layout facts. Model counts are not server response counts. */
final class RankDiagnostics {
    private static final Map<View,State> states=Collections.synchronizedMap(new WeakHashMap<View,State>());
    private static final int VIEW_LIMIT=256, MODEL_LIMIT=64, FIELD_LIMIT=96, LIST_SAMPLE=4;
    private static final class State { long sampled; String signature; int changes; }
    private static final Set<String> hookFacts=new LinkedHashSet<>();
    private RankDiagnostics(){}
    static void install(io.github.libxposed.api.XposedModule module,ClassLoader loader){
        if(!HostSymbols.requireCapability("RankDiagnostics",new String[]{"com.dragon.read.component.biz.impl.bookmall.holder.NewRankListHolder","com.dragon.read.component.biz.impl.bookmall.holder.ShadeRankListHolder","com.dragon.read.component.biz.impl.bookmall.widge.z","com.dragon.read.pages.bookmall.model.BookMallCellModel$RankCategoryDataModel","za6.j"},new String[]{"com.dragon.read.component.biz.impl.bookmall.widge.z#a(Lcom/dragon/read/rpc/model/RankListAlgoInfo;Ljava/util/List;)V","com.dragon.read.component.biz.impl.bookmall.widge.z#b(Lcom/dragon/read/pages/bookmall/model/BookMallCellModel$RankCategoryDataModel;Z)V","com.dragon.read.pages.bookmall.model.BookMallCellModel$RankCategoryDataModel#Z()Ljava/util/List;"},new String[]{"com.dragon.read.pages.bookmall.model.BookMallCellModel$RankCategoryDataModel#bookList:Ljava/util/List;","com.dragon.read.pages.bookmall.model.BookMallCellModel$RankCategoryDataModel#bookPageList:Ljava/util/List;","com.dragon.read.pages.bookmall.model.BookMallCellModel$RankCategoryDataModel#pageOffset:I"},new String[]{"hlf","gi1"}))return;
        installCurrent(module,loader);
        for(String name:new String[]{"com.dragon.read.component.biz.impl.bookmall.holder.NewRankListHolder","com.dragon.read.component.biz.impl.bookmall.holder.ShadeRankListHolder"}){
            try{for(Method method:HostSymbols.type(name,loader).getDeclaredMethods()){
                if(!(HostSymbols.matches(method,"m4")&&method.getParameterCount()==2||HostSymbols.matches(method,"p4")&&method.getParameterCount()==2))continue;
                module.hook(method).intercept(chain->{
                    String before=exactModel(chain.getThisObject(),chain.getArg(0));
                    Object result=chain.proceed();
                    String text="rank-exact-model-not-server "+name+"."+method.getName()+" "+before+" returnedList="+(result instanceof List<?>?((List<?>)result).size():"n/a");
                    synchronized(hookFacts){if(hookFacts.size()<48&&hookFacts.add(text)){GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null)g.logApplied(text);}}
                    return result;
                });
            }}catch(Throwable error){GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null)g.logIssue("rank-exact-install "+name+" "+error.getClass().getSimpleName());}
        }
    }
    private static void fact(String text){try{synchronized(hookFacts){if(hookFacts.size()>=96||!hookFacts.add(text))return;}GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null)g.logApplied(text);}catch(Throwable ignored){}}
    private static String listCount(Object value){return value instanceof List<?>?Integer.toString(((List<?>)value).size()):"unknown";}
    private static String category(Object model){
        try{Object pages=field(model,"bookPageList");StringBuilder out=new StringBuilder("bookList=").append(listCount(field(model,"bookList"))).append(" bookPageList=").append(listCount(pages)).append(" pageOffset=").append(number(field(model,"pageOffset"))).append(" pageSizes=[");
            if(pages instanceof List<?>){List<?> list=(List<?>)pages;for(int i=0;i<Math.min(24,list.size());i++){if(i>0)out.append(',');out.append(listCount(list.get(i)));}}return out.append(']').toString();
        }catch(Throwable error){return "model-read="+error.getClass().getSimpleName();}
    }
    private static void installCurrent(io.github.libxposed.api.XposedModule module,ClassLoader loader){
        try{Class<?> cls=HostSymbols.type("com.dragon.read.pages.bookmall.model.BookMallCellModel$RankCategoryDataModel",loader);Method m=cls.getDeclaredMethod(HostSymbols.methodName(cls,"Z"));module.hook(m).intercept(chain->{Object result=chain.proceed();try{fact("rank-current-model-not-server Z "+category(chain.getThisObject())+" selected="+listCount(result));}catch(Throwable ignored){}return result;});}catch(Throwable error){fact("rank-current-install Z "+error.getClass().getSimpleName());}
        try{for(Method m:HostSymbols.type("com.dragon.read.component.biz.impl.bookmall.widge.z",loader).getDeclaredMethods()){
            if(!(HostSymbols.matches(m,"b")||HostSymbols.matches(m,"a"))||m.getParameterCount()!=2)continue;
            module.hook(m).intercept(chain->{String before="unknown";try{before=HostSymbols.matches(m,"b")?category(chain.getArg(0)):"input="+listCount(chain.getArg(1));}catch(Throwable ignored){}
                Object result=chain.proceed();
                try{Object self=chain.getThisObject(),adapter=call(self,"getPageAdapter");fact("rank-current-model-not-server z."+m.getName()+" "+before+" groupSize="+number(call(self,"getPages"))+" origin="+listCount(call(self,"getOriginDataList"))+" groups="+number(call(adapter,"getItemCount")));}catch(Throwable ignored){}return result;});
        }}catch(Throwable error){fact("rank-current-install z "+error.getClass().getSimpleName());}
    }
    private static Object field(Object value,String name){if(value==null)return null;for(Class<?> c=value.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(HostSymbols.fieldName(c,name));f.setAccessible(true);return f.get(value);}catch(Throwable ignored){}return null;}
    private static String exactModel(Object holder,Object model){
        try{
            StringBuilder out=new StringBuilder("model=").append(type(model));Object ranks=field(model,"rankList");
            if(ranks instanceof List<?>){List<?> list=(List<?>)ranks;out.append(" rankList=").append(list.size()).append(" rawModelBooks=[");for(int i=0;i<Math.min(list.size(),24);i++){if(i>0)out.append(',');Object books=field(list.get(i),"rankBookData");out.append(books instanceof List<?>?((List<?>)books).size():-1);}out.append(']');}
            for(Class<?> c=holder.getClass();c!=null&&hostType(c.getName());c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(f.getType().getName().equals(HostSymbols.name("za6.j"))){
                f.setAccessible(true);Object config=f.get(holder);out.append(" config=za6.j rows=").append(number(call(config,"a"))).append(" columns=").append(number(call(config,"getColumnCount")));
                Object rows=call(config,"a"),columns=call(config,"getColumnCount");if(rows instanceof Number&&columns instanceof Number)out.append(" product=").append(((Number)rows).intValue()*((Number)columns).intValue());
            }
            return out.toString();
        }catch(Throwable error){return "model-read="+error.getClass().getSimpleName();}
    }
    static void inspect(View panel){
        if(panel==null)return;
        State state=states.get(panel);if(state==null){state=new State();states.put(panel,state);}
        long now=SystemClock.uptimeMillis();if(now-state.sampled<1000)return;state.sampled=now;
        try{
            StringBuilder out=new StringBuilder("rank-model-not-server panel=").append(geometry(panel));
            ArrayDeque<View> queue=new ArrayDeque<>();queue.add(panel);int count=0;
            Set<Object> seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
            while(!queue.isEmpty()&&count++<VIEW_LIMIT){
                View view=queue.removeFirst();String id=id(view);
                if("hlf".equals(id)||"gi1".equals(id)){
                    out.append(" | ").append(id).append(' ').append(view.getClass().getName()).append(' ').append(geometry(view));
                    Object adapter=call(view,"getAdapter"),layout=call(view,"getLayoutManager");
                    out.append(" adapter=").append(type(adapter)).append(" items=").append(number(call(adapter,"getItemCount")));
                    out.append(" layout=").append(type(layout));
                    if(layout!=null)out.append(" span=").append(number(call(layout,"getSpanCount"))).append(" orientation=").append(number(call(layout,"getOrientation")));
                    if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;out.append(" attached=").append(group.getChildCount()).append(" childWidths=[");
                        for(int i=0;i<Math.min(24,group.getChildCount());i++){if(i>0)out.append(',');View child=group.getChildAt(i);out.append(child.getWidth()).append('/').append(child.getMeasuredWidth());ViewGroup.LayoutParams lp=child.getLayoutParams();out.append('/').append(lp==null?"?":lp.width);}
                        out.append(']');
                    }
                    scan(adapter,"adapter",0,seen,new int[]{0,0},out);
                }
                if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount()&&queue.size()<VIEW_LIMIT;i++)queue.addLast(group.getChildAt(i));}
            }
            if(!queue.isEmpty())out.append(" view-truncated");
            String signature=out.toString();
            if(!signature.equals(state.signature)&&state.changes<48){state.signature=signature;state.changes++;GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null)g.logApplied(signature);}
        }catch(Throwable error){GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null)g.logIssue("rank-diagnostic "+error.getClass().getSimpleName());}
    }
    private static void scan(Object value,String path,int depth,Set<Object> seen,int[] budget,StringBuilder out){
        if(value==null||depth>4||budget[0]>=MODEL_LIMIT||out.length()>12000||!seen.add(value))return;
        budget[0]++;
        if(value instanceof List<?>){
            List<?> list=(List<?>)value;int size=list.size();out.append(" model-list ").append(path).append(" type=").append(type(value)).append(" size=").append(size);
            if(size>0){Object first=list.get(0);out.append(" element=").append(type(first));}
            for(int i=0;i<Math.min(size,LIST_SAMPLE);i++)scan(list.get(i),path+"["+i+"]",depth+1,seen,budget,out);
            if(size>LIST_SAMPLE)out.append(" sample=").append(LIST_SAMPLE);
            return;
        }
        String name=value.getClass().getName();if(!hostType(name))return;
        for(Class<?> cls=value.getClass();cls!=null&&cls!=Object.class&&hostType(cls.getName());cls=cls.getSuperclass()){
            for(Field field:cls.getDeclaredFields()){
                if(budget[1]++>=FIELD_LIMIT)return;
                if(Modifier.isStatic(field.getModifiers())||field.getType().isPrimitive()||field.getType()==String.class||CharSequence.class.isAssignableFrom(field.getType())||View.class.isAssignableFrom(field.getType()))continue;
                try{field.setAccessible(true);Object child=field.get(value);if(child instanceof List<?>||child!=null&&hostType(child.getClass().getName()))scan(child,path+"."+cls.getSimpleName()+"."+field.getName(),depth+1,seen,budget,out);}catch(Throwable ignored){}
            }
        }
    }
    private static boolean hostType(String name){return name.startsWith("com.dragon.read.")||name.matches("[a-z][a-z0-9]{0,6}[.].+");}
    private static Object call(Object target,String name){if(target==null)return null;try{Method method=target.getClass().getMethod(HostSymbols.publicMethodName(target.getClass(),name));method.setAccessible(true);return method.invoke(target);}catch(Throwable ignored){return null;}}
    private static String type(Object value){return value==null?"unknown":value.getClass().getName();}
    private static String number(Object value){return value instanceof Number?value.toString():"unknown";}
    private static String id(View view){try{return HostSymbols.canonicalResource(view.getResources().getResourceEntryName(view.getId()));}catch(Throwable ignored){return "";}}
    private static String geometry(View v){ViewGroup.LayoutParams lp=v.getLayoutParams();return "w="+v.getWidth()+" mw="+v.getMeasuredWidth()+" lpw="+(lp==null?"?":lp.width)+" pad="+v.getPaddingLeft()+","+v.getPaddingRight()+" x="+v.getLeft()+","+v.getRight();}
}
