package io.github.mianxyz.fanqiecustomizer;
import android.app.*;
import android.os.Bundle;
import android.view.*;
import android.widget.*;
import java.util.*;
final class BookmallBusinessViews {
    private final FeatureHooks.Host host;
    private final Map<View,Integer> heights=new WeakHashMap<>();
    private final Map<View,Integer> visibility=new WeakHashMap<>();
    private final Map<View,Float> decorationAlphas=new WeakHashMap<>();
    private final Map<View,android.graphics.Rect> decorationClips=new WeakHashMap<>();
    private final Map<View,Integer> decorationVisibility=new WeakHashMap<>();
    private final Map<View,android.graphics.drawable.Drawable> whiteBackgrounds=new WeakHashMap<>(),whiteForegrounds=new WeakHashMap<>();
    private final Map<View,HeaderEdgeFade> headerFades=new WeakHashMap<>();
    private String last="";
    /** Fade only the folded host decoration to the page color before its measured clip edge. */
    private static final class HeaderEdgeFade extends android.graphics.drawable.Drawable {
        final android.graphics.Paint paint=new android.graphics.Paint();int bottom,color,width;float span;
        void update(View v,int edge){int next=GlobalGlassSurfaces.storeBackdropColor(v.getContext());float length=Math.min(edge,48f*v.getResources().getDisplayMetrics().density);if(bottom==edge&&color==next&&span==length&&width==v.getWidth())return;bottom=edge;color=next;span=length;width=v.getWidth();setBounds(0,0,width,edge);paint.setShader(new android.graphics.LinearGradient(0,Math.max(0,edge-length),0,edge,new int[]{next&0x00ffffff,(next&0x00ffffff)|0x40000000,(next&0x00ffffff)|0xBF000000,next},new float[]{0f,.333333f,.666667f,1f},android.graphics.Shader.TileMode.CLAMP));invalidateSelf();}
        public void draw(android.graphics.Canvas c){c.drawRect(getBounds(),paint);}
        public void setAlpha(int a){}public void setColorFilter(android.graphics.ColorFilter f){}public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
    }
    private final Map<View,Integer> originalTopMargins=new WeakHashMap<>(),originalParentTopPadding=new WeakHashMap<>(),minimums=new WeakHashMap<>();
    private final Set<View> watched=Collections.newSetFromMap(new WeakHashMap<View,Boolean>());
    BookmallBusinessViews(FeatureHooks.Host host){this.host=host;}
    void install(Application app){if(!HostSymbols.requireCapability("BookmallBusinessViews",new String[]{"com.dragon.read.component.biz.impl.bookmall.widge.BookstoreHeaderBgView","com.dragon.read.component.biz.impl.bookmall.widge.DetectTouchEventFrameLayout","com.dragon.read.pages.main.MainFragmentActivity","com.dragon.read.widget.refresh.SuperSwipeRefreshLayout","com.google.android.material.appbar.AppBarLayout"},new String[]{},new String[]{},new String[]{"ik","gat","h6","alr","c7q"}))return;app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
        public void onActivityResumed(Activity a){if(!a.getClass().getName().equals(HostSymbols.name("com.dragon.read.pages.main.MainFragmentActivity")))return;
            View decor=a.getWindow().getDecorView();if(!watched.add(decor))return;final long[] lastCheck={0};
            decor.getViewTreeObserver().addOnPreDrawListener(()->{long now=android.os.SystemClock.uptimeMillis();if(now-lastCheck[0]<150)return true;lastCheck[0]=now;try{Object fm=a.getClass().getMethod(HostSymbols.publicMethodName(a.getClass(),"getSupportFragmentManager")).invoke(a);for(Object f:(List<?>)fm.getClass().getMethod(HostSymbols.publicMethodName(fm.getClass(),"getFragments")).invoke(fm)){if(f==null||!"NewBookMallFragment".equals(f.getClass().getMethod(HostSymbols.publicMethodName(f.getClass(),"getTag")).invoke(f)))continue;View root=(View)f.getClass().getMethod(HostSymbols.publicMethodName(f.getClass(),"getView")).invoke(f);if(root!=null&&root.isShown()){apply(root);}}}catch(Throwable error){host.log("Bookmall header error: "+android.util.Log.getStackTraceString(error));}return true;});}
        public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityPaused(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}public void onActivityDestroyed(Activity a){}
    });}
    private View id(View root,String name){int id=root.getResources().getIdentifier(HostSymbols.resourceName(name),"id",root.getContext().getPackageName());return id==0?null:root.findViewById(id);}
    private void apply(View root){View top=id(root,"ik");if(top==null||!top.getClass().getName().equals(HostSymbols.name("com.google.android.material.appbar.AppBarLayout")))return;
        View search=id(top,"gat"), channels=id(top,"h6"), tabs=id(top,"alr"), inner=id(top,"c7q");
        if(search==null||channels==null||tabs==null||inner==null)return;
        int count=countTitles(tabs);boolean enabled=host.on("enabled"),hideSearch=enabled&&host.on("hide_search_bar"),hideChannels=enabled&&BookmallBusinessRules.collapseChannels(count);
        if(top.getHeight()<=0&&!heights.containsKey(top))return;
        int base=rememberHeight(top), baseInner=rememberHeight(inner), channelHeight=rememberHeight(channels);
        if(baseInner<=0||channelHeight<=0)return;
        int searchContribution=Math.max(0,baseInner-channelHeight);
        toggle(search,hideSearch);toggle(channels,hideChannels);
        int removed=(hideSearch?searchContribution:0)+(hideChannels?channelHeight:0);
        minimums.putIfAbsent(inner,inner.getMinimumHeight());minimums.putIfAbsent(top,top.getMinimumHeight());
        inner.setMinimumHeight(removed>0?0:minimums.get(inner));top.setMinimumHeight(removed>0?0:minimums.get(top));
        height(inner,Math.max(0,baseInner-removed));height(top,Math.max(0,base-removed));
        int statusId=root.getResources().getIdentifier("status_bar_height","dimen","android");int status=statusId==0?0:root.getResources().getDimensionPixelSize(statusId);
        int[] rootPos=new int[2],topPos=new int[2];root.getLocationOnScreen(rootPos);top.getLocationOnScreen(topPos);
        int headerBottom=Math.max(status,topPos[1]-rootPos[1]+Math.max(0,base-removed));
        adjustContentTop(root,removed,headerBottom,!hideSearch&&hideChannels,0);
        foldHeaderDecoration(root,removed>0,hideSearch&&hideChannels,headerBottom,0);
        clearWhiteDecoration(top,removed>0);
        String state="search="+hideSearch+" visibleChannels="+count+" collapsed="+hideChannels;
        if(!state.equals(last)){last=state;host.log("Bookmall header rows: "+state);}
    }
    private boolean solidWhite(android.graphics.drawable.Drawable d){if(!(d instanceof android.graphics.drawable.ColorDrawable))return false;int c=((android.graphics.drawable.ColorDrawable)d).getColor();return android.graphics.Color.alpha(c)>=230&&android.graphics.Color.red(c)>245&&android.graphics.Color.green(c)>245&&android.graphics.Color.blue(c)>245;}
    private void clearWhiteDecoration(View top,boolean folded){
        if(!folded){for(Map.Entry<View,android.graphics.drawable.Drawable> e:whiteBackgrounds.entrySet())e.getKey().setBackground(e.getValue());for(Map.Entry<View,android.graphics.drawable.Drawable> e:whiteForegrounds.entrySet())e.getKey().setForeground(e.getValue());whiteBackgrounds.clear();whiteForegrounds.clear();return;}
        for(View v:new View[]{top,id(top,"c7q")}){if(v==null)continue;
            if(solidWhite(v.getBackground())&&!whiteBackgrounds.containsKey(v)){whiteBackgrounds.put(v,v.getBackground());v.setBackground(null);host.log("Glass top blocker cleared: class="+v.getClass().getName()+" id="+v.getId()+" property=background");}
            if(solidWhite(v.getForeground())&&!whiteForegrounds.containsKey(v)){whiteForegrounds.put(v,v.getForeground());v.setForeground(null);host.log("Glass top blocker cleared: class="+v.getClass().getName()+" id="+v.getId()+" property=foreground");}
        }
    }
    private void foldHeaderDecoration(View v,boolean folded,boolean allHidden,int bottom,int depth){
        if(depth>10)return;
        if(v.getClass().getName().equals(HostSymbols.name("com.dragon.read.component.biz.impl.bookmall.widge.BookstoreHeaderBgView"))){
            if(!decorationAlphas.containsKey(v)){decorationAlphas.put(v,v.getAlpha());decorationVisibility.put(v,v.getVisibility());android.graphics.Rect clip=v.getClipBounds();decorationClips.put(v,clip==null?null:new android.graphics.Rect(clip));}
            HeaderEdgeFade fade=headerFades.get(v);boolean soften=folded&&!allHidden&&GlobalGlassSurfaces.panels();
            if(soften){if(fade==null){fade=new HeaderEdgeFade();headerFades.put(v,fade);v.getOverlay().add(fade);}int[] xy=new int[2],base=new int[2];v.getLocationOnScreen(xy);v.getRootView().getLocationOnScreen(base);fade.update(v,Math.max(0,bottom-(xy[1]-base[1])));}
            else if(fade!=null){v.getOverlay().remove(fade);headerFades.remove(v);}
            if(!folded){v.setAlpha(decorationAlphas.get(v));v.setVisibility(decorationVisibility.get(v));v.setClipBounds(decorationClips.get(v));}
            else if(allHidden){v.setAlpha(0f);}
            else{v.setAlpha(decorationAlphas.get(v));int[] pos=new int[2],root=new int[2];v.getLocationOnScreen(pos);v.getRootView().getLocationOnScreen(root);v.setClipBounds(new android.graphics.Rect(0,0,v.getWidth(),Math.max(0,bottom-(pos[1]-root[1]))));}
            return;
        }
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)foldHeaderDecoration(g.getChildAt(i),folded,allHidden,bottom,depth+1);}
    }
    private void adjustContentTop(View v,int removed,int headerBottom,boolean breathing,int depth){
        if(depth>30)return;
        String cls=v.getClass().getName();
        if(cls.equals(HostSymbols.name("com.dragon.read.widget.refresh.SuperSwipeRefreshLayout"))){
            if(!(v.getParent() instanceof View))return;
            View parent=(View)v.getParent();
            if(!parent.getClass().getName().equals(HostSymbols.name("com.dragon.read.component.biz.impl.bookmall.widge.DetectTouchEventFrameLayout")))return;
            originalParentTopPadding.putIfAbsent(parent,parent.getPaddingTop());
            ViewGroup.LayoutParams lp=v.getLayoutParams();
            if(lp instanceof ViewGroup.MarginLayoutParams){
                ViewGroup.MarginLayoutParams mp=(ViewGroup.MarginLayoutParams)lp;
                originalTopMargins.putIfAbsent(v,Math.max(0,mp.topMargin));
                int original=originalTopMargins.get(v);
                if(mp.topMargin!=original){mp.topMargin=original;v.setLayoutParams(mp);}
            }
            int desired=originalParentTopPadding.get(parent);
            if(removed>0){
                int[] parentPos=new int[2],rootPos=new int[2];parent.getLocationOnScreen(parentPos);parent.getRootView().getLocationOnScreen(rootPos);
                desired=Math.max(0,headerBottom-(parentPos[1]-rootPos[1]));
                if(breathing)desired+=Math.round(10f*v.getResources().getDisplayMetrics().density);
            }
            int before=parent.getPaddingTop();
            if(before!=desired){
                parent.setPadding(parent.getPaddingLeft(),desired,parent.getPaddingRight(),parent.getPaddingBottom());
                host.log("Bookmall ar paddingTop: before="+before+" after="+desired);
            }
            return;
        }
        if(cls.contains("RecyclerView")||cls.contains("NestedChildLayout"))return;
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)adjustContentTop(g.getChildAt(i),removed,headerBottom,breathing,depth+1);}
    }
    private int countTitles(View v){if(v.getVisibility()!=View.VISIBLE)return 0;if(v instanceof TextView){String t=((TextView)v).getText().toString().trim();return Arrays.asList(Rules.CHANNEL_LABELS).contains(t)?1:0;}int n=0;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)n+=countTitles(g.getChildAt(i));}return n;}
    private int rememberHeight(View v){Integer h=heights.get(v);if(h==null){h=v.getHeight();if(h>0)heights.put(v,h);}return h;}
    private void toggle(View v,boolean hide){visibility.putIfAbsent(v,v.getVisibility());int value=hide?View.GONE:visibility.get(v);if(v.getVisibility()!=value)v.setVisibility(value);}
    private void height(View v,int value){ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp!=null&&lp.height!=value){lp.height=value;v.setLayoutParams(lp);}}
}
