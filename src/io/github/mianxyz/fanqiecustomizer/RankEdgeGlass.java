package io.github.mianxyz.fanqiecustomizer;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import java.lang.ref.WeakReference;
import java.util.*;
/** Rank-local edge ownership, independent from the optional panel background. */
final class RankEdgeGlass implements View.OnAttachStateChangeListener,ViewTreeObserver.OnPreDrawListener {
 private static final Map<View,RankEdgeGlass> states=new WeakHashMap<>();
 private final WeakReference<View> root;
 private final Map<View,Drawable> backgrounds=new WeakHashMap<>(),foregrounds=new WeakHashMap<>();
 private final Map<View,Boolean> fades=new WeakHashMap<>();
 private final Map<View,Integer> lengths=new WeakHashMap<>();
 private boolean feather;
 private RankEdgeGlass(View v){root=new WeakReference<>(v);}
 static void update(View panel,boolean enabled,boolean feather){
  if(!enabled){restore(panel);return;}
  RankEdgeGlass state=states.get(panel);
  if(state==null){state=new RankEdgeGlass(panel);states.put(panel,state);panel.addOnAttachStateChangeListener(state);panel.getViewTreeObserver().addOnPreDrawListener(state);state.discover(panel);}
  state.feather=feather;state.apply();
 }
 static void restore(View panel){RankEdgeGlass s=states.remove(panel);if(s==null)return;panel.removeOnAttachStateChangeListener(s);if(panel.getViewTreeObserver().isAlive())panel.getViewTreeObserver().removeOnPreDrawListener(s);
  for(var e:s.backgrounds.entrySet())if(e.getKey().getBackground()==null)e.getKey().setBackground(e.getValue());
  for(var e:s.foregrounds.entrySet())if(e.getKey().getForeground()==null)e.getKey().setForeground(e.getValue());
  for(var e:s.fades.entrySet()){e.getKey().setHorizontalFadingEdgeEnabled(e.getValue());e.getKey().setFadingEdgeLength(s.lengths.get(e.getKey()));}
 }
 static void restoreRoot(View root){for(View v:new ArrayList<>(states.keySet()))if(root==null||v.getRootView()==root)restore(v);}
 private String id(View v){try{return HostSymbols.canonicalResource(v.getResources().getResourceEntryName(v.getId()));}catch(Throwable e){return "";}}
 private boolean white(Drawable d){return d instanceof GradientDrawable&&NativeCoverGlass.whiteFade(((GradientDrawable)d).getColors());}
 private void discover(View v){String id=id(v);if(NativeCoverGlass.rankEdge(id)){if(white(v.getBackground()))backgrounds.put(v,v.getBackground());if(white(v.getForeground()))foregrounds.put(v,v.getForeground());}
  if("hlf".equals(id)||"gi1".equals(id)){fades.put(v,v.isHorizontalFadingEdgeEnabled());lengths.put(v,v.getHorizontalFadingEdgeLength());}
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)discover(g.getChildAt(i));}
 }
 private void apply(){for(View v:backgrounds.keySet())if(white(v.getBackground())){backgrounds.put(v,v.getBackground());v.setBackground(null);}for(View v:foregrounds.keySet())if(white(v.getForeground())){foregrounds.put(v,v.getForeground());v.setForeground(null);}
  for(View v:fades.keySet()){if(v.isHorizontalFadingEdgeEnabled()!=feather)v.setHorizontalFadingEdgeEnabled(feather);int length=feather?Math.max(1,Math.round(10*v.getResources().getDisplayMetrics().density)):lengths.get(v);if(v.getHorizontalFadingEdgeLength()!=length)v.setFadingEdgeLength(length);}
 }
 public boolean onPreDraw(){apply();return true;}
 public void onViewAttachedToWindow(View v){v.getViewTreeObserver().removeOnPreDrawListener(this);v.getViewTreeObserver().addOnPreDrawListener(this);apply();}
 public void onViewDetachedFromWindow(View v){if(v.getViewTreeObserver().isAlive())v.getViewTreeObserver().removeOnPreDrawListener(this);}
}
