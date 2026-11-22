package io.github.mianxyz.fanqiecustomizer;

import android.view.View;
import android.view.ViewGroup;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;

/** Opt-in, event-driven native rank geometry. All callbacks run on the UI thread. */
final class RankLayoutFix {
    private static final Map<View,State> states=new WeakHashMap<>();
    private RankLayoutFix(){}
    static void update(View panel,boolean enabled){
        if(panel==null)return;
        if(!enabled){restore(panel);return;}
        if(states.containsKey(panel))return; // O(1): no tree walk, reflection or layout writes.
        if(!NativeCoverGlass.matches(panel))return;
        State state=new State(panel);states.put(panel,state);state.bind(panel);state.schedule();
    }
    static void restore(View panel){
        State state=states.remove(panel);if(state!=null)state.restore();
    }
    private static final class Original {
        final int width,left,top,right,bottom; final boolean column; int applied;
        Original(View v,boolean column){width=v.getLayoutParams().width;left=v.getPaddingLeft();top=v.getPaddingTop();right=v.getPaddingRight();bottom=v.getPaddingBottom();this.column=column;applied=width;}
    }
    private static final class State implements View.OnLayoutChangeListener,Runnable {
        final WeakReference<View> panel;
        final Map<View,Original> originals=new WeakHashMap<>();
        final Map<View,Boolean> pending=new WeakHashMap<>();
        final List<WeakReference<ViewGroup>> lists=new ArrayList<>();
        final Map<ViewGroup,Object> attachListeners=new WeakHashMap<>();
        final Map<ViewGroup,Method> removers=new WeakHashMap<>();
        final Map<ViewGroup,Method> adapterMethods=new WeakHashMap<>();
        final Map<Class<?>,Method> countMethods=new HashMap<>();
        final Map<ViewGroup,Integer> availableWidths=new WeakHashMap<>(),columnCounts=new WeakHashMap<>();
        final Map<View,Method> scrollMethods=new WeakHashMap<>();
        boolean queued,closed,geometry=true;
        State(View owner){panel=new WeakReference<>(owner);}
        void bind(View owner){
            owner.addOnLayoutChangeListener(this);
            for(android.view.ViewParent p=owner.getParent();p instanceof View;p=p.getParent()){View ancestor=(View)p;for(Class<?> c=ancestor.getClass();c!=null;c=c.getSuperclass())if(c.getName().equals("androidx.recyclerview.widget.RecyclerView")){try{scrollMethods.put(ancestor,ancestor.getClass().getMethod("getScrollState"));}catch(Throwable ignored){}break;}}
            ArrayDeque<View> queue=new ArrayDeque<>();queue.add(owner);int visited=0;
            while(!queue.isEmpty()&&visited++<256){View v=queue.removeFirst();
                if("hlf".equals(id(v))&&v instanceof ViewGroup){
                    ViewGroup list=(ViewGroup)v;lists.add(new WeakReference<>(list));list.addOnLayoutChangeListener(this);
                    try{adapterMethods.put(list,list.getClass().getMethod(HostSymbols.publicMethodName(list.getClass(),"getAdapter")));}catch(Throwable ignored){}
                    try{
                        Class<?> listener=Class.forName("androidx.recyclerview.widget.RecyclerView$OnChildAttachStateChangeListener",false,list.getClass().getClassLoader());
                        Object proxy=Proxy.newProxyInstance(listener.getClassLoader(),new Class<?>[]{listener},(p,m,args)->{
                            if(m.getName().equals("onChildViewAttachedToWindow")&&args!=null&&args[0] instanceof View){pending.put((View)args[0],Boolean.TRUE);geometry=true;schedule();}
                            if(m.getName().equals("hashCode"))return System.identityHashCode(p);
                            if(m.getName().equals("equals"))return args!=null&&p==args[0];
                            if(m.getName().equals("toString"))return "PulpRankAttach";
                            return null;
                        });
                        Method remove=list.getClass().getMethod("removeOnChildAttachStateChangeListener",listener);
                        list.getClass().getMethod("addOnChildAttachStateChangeListener",listener).invoke(list,proxy);
                        attachListeners.put(list,proxy);removers.put(list,remove);
                    }catch(Throwable error){GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null)g.logIssue("rank attach listener "+error.getClass().getSimpleName());}
                    continue; // Never revisit column subtrees during panel discovery.
                }
                if(v instanceof ViewGroup){ViewGroup group=(ViewGroup)v;for(int i=0;i<group.getChildCount()&&queue.size()<256;i++)queue.addLast(group.getChildAt(i));}
            }
        }
        public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob){
            if(closed)return;
            Original old=originals.get(v);
            if(old!=null){ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp!=null&&lp.width!=old.applied){pending.put(v,old.column);schedule();}return;}
            if(r-l!=or-ol){geometry=true;schedule();}
        }
        void schedule(){View owner=panel.get();if(!closed&&!queued&&owner!=null){queued=true;owner.post(this);}}
        public void run(){
            queued=false;View owner=panel.get();if(closed||owner==null)return;
            // Defer geometry until the containing bookmall RecyclerView is idle.
            for(Map.Entry<View,Method> entry:scrollMethods.entrySet()){
                try{
                    Object scroll=entry.getValue().invoke(entry.getKey());
                    if(scroll instanceof Number&&((Number)scroll).intValue()!=0){queued=true;owner.postDelayed(this,80);return;}
                }catch(Throwable ignored){}
            }
            for(WeakReference<ViewGroup> ref:lists){ViewGroup list=ref.get();if(list==null)continue;
                int available=list.getWidth()-list.getPaddingLeft()-list.getPaddingRight();
                int columns=columnCounts.getOrDefault(list,0);
                if(geometry){int count=count(list);columns=Math.min(4,count);}
                if(available<=0||columns<=0)continue;
                boolean changed=!Objects.equals(availableWidths.get(list),available)||!Objects.equals(columnCounts.get(list),columns);
                availableWidths.put(list,available);columnCounts.put(list,columns);
                int width=available/columns;
                if(changed){for(int i=0;i<list.getChildCount();i++)column(list.getChildAt(i),width);}
                else for(Map.Entry<View,Boolean> e:new ArrayList<>(pending.entrySet())){
                    View child=e.getKey();if(child==null)continue;
                    if(e.getValue()&&child.getParent()==list)column(child,width);
                    else if(!e.getValue()&&under(child,list))set(child,ViewGroup.LayoutParams.MATCH_PARENT,false);
                }
                if(changed){GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null)g.logApplied("rank-column-width columns="+columns+" available="+available+" width="+width);}
            }
            pending.clear();geometry=false;
        }
        int count(ViewGroup list){try{
            Method getter=adapterMethods.get(list);if(getter==null)return 0;
            Object adapter=getter.invoke(list);if(adapter==null)return 0;
            Method m=countMethods.get(adapter.getClass());
            if(m==null){m=adapter.getClass().getMethod(HostSymbols.publicMethodName(adapter.getClass(),"getItemCount"));m.setAccessible(true);countMethods.put(adapter.getClass(),m);}
            Object value=m.invoke(adapter);return value instanceof Number?((Number)value).intValue():0;
        }catch(Throwable ignored){return 0;}}
        void column(View column,int width){
            boolean known=originals.containsKey(column);set(column,width,true);
            if(known)return;
            ArrayDeque<View> inner=new ArrayDeque<>();inner.add(column);int n=0;
            while(!inner.isEmpty()&&n++<64){View child=inner.removeFirst();if("gi1".equals(id(child)))set(child,ViewGroup.LayoutParams.MATCH_PARENT,false);if(child instanceof ViewGroup){ViewGroup g=(ViewGroup)child;for(int k=0;k<g.getChildCount()&&inner.size()<64;k++)inner.addLast(g.getChildAt(k));}}
        }
        void set(View v,int width,boolean column){
            ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp==null)return;
            Original old=originals.get(v);if(old==null){old=new Original(v,column);originals.put(v,old);v.addOnLayoutChangeListener(this);}old.applied=width;
            if(column){int side=Math.min(old.left,old.right);if(v.getPaddingLeft()!=side||v.getPaddingRight()!=side)v.setPadding(side,old.top,side,old.bottom);}
            if(lp.width!=width){lp.width=width;v.setLayoutParams(lp);}
        }
        void restore(){
            closed=true;View owner=panel.get();if(owner!=null){owner.removeCallbacks(this);owner.removeOnLayoutChangeListener(this);}queued=false;
            for(WeakReference<ViewGroup> ref:lists){ViewGroup list=ref.get();if(list==null)continue;list.removeOnLayoutChangeListener(this);try{Method m=removers.get(list);if(m!=null)m.invoke(list,attachListeners.get(list));}catch(Throwable ignored){}}
            for(Map.Entry<View,Original> e:new ArrayList<>(originals.entrySet())){View v=e.getKey();Original old=e.getValue();if(v==null)continue;v.removeOnLayoutChangeListener(this);ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp!=null&&lp.width==old.applied&&lp.width!=old.width){lp.width=old.width;v.setLayoutParams(lp);}if(v.getPaddingLeft()!=old.left||v.getPaddingTop()!=old.top||v.getPaddingRight()!=old.right||v.getPaddingBottom()!=old.bottom)v.setPadding(old.left,old.top,old.right,old.bottom);}
            originals.clear();pending.clear();lists.clear();attachListeners.clear();removers.clear();adapterMethods.clear();countMethods.clear();scrollMethods.clear();
        }
    }
    private static boolean under(View v,View panel){for(View at=v;at!=null;){if(at==panel)return true;at=at.getParent() instanceof View?(View)at.getParent():null;}return false;}
    private static String id(View v){try{return HostSymbols.canonicalResource(v.getResources().getResourceEntryName(v.getId()));}catch(Throwable ignored){return "";}}
}
