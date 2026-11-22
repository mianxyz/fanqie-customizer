package io.github.mianxyz.fanqiecustomizer;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedModule;

/** 73932 candidate. Source-only recordings for panels; PixelCopy is dialog-only.
 * No layout parameters, visibility, alpha or view dimensions are written.
 */
final class GlobalGlassSurfaces {
    static final float SCALE=.25f, LIGHT=10f, MEDIUM=20f, HEAVY=40f;
    private static GlobalGlassSurfaces singleton;
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Map<View,Surface> surfaces=new WeakHashMap<>();
    private final Set<View> rankPanels=Collections.newSetFromMap(new WeakHashMap<View,Boolean>());
    private final Map<View,TopLayer> nativeTopLayers=new WeakHashMap<>();
    private final Set<Method> nativeTopHooks=new HashSet<>();
    private final Map<View,TopPageBackground> topBackdrops=new WeakHashMap<>();
    private final ThreadLocal<Boolean> recordingTop=new ThreadLocal<>();
    private final Map<Activity,Boolean> resumed=new WeakHashMap<>();
    private final Set<String> logged=new HashSet<>();
    private final ThreadLocal<Surface> searchScope=new ThreadLocal<>();
    private final ThreadLocal<Object> searchComposition=new ThreadLocal<>();
    private final WeakIdentityTable<Object,Object> searchPainterOwners=new WeakIdentityTable<>();
    private final WeakIdentityTable<Object,Surface> searchPainterSurfaces=new WeakIdentityTable<>();
    private final WeakIdentityTable<Object,Surface> searchOwnerSurfaces=new WeakIdentityTable<>();
    private Object searchDrawVersion;private Method searchStateRead,searchStateWrite;private int searchRevision;
    private boolean installed, ticking, searchInstalled, observing;
    private boolean surfaceGuardInstalled;
    static int storeBackdropColor(Context context){GlobalGlassSurfaces g=singleton;boolean dark=g!=null?g.night(context):(context.getResources().getConfiguration().uiMode&48)==32;return dark?0xff15161b:0xfff6f6f6;}
    private void installSurfaceGuard(){if(surfaceGuardInstalled)return;try{module.hook(View.class.getDeclaredMethod("setBackgroundDrawable",Drawable.class)).intercept(chain->{View v=(View)chain.getThisObject();Surface cached=surfaces.get(v);Drawable requested=(Drawable)chain.getArg(0);if(cached!=null&&cached.rankPanel&&cached.alive&&panels()&&!rankCover()&&requested!=cached){cached.original=requested;return chain.proceed(new Object[]{cached});}return chain.proceed();});surfaceGuardInstalled=true;}catch(Throwable e){logIssue("surface background-guard "+e.getClass().getSimpleName());}}
    private Application application;
    private CardGlassHooks cards;
    private boolean lastCardEnabled;
    private final Set<View> rankLayouts=Collections.newSetFromMap(new WeakHashMap<View,Boolean>());
    private boolean rankEqual(){return host.on("enabled")&&host.on("rank_equal_columns");}
    private void restoreRankLayouts(View root){for(View panel:new ArrayList<>(rankLayouts))if(panel==null||root==null||panel.getRootView()==root){RankLayoutFix.restore(panel);rankLayouts.remove(panel);}}
    GlobalGlassSurfaces(XposedModule module,FeatureHooks.Host businessHost,ClassLoader loader){
        this.module=module;this.host=businessHost;this.loader=loader;
    }
    static GlobalGlassSurfaces instance(){return singleton;}
    boolean enabled(){return Build.VERSION.SDK_INT>=31&&host.on("enabled")&&host.on("glass_global_enabled");}
    static boolean panels(){GlobalGlassSurfaces g=singleton;return g!=null&&g.enabled()&&g.host.on("glass_global_panels");}
    boolean cardText(){return enabled()&&host.on("glass_global_card_text");}
    boolean rankCover(){return panels()&&host.on("glass_global_rank_cover");}
    void logIssue(String text){if(logged.add("issue:"+text))host.log("GlobalGlass skipped: "+text);}
    void logApplied(String kind){if(logged.add(kind))host.log("GlobalGlass applied: "+kind);}
    void install(Application app){
        application=app;singleton=this;
        if(!observing){observing=true;app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityResumed(Activity a){resumed.put(a,Boolean.TRUE);if(installed){watchDiscovery(a);queueDiscovery(a);startTick();}}
            public void onActivityPaused(Activity a){resumed.remove(a);if(resumed.isEmpty()){main.removeCallbacks(tick);ticking=false;}}
            public void onActivityDestroyed(Activity a){resumed.remove(a);Runnable task=discoveries.remove(a);if(task!=null)main.removeCallbacks(task);restoreRoot(a.getWindow().getDecorView());}
            public void onActivityCreated(Activity a,Bundle b){}public void onActivityStarted(Activity a){}public void onActivityStopped(Activity a){}public void onActivitySaveInstanceState(Activity a,Bundle b){}
        });}
        if(installed||Build.VERSION.SDK_INT<31||!host.on("enabled")||(!host.on("glass_global_enabled")&&!rankEqual()))return;
        installed=true;singleton=this;
        RankDiagnostics.install(module,loader);NativeCoverGlass.installGuard(module);installSurfaceGuard();
        host.log("GlobalGlass config(enabled="+host.on("enabled")+" global="+host.on("glass_global_enabled")+" cardText="+host.on("glass_global_card_text")+" panels="+host.on("glass_global_panels")+")");
        if(panels()){searchInstalled=true;installSearchPainter();}


        if(cardText())try{cards=new CardGlassHooks(module,loader);cards.install();}catch(Throwable error){logIssue("card hook "+error.getClass().getSimpleName());}
        for(Activity current:new ArrayList<>(resumed.keySet()))if(current!=null){watchDiscovery(current);queueDiscovery(current);}
    }
    static void pageChanged(){GlobalGlassSurfaces g=singleton;if(g==null)return;g.main.post(()->{for(Activity a:new ArrayList<>(g.resumed.keySet()))if(a!=null)g.queueDiscovery(a);});}
    static void configurationChanged(){GlobalGlassSurfaces g=singleton;if(g==null)return;g.main.post(()->{
        if(!g.installed&&g.application!=null)g.install(g.application);
        if(!g.enabled())RankEdgeGlass.restoreRoot(null);
        if(g.panels()&&!g.searchInstalled){g.searchInstalled=true;g.installSearchPainter();}
        if(g.cardText()&&g.cards==null){g.cards=new CardGlassHooks(g.module,g.loader);g.cards.install();}
        for(Activity a:new ArrayList<>(g.resumed.keySet()))if(a!=null)g.queueDiscovery(a);
        if(g.cards!=null)g.cards.invalidateAll();
        if(!panels()){g.restoreRanks(null);for(View v:new ArrayList<>(g.surfaces.keySet()))g.restore(v);}
        if(panels()&&g.rankCover()){for(Map.Entry<View,Surface> e:new ArrayList<>(g.surfaces.entrySet()))if(e.getValue().rankPanel)g.restore(e.getKey());}
        else for(Surface s:new ArrayList<>(g.surfaces.values()))s.dirty=true;
        g.startTick();
    });}
    private final Set<View> discoveryRoots=Collections.newSetFromMap(new WeakHashMap<View,Boolean>());
    private final Map<Activity,Runnable> discoveries=new WeakHashMap<>();
    private void watchDiscovery(Activity a){
        View root=a.getWindow().getDecorView();if(!discoveryRoots.add(root))return;
        WeakReference<Activity> weak=new WeakReference<>(a);
        root.getViewTreeObserver().addOnGlobalLayoutListener(()->{
            Activity current=weak.get();if(current!=null)queueDiscovery(current);
        });
        root.getViewTreeObserver().addOnScrollChangedListener(()->{for(Map.Entry<View,Surface> entry:new ArrayList<>(surfaces.entrySet()))if(entry.getKey()!=null&&entry.getKey().getRootView()==root)if(!entry.getValue().rankPanel)entry.getValue().dirty=true;});
    }
    private void queueDiscovery(Activity a){
        if(discoveries.containsKey(a))return;
        Runnable task=new Runnable(){public void run(){
            if(!resumed.containsKey(a)||a.isDestroyed()){discoveries.remove(a);return;}
            if(ScrollWorkGate.busy(a.getWindow().getDecorView())){main.postDelayed(this,200);return;}
            discoveries.remove(a);if(enabled()||rankEqual())update(a);
        }};discoveries.put(a,task);main.postDelayed(task,120);
    }
    private void startTick(){if(!ticking){ticking=true;main.post(tick);}}
    private final Runnable tick=new Runnable(){public void run(){
        if(resumed.isEmpty()){ticking=false;return;}
        try{
            boolean busy=false;for(Activity a:resumed.keySet())if(a!=null&&ScrollWorkGate.busy(a.getWindow().getDecorView())){busy=true;break;}
            if(busy){main.postDelayed(this,500);return;}
            restoreTopBackdrops(false);
            for(View layer:new ArrayList<>(nativeTopLayers.keySet()))if(layer!=null&&layer.isAttachedToWindow())layer.invalidate();
            boolean cardEnabled=cardText();if(cards!=null&&lastCardEnabled!=cardEnabled){lastCardEnabled=cardEnabled;cards.invalidateAll();}
            if(!rankEqual())restoreRankLayouts(null);

            if(!panels()){restoreRanks(null);for(View v:new ArrayList<>(surfaces.keySet()))restore(v);}
            else {

                for(View v:new ArrayList<>(surfaces.keySet())){
                    Surface s=surfaces.get(v);if(s==null)continue;
                    if(s.rankPanel&&rankCover())restore(v);
                    else if(!v.isAttachedToWindow()){if(!s.rankPanel)restore(v);}
                    else if(v.isShown()&&s.dirty&&!ScrollWorkGate.busy(v)){s.night="drama".equals(s.kind)||night(v.getContext());if(s.search)invalidateSearch(v);else v.invalidate();}
                }
            }
        }catch(Throwable e){logIssue("update "+e.getClass().getSimpleName());}
        main.postDelayed(this,500);
    }};
    private List<View> discoveryCandidates(View root){
        List<View> out=new ArrayList<>();Set<View> seen=Collections.newSetFromMap(new IdentityHashMap<>());
        for(String name:new String[]{"iyd","gat","g64","dp0","c76","fue","feq","ti","b2q","ggr","fde","h3q","e3m"}){
            View target=id(root,name);if(target==null||!target.isShown())continue;if(seen.add(target))out.add(target);
            if(target instanceof ViewGroup){ViewGroup group=(ViewGroup)target;for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child.isShown()&&seen.add(child))out.add(child);}}
            for(ViewParent parent=target.getParent();parent instanceof View;parent=parent.getParent()){View v=(View)parent;if(seen.add(v))out.add(v);if(v==root)break;}
        }return out;
    }
    private void update(Activity a){
        if(!HostSymbols.name("com.dragon.read.pages.main.MainFragmentActivity").equals(a.getClass().getName()))return;
        View root=a.getWindow().getDecorView();
        // Exact ID candidates only; no full decor traversal or feed ViewHolder walk.
        for(View v:discoveryCandidates(root)){
            if(!visibleSource(v))continue;
            String name=idName(v),cls=v.getClass().getName();
            if("iyd".equals(name)&&ancestorId(v,"gat"))attach(v,LIGHT,"bookstore",21f,false);
            if(HostSymbols.name("t14.b0").equals(cls)&&ancestorId(v,"gat")){
                Surface search=attach(v,LIGHT,"bookstore",21f,true);
                Object kmp=fieldOfExactType(v,"com.dragon.read.kmp.bookmall.search.SearchWordDisplayViewKMP");
                if(search!=null&&kmp!=null)searchOwnerSurfaces.put(kmp,search);
            }
            if("g64".equals(name)&&HostSymbols.name("androidx.compose.ui.platform.ComposeView").equals(cls)&&ancestorId(v,"ixt")&&ancestorId(v,"g5o"))
                attach(v,LIGHT,"mine",8f*dp(v),true);
            if(HostSymbols.name("com.dragon.read.component.biz.impl.bookmall.widge.BookstoreHeaderBgView").equals(cls))discoverNativeTop(v);
            if(HostSymbols.name("ke4.n0").equals(cls)&&ancestorId(v,"dp0")&&v.getParent() instanceof View&&"ti".equals(idName((View)v.getParent())))
                attach(v,MEDIUM,"mine",radius(v,8f*dp(v)),false);
            if("c76".equals(name)&&v instanceof ViewGroup&&cls.endsWith("BookShelfReadTimeBarView")&&shelfAncestor(v))
                attach(first(v),LIGHT,"shelf",first(v)==null?0f:radius(first(v),0f),false);
            if("fue".equals(name)&&v instanceof android.widget.FrameLayout&&hasId(v,"hli")){
                Surface surface=attach(v,MEDIUM,"mine",radius(v,16f*dp(v)),false);
                View hp=id(v,"hp");
                if(surface!=null&&hp!=null&&hp.getParent()==v){
                    Surface mask=attach(hp,MEDIUM,"mine",21f,false);
                    if(mask!=null){mask.sourceAnchor=new WeakReference<>(v);mask.bottomMask=true;mask.nativeRadii=new float[]{0,0,0,0,21f,21f,21f,21f};
                        if(logged.add("hp-mask:"+System.identityHashCode(hp)))host.log("GlobalGlass hp-mask source=g5r anchor=fue blurDp=20 opaque=true lowerRadiusPx=21 topStroke=false");}
                }
            }
            if("feq".equals(name)&&v instanceof ViewGroup&&ancestorId(v,"frh")&&ancestorId(v,"g5o")&&hasId(v,"he1")){
                Surface benefit=attach(v,MEDIUM,"mine",radius(v,16f*dp(v)),false);
                if(benefit!=null)clearBenefitBackdrops(benefit,v);
            }
            if("ti".equals(name)&&cls.endsWith("AnimatorContainerLayout")){
                ViewParent p=v.getParent();
                if(p instanceof View&&NativeCoverGlass.matches((View)p)){View panel=(View)p;
                    RankEdgeGlass.update(panel,enabled(),host.on("glass_rank_edge_feather"));
                    if(rankEqual()){rankLayouts.add(panel);RankLayoutFix.update(panel,true);}
                    else if(rankLayouts.remove(panel))RankLayoutFix.restore(panel);
                    if(panels()&&host.on("glass_global_rank_cover")){restore(panel);rankPanels.add(panel);NativeCoverGlass.update(panel,true,night(panel.getContext()));}
                    else {NativeCoverGlass.restore(panel);rankPanels.remove(panel);Surface cached=attach(panel,MEDIUM,"bookstore",21f,false);if(cached!=null)cached.rankPanel=true;}
                }
            }
            if("b2q".equals(name)&&v instanceof android.widget.FrameLayout&&ancestorId(v,"ixs")&&v.getParent() instanceof View&&"hlg".equals(idName((View)v.getParent()))){
                Surface search=attach(v,LIGHT,"drama",radius(v,0f),false);
                if(search!=null)clearDramaSearchBackdrops(search,v);
            }
            if("ggr".equals(name)&&v instanceof android.widget.FrameLayout&&ancestorId(v,"bottom_relate_series_container")&&ancestorId(v,"arh")){
                Surface series=attach(v,MEDIUM,"drama",radius(v,0f),false);
                if(series!=null){View firstBackdrop=id(v,"ggs"),secondBackdrop=id(v,"ggt");if(firstBackdrop!=null)series.clear(firstBackdrop);if(secondBackdrop!=null)series.clear(secondBackdrop);}
            }
            if("fde".equals(name)&&v instanceof android.widget.FrameLayout&&hasId(v,"ao_")&&shelfAncestor(v))attach(first(v),MEDIUM,"shelf",16f,false);
        }
    }
    private void clearDramaSearchBackdrops(Surface surface,View search){
        // Only full search-sized solid containers; icons/text/image drawables are foreground.
        View parent=(View)search.getParent();
        if(parent.getBackground() instanceof ColorDrawable||parent.getBackground() instanceof GradientDrawable)surface.clear(parent);
        for(View child:descendants(search))if(child!=search&&child instanceof ViewGroup
                &&child.getWidth()>=search.getWidth()*.8f&&child.getHeight()>=search.getHeight()*.8f
                &&(child.getBackground() instanceof ColorDrawable||child.getBackground() instanceof GradientDrawable))surface.clear(child);
        if(logged.add("drama-search-backdrops"))host.log("GlobalGlass drama search light-mist original="+topDrawableFact(surface.original,0)+" parent="+topDrawableFact(parent.getBackground(),0)+" cleared="+surface.cleared.size());
    }
    private boolean visibleStoreOwner(View layer){
        Rect visible=new Rect();
        for(Surface surface:new ArrayList<>(surfaces.values())){
            View owner=surface.owner.get();
            if(surface.alive&&"bookstore".equals(surface.kind)&&visibleSource(owner)&&owner.getRootView()==layer.getRootView()&&owner.getGlobalVisibleRect(visible))return true;
        }return false;
    }
    private static final String STORE_HEADER="com.dragon.read.component.biz.impl.bookmall.widge.BookstoreHeaderBgView";
    private static final class TopLayer {boolean pixels,headerTree;String signature,pendingSignature;}
    private void discoverNativeTop(View header){
        for(View child:descendants(header)){
            TopLayer state=nativeTopLayers.get(child);if(state==null){state=new TopLayer();nativeTopLayers.put(child,state);}
            state.headerTree=true;sampleNativeTop(child);
        }
        for(View candidate:descendants(header.getRootView()))if(TopRules.candidate(idName(candidate))&&visibleSource(candidate)
                &&("fa_".equals(idName(candidate))?hasId(candidate,"gat"):ancestorId(candidate,"fa_")))sampleTopBackdrop(candidate);
        if(header.getParent() instanceof ViewGroup){ViewGroup parent=(ViewGroup)header.getParent();
            for(int i=0;i<parent.getChildCount();i++){View sibling=parent.getChildAt(i);
                if("e3e".equals(idName(sibling))&&HostSymbols.name("com.facebook.drawee.view.SimpleDraweeView").equals(sibling.getClass().getName()))sampleNativeTop(sibling);
            }
        }
    }
    private void sampleNativeTop(View v){
        TopLayer state=nativeTopLayers.get(v);if(state==null){state=new TopLayer();nativeTopLayers.put(v,state);}
        hookNativeTop(v.getClass(),"draw");if(v instanceof ViewGroup)hookNativeTop(v.getClass(),"dispatchDraw");
        boolean before=state.pixels;state.pixels=sampleTopPixels(v,"native-background",true);if(before!=state.pixels)v.invalidate();
    }
    private boolean sampleTopPixels(View v,String role,boolean retained){
        if(!visibleSource(v)||v.getHeight()<=1)return false;
        Drawable image=v instanceof android.widget.ImageView?((android.widget.ImageView)v).getDrawable():null;
        String signature=v.getWidth()+"x"+v.getHeight()+":"+System.identityHashCode(v.getBackground())+":"+System.identityHashCode(image);
        TopLayer state=retained?nativeTopLayers.get(v):null;
        if(state!=null&&signature.equals(state.signature)&&state.pixels)return true;
        if(state!=null&&!signature.equals(state.pendingSignature)){state.pendingSignature=signature;return false;}
        String key="top-sample:"+System.identityHashCode(v)+":"+signature;
        if(!retained&&logged.contains(key))return false;
        Bitmap sample=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);boolean previous=Boolean.TRUE.equals(recordingTop.get());
        try{Canvas canvas=new Canvas(sample);float topHeight=Math.min(v.getHeight(),184f);canvas.scale(16f/v.getWidth(),16f/topHeight);canvas.clipRect(0,0,v.getWidth(),topHeight);
            recordingTop.set(Boolean.TRUE);v.draw(canvas);
            int[] pixels=new int[256];sample.getPixels(pixels,0,16,0,0,16,16);int opaque=0;for(int color:pixels)if(Color.alpha(color)>0)opaque++;
            if(logged.add(key)){int[] p=new int[2];v.getLocationOnScreen(p);host.log("GlobalGlass top-sample16 role="+role+" class="+v.getClass().getName()+" id="+idName(v)+" bounds="+new Rect(p[0],p[1],p[0]+v.getWidth(),p[1]+v.getHeight())+" pixels="+opaque+" rows="+Integer.toHexString(pixels[8])+","+Integer.toHexString(pixels[120])+","+Integer.toHexString(pixels[248])+" bg="+topDrawableFact(v.getBackground(),0)+" image="+topDrawableFact(image,0));}
            if(state!=null)state.signature=signature;return opaque>0;
        }catch(Throwable error){logIssue("top sample "+v.getClass().getName()+" "+error.getClass().getSimpleName());return false;}
        finally{if(previous)recordingTop.set(Boolean.TRUE);else recordingTop.remove();sample.recycle();}
    }
    private void hookNativeTop(Class<?> type,String name){
        for(Class<?> at=type;at!=null&&View.class.isAssignableFrom(at);at=at.getSuperclass())try{
            Method method;try{method=at.getDeclaredMethod(HostSymbols.methodName(at,name),Canvas.class);}catch(NoSuchMethodException ignored){continue;}
            if(!nativeTopHooks.add(method))continue;
            module.hook(method).intercept(chain->{Object target=chain.getThisObject();if(!(target instanceof View)||Boolean.TRUE.equals(recordingTop.get()))return chain.proceed();
                View view=(View)target;TopLayer state=nativeTopLayers.get(view);Rect shown=new Rect();
                boolean exact=HostSymbols.name(STORE_HEADER).equals(view.getClass().getName())||("e3e".equals(idName(view))&&HostSymbols.name("com.facebook.drawee.view.SimpleDraweeView").equals(view.getClass().getName()));
                if(state!=null&&(state.headerTree||(exact&&state.pixels))&&panels()&&visibleSource(view)&&view.getGlobalVisibleRect(shown)&&visibleStoreOwner(view)){
                    if(logged.add("top-skip:"+System.identityHashCode(view)+":"+name))host.log("GlobalGlass top-skip class="+view.getClass().getName()+" id="+idName(view)+" method="+name+" sampled="+state.pixels+" headerTree="+state.headerTree);return null;
                }return chain.proceed();});
        }catch(Throwable error){logIssue("native top "+type.getName()+"."+name+" "+error.getClass().getSimpleName());}
    }
    /** Content containers retain all children; only sampled neutral white backgrounds change. */
    static final class TopRules {
        static boolean candidate(String id){return "ik".equals(id)||"c7q".equals(id)||"gat".equals(id)||"fa_".equals(id)||"kn0".equals(id)||"ar".equals(id);}
        static boolean whitePixel(int color){int a=color>>>24,r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;return a>=240&&r>=235&&g>=235&&b>=235&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<=8;}
    }
    private void sampleTopBackdrop(View view){
        Drawable current=view.getBackground();if(current instanceof TopPageBackground)return;
        String key="top-bg:"+System.identityHashCode(view)+":"+System.identityHashCode(current)+":"+view.getWidth()+"x"+view.getHeight();
        boolean first=logged.add(key);
        Bitmap bitmap=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888);boolean white=true;int count=0;
        try{
            if(current!=null){Rect saved=new Rect(current.getBounds());try{current.setBounds(0,0,view.getWidth(),view.getHeight());Canvas canvas=new Canvas(bitmap);canvas.scale(16f/view.getWidth(),16f/Math.min(208,view.getHeight()));current.draw(canvas);}finally{current.setBounds(saved);}}
            int[] sample=new int[256];bitmap.getPixels(sample,0,16,0,0,16,16);for(int color:sample){if((color>>>24)>0)count++;if(!TopRules.whitePixel(color))white=false;}
            int[] xy=new int[2];view.getLocationOnScreen(xy);if(first)host.log("GlobalGlass top559-bg class="+view.getClass().getName()+" id="+idName(view)+" bounds="+new Rect(xy[0],xy[1],xy[0]+view.getWidth(),xy[1]+view.getHeight())+" bg="+topDrawableFact(current,0)+" pixels="+count+" rows="+Integer.toHexString(sample[8])+","+Integer.toHexString(sample[120])+","+Integer.toHexString(sample[248])+" white="+white);
            if(white&&current!=null&&panels()&&visibleStoreOwner(view)){TopPageBackground replacement=new TopPageBackground(view,current);topBackdrops.put(view,replacement);setPreservingPadding(view,replacement);view.invalidate();}
        }catch(Throwable error){logIssue("top559 background "+idName(view)+" "+error.getClass().getSimpleName());}
        finally{bitmap.recycle();}
    }
    private static String topDrawableFact(Drawable drawable,int depth){
        String fact=drawableFact(drawable);if(drawable instanceof LayerDrawable&&depth<4){LayerDrawable layers=(LayerDrawable)drawable;for(int i=0;i<layers.getNumberOfLayers();i++)fact+=" layer["+i+"]={"+topDrawableFact(layers.getDrawable(i),depth+1)+"}";}return fact;
    }
    private void restoreTopBackdrops(boolean force){
        for(Map.Entry<View,TopPageBackground> entry:new ArrayList<>(topBackdrops.entrySet())){View view=entry.getKey();TopPageBackground bg=entry.getValue();
            if(view==null||force||!panels()||(isResumedRoot(view.getRootView())&&(!view.isAttachedToWindow()||!visibleStoreOwner(view)))){if(view!=null&&view.getBackground()==bg){setPreservingPadding(view,bg.original);view.invalidate();}topBackdrops.remove(view);}
        }
    }
    private final class TopPageBackground extends Drawable {
        final WeakReference<View> owner;final Drawable original;
        TopPageBackground(View view,Drawable drawable){owner=new WeakReference<>(view);original=drawable;}
        public void draw(Canvas canvas){View view=owner.get();if(view!=null&&panels()&&visibleStoreOwner(view)){Paint paint=new Paint();paint.setColor(night(view.getContext())?0xff15161b:0xfff6f6f6);canvas.drawRect(getBounds(),paint);return;}Rect saved=new Rect(original.getBounds());try{original.setBounds(getBounds());original.draw(canvas);}finally{original.setBounds(saved);}}
        public boolean getPadding(Rect rect){return original.getPadding(rect);}public int getMinimumWidth(){return original.getMinimumWidth();}public int getMinimumHeight(){return original.getMinimumHeight();}
        public int getIntrinsicWidth(){return original.getIntrinsicWidth();}public int getIntrinsicHeight(){return original.getIntrinsicHeight();}
        public void getOutline(Outline outline){Rect saved=new Rect(original.getBounds());try{original.setBounds(getBounds());original.getOutline(outline);}finally{original.setBounds(saved);}}
        public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.OPAQUE;}
    }
    private void clearBenefitBackdrops(Surface surface,View panel){
        if(!(panel instanceof ViewGroup))return;ViewGroup group=(ViewGroup)panel;
        for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);
            if(child instanceof ViewGroup&&solidWhiteBackground(child.getBackground()))surface.clear(child);
        }
    }
    private static boolean solidWhiteBackground(Drawable drawable){
        if(drawable instanceof ColorDrawable){int color=((ColorDrawable)drawable).getColor();return Color.alpha(color)==255&&white(color);}
        if(drawable instanceof GradientDrawable){GradientDrawable gradient=(GradientDrawable)drawable;int[] colors=gradient.getColors();
            if(colors!=null){if(colors.length==0)return false;for(int color:colors)if(Color.alpha(color)!=255||!white(color)||color!=colors[0])return false;return true;}
            return gradient.getColor()!=null&&Color.alpha(gradient.getColor().getDefaultColor())==255&&white(gradient.getColor().getDefaultColor());}
        return false;
    }
    private static String drawableFact(Drawable drawable){
        if(drawable==null)return "none";String value=drawable.getClass().getName()+"@"+System.identityHashCode(drawable)+" bounds="+drawable.getBounds()+" alpha="+drawable.getAlpha();
        if(drawable instanceof ColorDrawable)value+=" color="+Integer.toHexString(((ColorDrawable)drawable).getColor());
        if(drawable instanceof GradientDrawable){GradientDrawable gradient=(GradientDrawable)drawable;value+=" colors="+Arrays.toString(gradient.getColors())+" solid="+(gradient.getColor()==null?"none":Integer.toHexString(gradient.getColor().getDefaultColor()));}
        return value;
    }
    private static boolean ancestorId(View v,String name){
        for(ViewParent p=v.getParent();p instanceof View;p=p.getParent())if(name.equals(idName((View)p)))return true;
        return false;
    }
    private static boolean hasId(View root,String name){for(View v:descendants(root))if(name.equals(idName(v)))return true;return false;}
    private Surface attach(View v,float blur,String source,float radius,boolean search){
        if(v==null||!v.isAttachedToWindow()||!v.isShown()||v.getWidth()<=0||v.getHeight()<=0)return null;
        Surface old=surfaces.get(v);if(old!=null){if(old.rankPanel){old.ensureRankBackground(v);return old;}if(old.search||v.getBackground()==old)return old;restore(v);}
        Surface s=new Surface(v,blur,source,radius,search,false);surfaces.put(v,s);
        v.addOnAttachStateChangeListener(s);
        if(!search)s.install();else invalidateSearch(v);
        return s;
    }
    private static boolean white(int c){return Color.red(c)>=235&&Color.red(c)==Color.green(c)&&Color.green(c)==Color.blue(c);}
    private void restoreRanks(View root){for(View v:new ArrayList<>(rankPanels))if(v==null||root==null||v.getRootView()==root){if(v!=null)NativeCoverGlass.restore(v);rankPanels.remove(v);}}
    // Paused windows can remain visible during transitions; another resumed root does not own their cleanup.
    private boolean isResumedRoot(View root){
        for(Activity a:new ArrayList<>(resumed.keySet()))if(a!=null&&!a.isDestroyed()&&a.getWindow().getDecorView()==root)return true;
        return false;
    }
    private void restoreRoot(View root){
        if(root==null)return;
        for(View v:new ArrayList<>(topBackdrops.keySet()))if(v!=null&&v.getRootView()==root){
            TopPageBackground bg=topBackdrops.remove(v);
            if(bg!=null&&v.getBackground()==bg){setPreservingPadding(v,bg.original);v.invalidate();}
        }
        restoreRankLayouts(root);
        restoreRanks(root);RankEdgeGlass.restoreRoot(root);
        for(View v:new ArrayList<>(surfaces.keySet()))if(v!=null&&(v.getRootView()==root||surfaces.get(v).root.get()==root))restore(v);
        for(View v:new ArrayList<>(nativeTopLayers.keySet()))if(v!=null&&v.getRootView()==root){nativeTopLayers.remove(v);v.invalidate();}
    }
    private void restore(View v){
        Surface s=surfaces.remove(v);if(s==null)return;s.alive=false;
        if(v!=null){v.removeOnAttachStateChangeListener(s);if(!s.search&&v.getBackground()==s)setPreservingPadding(v,s.original);if(s.search)invalidateSearch(v);else v.invalidate();}
        s.release();
    }
    /** Matches old background padding, while preserving the actual current View padding. */
    private static void setPreservingPadding(View v,Drawable background){
        int l=v.getPaddingLeft(),t=v.getPaddingTop(),r=v.getPaddingRight(),b=v.getPaddingBottom();
        boolean relative=v.isPaddingRelative();int start=v.getPaddingStart(),end=v.getPaddingEnd();
        v.setBackground(background);
        if(l!=v.getPaddingLeft()||t!=v.getPaddingTop()||r!=v.getPaddingRight()||b!=v.getPaddingBottom()){
            if(relative)v.setPaddingRelative(start,t,end,b);else v.setPadding(l,t,r,b);
        }
    }
    private static float dp(View v){return v.getResources().getDisplayMetrics().density;}
    private static float radius(View v,float fallback){Drawable d=v.getBackground();return d instanceof GradientDrawable?((GradientDrawable)d).getCornerRadius():fallback;}
    private static View first(View v){return v instanceof ViewGroup&&((ViewGroup)v).getChildCount()>0?((ViewGroup)v).getChildAt(0):null;}
    private static View id(View root,String name){int n=root.getResources().getIdentifier(HostSymbols.resourceName(name),"id",root.getContext().getPackageName());return n==0?null:root.findViewById(n);}
    private static String idName(View v){try{return v.getId()==View.NO_ID?"":HostSymbols.canonicalResource(v.getResources().getResourceEntryName(v.getId()));}catch(Throwable e){return "";}}
    private static boolean shelfAncestor(View v){
        for(ViewParent p=v.getParent();p instanceof View;p=p.getParent()){
            String name=p.getClass().getName().toLowerCase(Locale.ROOT),id=idName((View)p);
            if(name.contains("bookshelf")||"e3m".equals(id)||"h3q".equals(id))return true;
        }return false;
    }
    // Walking discovers identity only. None of these containers is recorded or drawn.
    private static List<View> descendants(View root){
        List<View> out=new ArrayList<>();ArrayDeque<View> q=new ArrayDeque<>();q.add(root);
        while(!q.isEmpty()){View v=q.removeFirst();out.add(v);if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)q.addLast(g.getChildAt(i));}}return out;
    }
    private View pureSource(View owner,String kind){
        // Ascend the owner branch; never record a pager/content container. Only exact
        // background classes from 73932 metadata can be sources, even when ViewGroup.
        View branch=owner;
        for(ViewParent p=branch.getParent();p instanceof ViewGroup;p=branch.getParent()){
            ViewGroup parent=(ViewGroup)p;int branchIndex=parent.indexOfChild(branch);
            List<View> before=new ArrayList<>();
            for(int i=0;i<parent.getChildCount();i++){
                View sibling=parent.getChildAt(i);
                if(sibling!=branch&&SourceRules.before(sibling.getZ(),branch.getZ(),i,branchIndex))before.add(sibling);
            }
            // Closest preceding draw layer wins; equal-Z layers follow child order.
            Collections.sort(before,(left,right)->{int z=Float.compare(right.getZ(),left.getZ());return z!=0?z:Integer.compare(parent.indexOfChild(right),parent.indexOfChild(left));});
            for(View sibling:before)if(visibleSource(sibling)&&pureBackground(sibling,kind)&&overlaps(owner,sibling))return sibling;
            branch=parent;
        }
        return null;
    }
    private static boolean pureBackground(View v,String kind){return SourceRules.pure(kind,v.getClass().getName(),idName(v));}
    /** Android-free rules are used by production selection and standalone JVM regression. */
    static final class SourceRules {
        static boolean before(float siblingZ,float branchZ,int siblingIndex,int branchIndex){
            return Float.isFinite(siblingZ)&&Float.isFinite(branchZ)&&(siblingZ<branchZ||(Float.compare(siblingZ,branchZ)==0&&siblingIndex<branchIndex));
        }
        static boolean visible(boolean shown,float alpha,int width,int height){return shown&&alpha>0&&width>0&&height>0;}
        static boolean pure(String kind,String cls,String name){
            if("bookstore".equals(kind))return HostSymbols.name("com.dragon.read.component.biz.impl.bookmall.widge.BookstoreHeaderBgView").equals(cls);
            if("mine".equals(kind))return "g5r".equals(name)&&HostSymbols.name("com.facebook.drawee.view.SimpleDraweeView").equals(cls);
            return "shelf".equals(kind)&&(("e3m".equals(name)&&HostSymbols.name("com.facebook.drawee.view.SimpleDraweeView").equals(cls))||("h3q".equals(name)&&HostSymbols.name("com.dragon.read.component.biz.impl.bookshelf.recommend.BsRecommendBgView").equals(cls)));
        }
    }
    private static boolean overlaps(View a,View b){int[] x=new int[2],y=new int[2];a.getLocationOnScreen(x);b.getLocationOnScreen(y);return x[0]<y[0]+b.getWidth()&&y[0]<x[0]+a.getWidth()&&x[1]<y[1]+b.getHeight()&&y[1]<x[1]+a.getHeight();}
    private void logSource(View source,String kind){
        if(source==null||!logged.add("source:"+kind+":"+source.getClass().getName()+":"+idName(source)))return;
        int[] p=new int[2];source.getLocationOnScreen(p);
        host.log("GlobalGlass source recorded: kind="+kind+" class="+source.getClass().getName()+" id="+idName(source)+" alpha="+source.getAlpha()+" bounds="+new Rect(p[0],p[1],p[0]+source.getWidth(),p[1]+source.getHeight()));
    }
    private static boolean visibleSource(View v){return v!=null&&SourceRules.visible(v.isShown(),v.getAlpha(),v.getWidth(),v.getHeight());}
    private Method publicMethod(String owner,String name,Class<?>... parameters)throws Exception{Class<?> type=HostSymbols.type(owner,loader);return type.getMethod(HostSymbols.publicMethodName(type,name,parameters),parameters);}
    private boolean night(Context c){try{return Boolean.TRUE.equals(publicMethod("com.dragon.read.base.skin.SkinManager","isNightMode").invoke(null));}catch(Throwable e){return (c.getResources().getConfiguration().uiMode&48)==32;}}
    /** Shared pure-source bridge: caller owns Canvas/DrawScope clipping and actual dimensions. */
    void drawPureSource(Canvas canvas,View owner,String kind,float width,float height){recordPureSource(canvas,owner,kind,width,height);}
    private View recordPureSource(Canvas canvas,View owner,String kind,float width,float height){
        Surface surface=surfaces.get(owner);View anchor=surface==null||surface.sourceAnchor==null?null:surface.sourceAnchor.get();
        View source=pureSource(anchor==null?owner:anchor,kind);canvas.drawColor(night(owner.getContext())?0xff15161b:0xfff6f6f6);
        if(source==null||source==owner||!source.isShown()||source.getAlpha()<=0||source.getWidth()<=0||source.getHeight()<=0)return null;
        TopLayer top=nativeTopLayers.get(source);if("bookstore".equals(kind)&&panels()&&top!=null&&(top.headerTree||top.pixels))return null;
        int[] src=new int[2],dst=new int[2];source.getLocationOnScreen(src);owner.getLocationOnScreen(dst);
        int save=canvas.save();canvas.clipRect(0,0,width,height);canvas.translate(src[0]-dst[0],src[1]-dst[1]);
        int layer=canvas.saveLayerAlpha(0,0,source.getWidth(),source.getHeight(),Math.round(255*source.getAlpha()));
        boolean previous=Boolean.TRUE.equals(recordingTop.get());recordingTop.set(Boolean.TRUE);
        try{source.draw(canvas);}finally{if(previous)recordingTop.set(Boolean.TRUE);else recordingTop.remove();canvas.restoreToCount(layer);canvas.restoreToCount(save);}
        return source;
    }
    static void decorateDialog(Activity activity,View card){
        GlobalGlassSurfaces g=singleton;if(g==null||activity==null||card==null)return;
        // Layout and window attachment must settle before computing the screen rectangle.
        WeakReference<Activity> a=new WeakReference<>(activity);WeakReference<View> v=new WeakReference<>(card);
        card.post(()->{Activity target=a.get();View view=v.get();if(target!=null&&view!=null)g.captureDialog(target,view);});
    }
    private void captureDialog(Activity a,View card){
        if(!panels()||a.isDestroyed()||!card.isAttachedToWindow()||card.getWidth()<=0||card.getHeight()<=0||surfaces.containsKey(card))return;
        Window window=a.getWindow();View decor=window.getDecorView();
        if(!decor.isAttachedToWindow()||card.getWindowToken()==decor.getWindowToken())return;
        int[] p=new int[2],base=new int[2];card.getLocationInWindow(p);
        int[] screen=new int[2];card.getLocationOnScreen(screen);decor.getLocationOnScreen(base);
        // Window PixelCopy rect is in Window coordinates: include decor's in-window inset.
        decor.getLocationInWindow(p);
        Rect rect=new Rect(screen[0]-base[0]+p[0],screen[1]-base[1]+p[1],screen[0]-base[0]+p[0]+card.getWidth(),screen[1]-base[1]+p[1]+card.getHeight());
        Rect valid=new Rect(p[0],p[1],p[0]+decor.getWidth(),p[1]+decor.getHeight());
        // Do not distort partially off-window cards by copying a different aspect/region.
        if(!valid.contains(rect))return;
        Surface s=new Surface(card,HEAVY,"dialog",radius(card,24f*dp(card)),false,true);
        surfaces.put(card,s);card.addOnAttachStateChangeListener(s);
        Bitmap bitmap=Bitmap.createBitmap(Math.max(1,Math.round(rect.width()*SCALE)),Math.max(1,Math.round(rect.height()*SCALE)),Bitmap.Config.ARGB_8888);
        final Object token=card.getWindowToken();final int copiedWidth=card.getWidth(),copiedHeight=card.getHeight(),copiedX=screen[0],copiedY=screen[1];WeakReference<View> weak=new WeakReference<>(card);WeakReference<Activity> weakActivity=new WeakReference<>(a);
        try{PixelCopy.request(window,rect,bitmap,result->{
            View v=weak.get();Activity activity=weakActivity.get();int[] current=new int[2];if(v!=null)v.getLocationOnScreen(current);
            if(v!=null&&(v.getWidth()!=copiedWidth||v.getHeight()!=copiedHeight||current[0]!=copiedX||current[1]!=copiedY)){bitmap.recycle();if(surfaces.get(v)==s)restore(v);return;}
            if(result!=PixelCopy.SUCCESS||v==null||activity==null||activity.isDestroyed()||!panels()||!s.alive||!v.isAttachedToWindow()||v.getWindowToken()!=token||surfaces.get(v)!=s){bitmap.recycle();if(v!=null&&surfaces.get(v)==s)restore(v);return;}
            s.bitmap=bitmap;s.install();v.invalidate();
        },main);}catch(Throwable e){bitmap.recycle();restore(card);logIssue("dialog copy "+e.getClass().getSimpleName());}
    }
    private void invalidateSearch(View root){
        // Reads in wi3.e.j subscribe the local Compose draw observation, even while disabled.
        if(searchDrawVersion!=null)try{searchStateWrite.invoke(searchDrawVersion,++searchRevision);}catch(Throwable e){logIssue("search state invalidate "+e);}
        for(View v:descendants(root))v.invalidate();
    }
    private static Object fieldOfExactType(Object object,String type){
        if(object==null)return null;
        for(Class<?> cls=object.getClass();cls!=null;cls=cls.getSuperclass())for(Field field:cls.getDeclaredFields()){
            if(Modifier.isStatic(field.getModifiers())||!HostSymbols.name(type).equals(field.getType().getName()))continue;
            try{field.setAccessible(true);return field.get(object);}catch(Throwable ignored){}
        }return null;
    }
    /** Identity, not Painter.equals(): no key or value retains a Compose/view tree. */
    private static final class WeakIdentityTable<K,V>{
        final List<WeakReference<K>> keys=new ArrayList<>();final List<WeakReference<V>> values=new ArrayList<>();
        synchronized V get(K key){V found=null;for(int i=keys.size()-1;i>=0;i--){K k=keys.get(i).get();V v=values.get(i).get();if(k==null||v==null){keys.remove(i);values.remove(i);}else if(k==key)found=v;}return found;}
        synchronized void put(K key,V value){if(key==null||value==null)return;for(int i=keys.size()-1;i>=0;i--){K k=keys.get(i).get();if(k==null||values.get(i).get()==null||k==key){keys.remove(i);values.remove(i);}}keys.add(new WeakReference<>(key));values.add(new WeakReference<>(value));}
    }
    private Surface scopedSearch(Object object){
        if(!(object instanceof View))return null;
        for(View v=(View)object;v!=null;){Surface s=surfaces.get(v);if(s!=null&&s.search)return s;ViewParent p=v.getParent();v=p instanceof View?(View)p:null;}return null;
    }
    private boolean searchSymbolPreflight(){if(HostSymbols.isBaseline())return true;try{Class<?> painter=HostSymbols.type("wi3.e",loader);painter.getDeclaredField(HostSymbols.fieldName(painter,"f"));painter.getDeclaredField(HostSymbols.fieldName(painter,"g"));Class<?> base=HostSymbols.type("androidx.compose.ui.graphics.painter.Painter",loader);painter.getDeclaredConstructor(base,base,float.class);publicMethod("androidx.compose.runtime.SnapshotStateKt","mutableStateOf$default",Object.class,HostSymbols.type("androidx.compose.runtime.SnapshotMutationPolicy",loader),int.class,Object.class);return true;}catch(Throwable error){logIssue("search symbols unavailable "+error.getClass().getSimpleName());return false;}}
    private void installSearchPainter(){
        try{
            Class<?> state=HostSymbols.type("androidx.compose.runtime.State",loader),mutable=HostSymbols.type("androidx.compose.runtime.MutableState",loader);
            Method factory=publicMethod("androidx.compose.runtime.SnapshotStateKt","mutableStateOf$default",Object.class,HostSymbols.type("androidx.compose.runtime.SnapshotMutationPolicy",loader),int.class,Object.class);
            searchDrawVersion=factory.invoke(null,0,null,2,null);searchStateRead=state.getMethod(HostSymbols.publicMethodName(state,"getValue"));searchStateWrite=mutable.getMethod(HostSymbols.publicMethodName(mutable,"setValue",Object.class),Object.class);
        }catch(Throwable e){logIssue("search snapshot state "+e);}
        try{
            Class<?> lambda=HostSymbols.type("com.dragon.read.kmp.bookmall.search.SearchWordDisplayViewKMP$b",loader);
            for(Method invoke:lambda.getDeclaredMethods())if(HostSymbols.matches(invoke,"invoke")&&invoke.getParameterCount()==2&&!invoke.isBridge()){
                module.hook(invoke).intercept(chain->{Object previous=searchComposition.get(),owner=fieldOfExactType(chain.getThisObject(),"com.dragon.read.kmp.bookmall.search.SearchWordDisplayViewKMP");
                    if(owner!=null)searchComposition.set(owner);try{return chain.proceed();}finally{if(previous==null)searchComposition.remove();else searchComposition.set(previous);}});
            }
        }catch(Throwable e){logIssue("search composition SearchWordDisplayViewKMP$b.invoke "+e);}
        try{
            Class<?> compose=HostSymbols.type("androidx.compose.ui.platform.AndroidComposeView",loader);
            Method dispatch=compose.getDeclaredMethod(HostSymbols.methodName(compose,"dispatchDraw"),Canvas.class);
            module.hook(dispatch).intercept(chain->{Surface s=scopedSearch(chain.getThisObject()),previous=searchScope.get();if(s!=null&&panels())searchScope.set(s);try{return chain.proceed();}finally{if(previous==null)searchScope.remove();else searchScope.set(previous);}});
        }catch(Throwable e){logIssue("search hook androidx.compose.ui.platform.AndroidComposeView.dispatchDraw(android.graphics.Canvas): "+e);}
        try{
            if(!searchSymbolPreflight())return;
        if(!HostSymbols.requireCapability("GlobalGlassSurfaces.searchPainter",new String[]{"androidx.compose.runtime.MutableState","androidx.compose.runtime.SnapshotMutationPolicy","androidx.compose.runtime.SnapshotStateKt","androidx.compose.runtime.State","androidx.compose.ui.graphics.c","androidx.compose.ui.graphics.g0","androidx.compose.ui.graphics.painter.Painter","androidx.compose.ui.graphics.painter.c","androidx.compose.ui.platform.AndroidComposeView","f0.a$b","f0.h","wi3.e"},new String[]{"androidx.compose.runtime.MutableState#setValue(Ljava/lang/Object;)V","androidx.compose.runtime.State#getValue()Ljava/lang/Object;","androidx.compose.ui.graphics.c#a(Landroidx/compose/ui/graphics/g0;)Landroid/graphics/Canvas;","androidx.compose.ui.graphics.painter.c#j(Lf0/h;)V","androidx.compose.ui.platform.AndroidComposeView#dispatchDraw(Landroid/graphics/Canvas;)V","f0.a$b#a()Landroidx/compose/ui/graphics/g0;","f0.h#U0()Lf0/a$b;","f0.h#c()J","wi3.e#j(Lf0/h;)V"},new String[]{"wi3.e#f:Landroidx/compose/ui/graphics/painter/Painter;","wi3.e#g:Landroidx/compose/ui/graphics/painter/Painter;"},new String[]{}))return;
            Class<?> painter=HostSymbols.type("wi3.e",loader),color=HostSymbols.type("androidx.compose.ui.graphics.painter.c",loader),scope=HostSymbols.type("f0.h",loader);
            Field f=painter.getDeclaredField(HostSymbols.fieldName(painter,"f")),g=painter.getDeclaredField(HostSymbols.fieldName(painter,"g"));f.setAccessible(true);g.setAccessible(true);
            Class<?> base=HostSymbols.type("androidx.compose.ui.graphics.painter.Painter",loader);
            Constructor<?> constructor=painter.getDeclaredConstructor(base,base,float.class);
            module.hook(constructor).intercept(chain->{Object result=chain.proceed(),owner=searchComposition.get();
                try{Object first=f.get(chain.getThisObject()),second=g.get(chain.getThisObject());
                    if(owner!=null&&first!=null&&second!=null&&first.getClass()==color&&second.getClass()==color){searchPainterOwners.put(chain.getThisObject(),owner);logApplied("search painter weak-identity bound exact ColorPainter");}
                }catch(Throwable e){logIssue("search painter bind "+e);}return result;});
            Method getContext=scope.getMethod(HostSymbols.publicMethodName(scope,"U0")),size=scope.getMethod(HostSymbols.publicMethodName(scope,"c"));
            Method getCanvas=publicMethod("f0.a$b","a");
            Method nativeCanvas=publicMethod("androidx.compose.ui.graphics.c","a",HostSymbols.type("androidx.compose.ui.graphics.g0",loader));
            // Mine g64 has a direct solid ColorPainter background. Scope establishes identity
            // once; full search-rectangle dimensions exclude small icon painters.
            try{
            Method solidPaint=color.getDeclaredMethod(HostSymbols.methodName(color,"j"),scope);
            module.hook(solidPaint).intercept(chain->{
                if(chain.getThisObject().getClass()!=color)return chain.proceed();
                Surface bound=searchPainterSurfaces.get(chain.getThisObject()),candidate=searchScope.get();
                Surface s=bound!=null&&bound.alive?bound:candidate;
                if(s==null||!"mine".equals(s.kind)||!s.search)return chain.proceed();
                try{
                    if(searchDrawVersion!=null)searchStateRead.invoke(searchDrawVersion);
                    if(!panels()||!s.alive)return chain.proceed();
                    View owner=s.owner.get();Object ds=chain.getArg(0);long packed=((Number)size.invoke(ds)).longValue();
                    float w=Float.intBitsToFloat((int)(packed>>>32)),h=Float.intBitsToFloat((int)packed);
                    if(owner==null||!Float.isFinite(w)||!Float.isFinite(h)||w<owner.getWidth()*.8f||h<owner.getHeight()*.8f||w>owner.getWidth()*1.1f||h>owner.getHeight()*1.1f)return chain.proceed();
                    Canvas canvas=(Canvas)nativeCanvas.invoke(null,getCanvas.invoke(getContext.invoke(ds)));
                    if(canvas==null||!canvas.isHardwareAccelerated())return chain.proceed();
                    searchPainterSurfaces.put(chain.getThisObject(),s);int save=canvas.save();try{s.paint(canvas,new RectF(0,0,w,h));}finally{canvas.restoreToCount(save);}return null;
                }catch(Throwable e){logIssue("mine search ColorPainter draw "+e);return chain.proceed();}
            });
            }catch(Throwable e){logIssue("mine search ColorPainter hook "+e);}
            Method paint=painter.getDeclaredMethod(HostSymbols.methodName(painter,"j"),scope);
            module.hook(paint).intercept(chain->{
                Object kmp=searchPainterOwners.get(chain.getThisObject());
                Surface bound=kmp==null?searchPainterSurfaces.get(chain.getThisObject()):searchOwnerSurfaces.get(kmp),s=bound!=null&&bound.alive?bound:searchScope.get();
                if(kmp!=null||s!=null)try{if(searchDrawVersion!=null)searchStateRead.invoke(searchDrawVersion);}catch(Throwable e){logIssue("search state read "+e);}
                if(s==null||!panels()||!s.alive)return chain.proceed();
                try{
                    Object first=f.get(chain.getThisObject()),second=g.get(chain.getThisObject());
                    if(first==null||second==null||first.getClass()!=color||second.getClass()!=color)return chain.proceed();
                    Object ds=chain.getArg(0);Canvas canvas=(Canvas)nativeCanvas.invoke(null,getCanvas.invoke(getContext.invoke(ds)));
                    long packed=((Number)size.invoke(ds)).longValue();float w=Float.intBitsToFloat((int)(packed>>>32)),h=Float.intBitsToFloat((int)packed);
                    if(canvas==null||!canvas.isHardwareAccelerated()||!Float.isFinite(w)||!Float.isFinite(h)||w<=0||h<=0)return chain.proceed();
                    searchPainterSurfaces.put(chain.getThisObject(),s);s.paint(canvas,new RectF(0,0,w,h));return null;
                }catch(Throwable e){logIssue("search draw wi3.e.j(f0.h) [wi3.e.f/g; f0.h.U0()/c(); f0.a$b.a(); androidx.compose.ui.graphics.c.a(g0)]: "+e);return chain.proceed();}
            });
        }catch(Throwable e){logIssue("search hook wi3.e.j(f0.h) [wi3.e.f/g; f0.h.U0()/c(); f0.a$b.a(); androidx.compose.ui.graphics.c.a(g0)]: "+e);}
    }
    private static final class TransparentBackground extends Drawable {
        final Drawable original;TransparentBackground(Drawable d){original=d;}
        public void draw(Canvas c){}public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        public void getOutline(Outline o){Rect before=new Rect(original.getBounds());try{original.setBounds(getBounds());original.getOutline(o);}finally{original.setBounds(before);}}
        public boolean getPadding(Rect r){return original.getPadding(r);}public int getMinimumWidth(){return original.getMinimumWidth();}public int getMinimumHeight(){return original.getMinimumHeight();}public int getIntrinsicWidth(){return original.getIntrinsicWidth();}public int getIntrinsicHeight(){return original.getIntrinsicHeight();}
    }
    private final class Surface extends Drawable implements View.OnAttachStateChangeListener {
        final WeakReference<View> owner,root;
        Drawable original;boolean rankPanel;
        final Rect backgroundPadding=new Rect();final boolean paddingResult;
        float[] nativeRadii;boolean bottomMask;WeakReference<View> sourceAnchor;
        final float blur,radius;final String kind;final boolean search,dialog;
        final Map<View,Drawable> cleared=new WeakHashMap<>();
        final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
        boolean alive=true,night,dirty=true,liquidActive;RenderNode node;Bitmap bitmap;int width,height;
        final LiquidGlassRenderer liquid=new LiquidGlassRenderer(message->host.log("GlobalGlass "+message));
        boolean wantsLiquid(View v){
            if(dialog||!panels()||Build.VERSION.SDK_INT<33)return false;
            if("bookstore".equals(kind))return host.on("glass_global_store_liquid")&&(search||"iyd".equals(idName(v)));
            return "mine".equals(kind)&&host.on("glass_global_mine_liquid");
        }
        Surface(View v,float blur,String kind,float radius,boolean search,boolean dialog){
            owner=new WeakReference<>(v);root=new WeakReference<>(v.getRootView());original=v.getBackground();this.blur=blur;this.kind=kind;this.radius=radius;this.search=search;this.dialog=dialog;
            paddingResult=original!=null&&original.getPadding(backgroundPadding);night="drama".equals(kind)||night(v.getContext());
            boolean c76=v.getParent() instanceof View&&"c76".equals(idName((View)v.getParent()));
            nativeRadii=c76&&original instanceof GradientDrawable?((GradientDrawable)original).getCornerRadii():null;
            if(c76&&v.getParent() instanceof View){View parent=(View)v.getParent();Outline inherited=new Outline();Rect inheritedRect=new Rect();if(parent.getOutlineProvider()!=null)parent.getOutlineProvider().getOutline(parent,inherited);if(!inherited.isEmpty()&&inherited.getRect(inheritedRect)&&inherited.getRadius()>0){float inheritedRadius=inherited.getRadius();nativeRadii=new float[]{inheritedRadius,inheritedRadius,inheritedRadius,inheritedRadius,inheritedRadius,inheritedRadius,inheritedRadius,inheritedRadius};host.log("GlobalGlass c76 parent-outline rect="+inheritedRect+" radius="+inheritedRadius+" child="+v.getWidth()+"x"+v.getHeight());}}
            if(c76&&logged.add("native-shape:c76")){
                Outline outline=new Outline();if(original!=null)original.getOutline(outline);Rect rect=new Rect();boolean rounded=outline.getRect(rect);
                StringBuilder chain=new StringBuilder();for(View at=v;at!=null;){chain.append(at.getClass().getName()).append('#').append(idName(at)).append(" clip=").append(at.getClipToOutline()).append(" -> ");ViewParent parent=at.getParent();at=parent instanceof View?(View)parent:null;}
                host.log("GlobalGlass native c76 radius="+radius+" cornerRadii="+Arrays.toString(nativeRadii)+" outlineRect="+rounded+":"+rect+" outlineRadius="+outline.getRadius()+" outlineEmpty="+outline.isEmpty()+" clipToOutline="+v.getClipToOutline()+" outlineProvider="+v.getOutlineProvider()+" parents="+chain);
            }
        }
        void install(){View v=owner.get();if(v!=null&&alive)setPreservingPadding(v,this);}
        void clear(View child){if(cleared.containsKey(child)||child.getBackground()==null)return;Drawable bg=child.getBackground();cleared.put(child,bg);setPreservingPadding(child,new TransparentBackground(bg));}
        void releaseNode(){if(node!=null){node.discardDisplayList();node=null;}liquid.reset();}
        void release(){releaseNode();if(bitmap!=null&&!bitmap.isRecycled())bitmap.recycle();bitmap=null;
            for(Map.Entry<View,Drawable> entry:new ArrayList<>(cleared.entrySet()))if(entry.getKey()!=null&&entry.getKey().getBackground() instanceof TransparentBackground&&((TransparentBackground)entry.getKey().getBackground()).original==entry.getValue())setPreservingPadding(entry.getKey(),entry.getValue());cleared.clear();}
        void ensureRankBackground(View v){if(!alive||!rankPanel)return;if(!panels()||rankCover()){restore(v);return;}if(v.getBackground()!=this){original=v.getBackground();install();}v.invalidate();}
        void prepareRankNode(View v){if(!rankPanel||!alive||!panels()||rankCover())return;boolean recovered=node!=null&&!node.hasDisplayList();if(node==null){width=Math.max(1,Math.round(v.getWidth()*SCALE));height=Math.max(1,Math.round(v.getHeight()*SCALE));node=new RenderNode("PulpPureGlass-"+kind);node.setPosition(0,0,width,height);node.setClipToBounds(true);}if(!node.hasDisplayList()){RecordingCanvas rc=node.beginRecording(width,height);try{rc.drawColor(storeBackdropColor(v.getContext()));}finally{node.endRecording();}float sigma=blur*dp(v)*SCALE;node.setRenderEffect(RenderEffect.createBlurEffect(sigma,sigma,Shader.TileMode.CLAMP));dirty=true;}host.log("GlobalGlass rank attach cached=true node="+node.hasDisplayList()+" recovered="+recovered+" identity="+System.identityHashCode(node));}
        public void onViewAttachedToWindow(View v){if(rankPanel){ensureRankBackground(v);prepareRankNode(v);}}
        public void onViewDetachedFromWindow(View v){if(!rankPanel)restore(v);}
        public boolean getPadding(Rect out){out.set(backgroundPadding);return paddingResult;}
        public int getMinimumWidth(){return original==null?0:original.getMinimumWidth();}
        public int getMinimumHeight(){return original==null?0:original.getMinimumHeight();}
        public int getIntrinsicWidth(){return original==null?-1:original.getIntrinsicWidth();}
        public int getIntrinsicHeight(){return original==null?-1:original.getIntrinsicHeight();}
        public void getOutline(Outline outline){
            if(original==null){outline.setEmpty();return;}Rect before=new Rect(original.getBounds());
            try{original.setBounds(getBounds());original.getOutline(outline);}finally{original.setBounds(before);}
        }
        private void drawOriginal(Canvas canvas){
            if(original==null)return;Rect before=new Rect(original.getBounds());
            try{original.setBounds(getBounds());original.draw(canvas);}finally{original.setBounds(before);}
        }
        public void draw(Canvas canvas){
            View v=owner.get();if(v==null)return;
            if(!panels()||!alive||!canvas.isHardwareAccelerated()){drawOriginal(canvas);return;}
            int saved=canvas.save();boolean failed=false;
            try{paint(canvas,new RectF(getBounds()));}
            catch(Throwable error){failed=true;logIssue("native draw "+error.getClass().getSimpleName());}
            finally{canvas.restoreToCount(saved);}
            if(failed){drawOriginal(canvas);WeakReference<View> weak=owner;
                main.post(()->{View view=weak.get();if(view!=null&&surfaces.get(view)==this)restore(view);});}
        }
        private Path shape(RectF area,float inset){
            Path p=new Path();if(nativeRadii!=null&&nativeRadii.length==8){float[] r=nativeRadii.clone();for(int i=0;i<r.length;i++)r[i]=Math.max(0,r[i]-inset);p.addRoundRect(area,r,Path.Direction.CW);}
            else p.addRoundRect(area,Math.max(0,radius-inset),Math.max(0,radius-inset),Path.Direction.CW);return p;
        }
        private Path maskEdges(RectF area,float inset){
            float r=Math.max(0,Math.min(radius-inset,Math.min(area.width(),area.height())/2));Path p=new Path();
            p.moveTo(area.left,area.top);p.lineTo(area.left,area.bottom-r);p.quadTo(area.left,area.bottom,area.left+r,area.bottom);
            p.lineTo(area.right-r,area.bottom);p.quadTo(area.right,area.bottom,area.right,area.bottom-r);p.lineTo(area.right,area.top);return p;
        }
        private void recordDrama(Canvas c,float w,float h){
            // Background Drawable only. Never View.draw, Surface/Texture, parent or window capture.
            View ownerView=owner.get();boolean series=ownerView!=null&&"ggr".equals(idName(ownerView));
            if(series){
                c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
                if(logged.add("drama-source:ggr"))host.log("GlobalGlass drama source=transparent-layer id=ggr originalSuppressed=true videoSampling=false pixelCopy=false");
                return;
            }
            if(blur==LIGHT){
                c.drawColor(Color.TRANSPARENT,PorterDuff.Mode.CLEAR);
                if(logged.add("drama-source:b2q:light"))host.log("GlobalGlass drama source=transparent-layer id=b2q originalSuppressed=true blurDp=10 videoSampling=false pixelCopy=false");
                return;
            }
            c.drawColor(night?0xff15161b:0xfff6f6f6);
            if(original!=null){Rect before=new Rect(original.getBounds());try{original.setBounds(0,0,Math.round(w),Math.round(h));original.draw(c);}finally{original.setBounds(before);}}
            View v=owner.get();if(v!=null&&logged.add("drama-source:"+idName(v)))host.log("GlobalGlass drama source=background-drawable-only id="+idName(v)+" drawable="+(original==null?"theme-solid-fallback":original.getClass().getName())+" dark="+night+" blurDp="+blur+" videoSampling=false pixelCopy=false");
        }
        void paint(Canvas canvas,RectF bounds){
            View v=owner.get();if(v==null||bounds.width()<=0||bounds.height()<=0)return;
            View anchor=sourceAnchor==null?null:sourceAnchor.get();View recordOwner=bottomMask&&anchor!=null?anchor:v;
            float recordWidth=recordOwner==v?bounds.width():recordOwner.getWidth(),recordHeight=recordOwner==v?bounds.height():recordOwner.getHeight();
            int w=Math.max(1,Math.round(recordWidth*SCALE)),h=Math.max(1,Math.round(recordHeight*SCALE));
            if((node==null||w!=width||h!=height)&&ScrollWorkGate.busy(v)&&!(rankPanel&&node!=null&&node.hasDisplayList())){drawOriginal(canvas);return;}
            if(node==null||((w!=width||h!=height)&&!ScrollWorkGate.busy(v))){releaseNode();width=w;height=h;node=new RenderNode("PulpPureGlass-"+kind);node.setPosition(0,0,w,h);node.setClipToBounds(true);dirty=true;}
            if(rankPanel&&node!=null&&!node.hasDisplayList())prepareRankNode(v);
            if(rankPanel&&!ScrollWorkGate.busy(v)){boolean dark=night(v.getContext());if(night!=dark){night=dark;dirty=true;}}
            if(dirty&&!ScrollWorkGate.busy(v)){
                float pixels=blur*dp(v)*SCALE;
                Surface parentSurface=surfaces.get(recordOwner);
                float opticalRadius=recordOwner==v?radius:parentSurface==null?radius(recordOwner,16f*dp(v)):parentSurface.radius;
                liquidActive=wantsLiquid(v)&&liquid.apply(node,w,h,opticalRadius*SCALE,dp(v)*SCALE,1,35,night,false,false);
                if(!liquidActive)node.setRenderEffect(RenderEffect.createBlurEffect(pixels,pixels,Shader.TileMode.CLAMP));
                RecordingCanvas recording=node.beginRecording(w,h);View recordedSource=null;
                try{if(dialog&&bitmap!=null)recording.drawBitmap(bitmap,null,new Rect(0,0,w,h),brush);
                    else {recording.scale(SCALE,SCALE);if("drama".equals(kind))recordDrama(recording,bounds.width(),bounds.height());else recordedSource=recordPureSource(recording,recordOwner,kind,recordWidth,recordHeight);}}
                finally{node.endRecording();}dirty=false;logSource(recordedSource,kind);
            }
            int save=canvas.save();canvas.translate(bounds.left,bounds.top);RectF area=new RectF(0,0,bounds.width(),bounds.height());Path clip=shape(area,0);canvas.clipPath(clip);
            int scale=canvas.save();if(recordOwner!=v){int[] src=new int[2],dst=new int[2];recordOwner.getLocationOnScreen(src);v.getLocationOnScreen(dst);canvas.translate(src[0]-dst[0],src[1]-dst[1]);}
            canvas.scale(1f/SCALE,1f/SCALE);canvas.drawRenderNode(node);canvas.restoreToCount(scale);
            boolean series="drama".equals(kind)&&"ggr".equals(idName(v));
            float tintTop=0,tintBottom=area.height();
            if(bottomMask&&anchor!=null){int[] a=new int[2],b=new int[2];anchor.getLocationOnScreen(a);v.getLocationOnScreen(b);tintTop=a[1]-b[1];tintBottom=tintTop+anchor.getHeight();}
            boolean dramaSearch="drama".equals(kind)&&blur==LIGHT;
            int top=dramaSearch?0x301E2028:series?0x3E1A1E26:liquidActive?(night?0x202B2D36:0x18EDF0F3):night?0x992b2d36:0x66edf0f3;
            int bottom=dramaSearch?0x42242832:series?0x521A1E26:liquidActive?(night?0x28181A21:0x10F4F5F7):night?0xb0181a21:0x33f4f5f7;
            brush.setStyle(Paint.Style.FILL);brush.setShader(new LinearGradient(0,tintTop,0,tintBottom,top,bottom,Shader.TileMode.CLAMP));canvas.drawRect(area,brush);brush.setShader(null);
            logApplied("optics="+(liquidActive?"liquid":"frosted")+" source="+kind+" owner="+idName(v)+" parentSource="+(bottomMask?"fue":"self"));
            float stroke=.75f*dp(v);brush.setStyle(Paint.Style.STROKE);brush.setStrokeWidth(stroke);brush.setColor(dramaSearch?0x40FFFFFF:series?0x4DFFFFFF:night?0x42ffffff:0x1F000000);area.inset(stroke/2,stroke/2);if(bottomMask)canvas.drawPath(maskEdges(area,stroke/2),brush);else if(nativeRadii!=null)canvas.drawPath(shape(area,stroke/2),brush);else canvas.drawRoundRect(area,Math.max(0,radius-stroke/2),Math.max(0,radius-stroke/2),brush);
            area.inset(stroke,stroke);brush.setColor(night?0x22ffffff:0x88FFFFFF);if(bottomMask)canvas.drawPath(maskEdges(area,1.5f*stroke),brush);else if(nativeRadii!=null)canvas.drawPath(shape(area,1.5f*stroke),brush);else canvas.drawRoundRect(area,Math.max(0,radius-1.5f*stroke),Math.max(0,radius-1.5f*stroke),brush);brush.setStyle(Paint.Style.FILL);canvas.restoreToCount(save);
            logApplied("tier="+(blur==LIGHT?"light":blur==HEAVY?"heavy":"medium")+" kind="+(dialog?"dialog":search||blur==LIGHT?"search":"panel")+" blurDp="+(int)blur+" source="+kind+" owner="+idName(v));
        }
        public void setAlpha(int alpha){} public void setColorFilter(ColorFilter filter){} public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
