package io.github.mianxyz.fanqiecustomizer;
import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import java.util.Map;
import java.util.WeakHashMap;
import io.github.libxposed.api.XposedModule;
/** UI-thread-only coordination; never changes the host touch result or listeners. */
final class ScrollWorkGate {
 private static final Map<View,State> states=new WeakHashMap<>();
 private static boolean installed;
 private static final Map<View,Integer> scrolling=new WeakHashMap<>();
 static void observeRecycler(XposedModule module,ClassLoader loader){try{Class<?> cls=Class.forName("androidx.recyclerview.widget.RecyclerView",false,loader);module.hook(cls.getDeclaredMethod("setScrollState",int.class)).intercept(chain->{View view=(View)chain.getThisObject();int next=(Integer)chain.getArg(0);scrolling.put(view,next);State s=state(view.getRootView());s.lastScroll=SystemClock.uptimeMillis();return chain.proceed();});}catch(Throwable error){AsyncLog.log(module,android.util.Log.INFO,"FanqieCustomizer","Recycler scroll observation failed: "+error.getClass().getSimpleName());}}
 private static final class State {boolean touching;long lastScroll;}
 static void install(XposedModule module){
  if(installed)return;
  try{module.hook(Activity.class.getDeclaredMethod("dispatchTouchEvent",MotionEvent.class)).intercept(chain->{
   Activity a=(Activity)chain.getThisObject();View root=a.getWindow().getDecorView();State state=state(root);
   int action=((MotionEvent)chain.getArg(0)).getActionMasked();
   if(action==MotionEvent.ACTION_DOWN)state.touching=true;
   try{return chain.proceed();}finally{if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){state.touching=false;state.lastScroll=SystemClock.uptimeMillis();}}
  });installed=true;}catch(Throwable error){AsyncLog.log(module,android.util.Log.INFO,"FanqieCustomizer","Scroll observation failed: "+error.getClass().getSimpleName());}
 }
 private static State state(View root){State s=states.get(root);if(s==null){s=new State();states.put(root,s);final State tracked=s;root.getViewTreeObserver().addOnScrollChangedListener(()->tracked.lastScroll=SystemClock.uptimeMillis());}return s;}
 static boolean busy(View view){if(view==null)return false;View root=view.getRootView();State s=state(root);if(s.touching||SystemClock.uptimeMillis()-s.lastScroll<180)return true;for(Map.Entry<View,Integer> entry:scrolling.entrySet())if(entry.getValue()!=0&&entry.getKey().isAttachedToWindow()&&entry.getKey().isShown()&&entry.getKey().getRootView()==root)return true;return false;}
}
