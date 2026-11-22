package io.github.mianxyz.fanqiecustomizer;

import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import java.lang.ref.WeakReference;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedModule;

/** Exact 73932 PreCompose square tile; records independent image pixels only. */
final class SquareGlassHooks {
 final XposedModule module;final ClassLoader loader;
 final Map<View,Tile> tiles=new WeakHashMap<>();final ThreadLocal<Tile> scope=new ThreadLocal<>();final Set<String> logs=new HashSet<>();
 SquareGlassHooks(XposedModule m,ClassLoader l){module=m;loader=l;}
 Class<?> cls(String n)throws Exception{return HostSymbols.type(n,loader);}
 Method publicMethod(String owner,String name,Class<?>... parameters)throws Exception{Class<?> type=cls(owner);return type.getMethod(HostSymbols.publicMethodName(type,name,parameters),parameters);}
 Field publicField(String owner,String name)throws Exception{Class<?> type=cls(owner);return type.getField(HostSymbols.publicFieldName(type,name));}
 Method method(String c,String n,Class<?>... p)throws Exception{Method m=cls(c).getDeclaredMethod(HostSymbols.methodName(cls(c),n),p);m.setAccessible(true);return m;}
 void log(String s){if(logs.add(s))module.log(android.util.Log.INFO,"FanqieCustomizer","GlobalGlass square "+s);}
 interface Setup{void run()throws Throwable;}
 void hook(String n,Setup s){try{s.run();}catch(Throwable e){log("hook-error "+n+" "+e);}}
 boolean bitmapPainterSymbols(){return HostSymbols.requireCapability("SquareGlassHooks.bitmapPainter",new String[]{"androidx.compose.ui.graphics.painter.a","androidx.compose.ui.graphics.g1","androidx.compose.ui.graphics.j"},new String[]{"androidx.compose.ui.graphics.j#a(Landroidx/compose/ui/graphics/g1;)Landroid/graphics/Bitmap;"},new String[]{"androidx.compose.ui.graphics.painter.a#f:Landroidx/compose/ui/graphics/g1;"},new String[]{});}

 boolean symbolPreflight(){if(HostSymbols.isBaseline())return true;try{publicMethod("f0.d","A1");publicMethod("androidx.compose.runtime.SnapshotStateKt","mutableStateOf$default",Object.class,cls("androidx.compose.runtime.SnapshotMutationPolicy"),int.class,Object.class);return true;}catch(Throwable error){log("symbols unavailable "+error.getClass().getSimpleName());return false;}}

 void install(){
        if(!symbolPreflight())return;
        if(!HostSymbols.requireCapability("SquareGlassHooks",new String[]{"androidx.compose.foundation.h","androidx.compose.runtime.MutableState","androidx.compose.runtime.SnapshotMutationPolicy","androidx.compose.runtime.SnapshotStateKt","androidx.compose.runtime.State","androidx.compose.ui.graphics.c","androidx.compose.ui.graphics.g0","androidx.compose.ui.graphics.n0","androidx.compose.ui.graphics.painter.Painter","androidx.compose.ui.platform.AndroidComposeView","com.bytedance.kmp.image.painter.DrawablePainter","f0.a$b","f0.d","f0.h"},new String[]{"androidx.compose.foundation.h#s(Lf0/d;)V","androidx.compose.runtime.MutableState#setValue(Ljava/lang/Object;)V","androidx.compose.runtime.State#getValue()Ljava/lang/Object;","androidx.compose.ui.graphics.c#a(Landroidx/compose/ui/graphics/g0;)Landroid/graphics/Canvas;","androidx.compose.ui.graphics.painter.Painter#f(Lf0/h;JFLandroidx/compose/ui/graphics/n0;)V","androidx.compose.ui.platform.AndroidComposeView#dispatchDraw(Landroid/graphics/Canvas;)V","f0.a$b#a()Landroidx/compose/ui/graphics/g0;","f0.h#U0()Lf0/a$b;","f0.h#c()J"},new String[]{"com.bytedance.kmp.image.painter.DrawablePainter#f:Landroid/graphics/drawable/Drawable;"},new String[]{"f5y","e5u"}))return;
  hook("owner",()->module.hook(method("androidx.compose.ui.platform.AndroidComposeView","dispatchDraw",Canvas.class)).intercept(c->{
   View v=(View)c.getThisObject();Tile previous=scope.get(),t=eligible(v)?tile(v):null;
   if(t==null)scope.remove();else scope.set(t);
   Canvas canvas=(Canvas)c.getArg(0);
   boolean rounded=t!=null&&t.images.size()==3&&enabled()&&canvas.isHardwareAccelerated();
   int saved=rounded?canvas.saveLayer(0,0,v.getWidth(),v.getHeight(),null):canvas.save();
   if(t!=null)t.cornersActive=rounded;
   try{return c.proceed();}
   finally{
    try{if(rounded)maskCorners(t,canvas,v.getWidth(),v.getHeight());}
    finally{canvas.restoreToCount(saved);if(t!=null)t.cornersActive=false;if(previous==null)scope.remove();else scope.set(previous);}
   }
  }));
  hook("background",()->module.hook(method("androidx.compose.foundation.h","s",cls("f0.d"))).intercept(c->{Tile t=scope.get();if(t==null)return c.proceed();Object ds=c.getArg(0);float[] size=size(ds);View v=t.owner.get();if(v==null||!full(size[0],size[1],v.getWidth(),v.getHeight()))return c.proceed();readState(t);log("background owner="+System.identityHashCode(v)+" node="+System.identityHashCode(c.getThisObject())+" size="+size[0]+"x"+size[1]);if(!enabled())return c.proceed();if(t.images.size()!=3)return c.proceed();if(!t.cornersActive)return c.proceed();Canvas canvas=canvas(ds);if(!canvas.isHardwareAccelerated())return c.proceed();paint(t,canvas,size[0],size[1],c.getThisObject());publicMethod("f0.d","A1").invoke(ds);return null;}));
  hook("painter",()->module.hook(method("androidx.compose.ui.graphics.painter.Painter","f",cls("f0.h"),long.class,float.class,cls("androidx.compose.ui.graphics.n0"))).intercept(c->{Object result=c.proceed();Tile t=scope.get();if(t!=null)try{long packed=((Number)c.getArg(1)).longValue();View owner=t.owner.get();if(owner!=null&&smallCover(Float.intBitsToFloat((int)(packed>>>32)),Float.intBitsToFloat((int)packed),owner.getWidth()))capture(t,c.getThisObject());}catch(Throwable e){log("capture-error "+e.getClass().getSimpleName());}return result;}));
  // Painter.f supplies actual destination geometry for DrawablePainter and BitmapPainter.
 }
 static boolean smallCover(float w,float h,int ownerWidth){return Float.isFinite(w)&&Float.isFinite(h)&&ownerWidth>0&&w>=ownerWidth*.12f&&w<=ownerWidth*.42f&&h>=w*1.05f&&h<=w*2.2f;}
 static boolean full(float w,float h,int vw,int vh){return Float.isFinite(w)&&Float.isFinite(h)&&vw>0&&vh>0&&Math.abs(w-vw)<=2&&Math.abs(h-vh)<=2;}
 boolean eligible(View v){ViewParent p=v.getParent();if(!(p instanceof View))return false;View compose=(View)p;ViewParent fp=compose.getParent();if(!(fp instanceof View))return false;ViewParent ep=fp.getParent();if(!(ep instanceof View))return false;View host=(View)ep;boolean book=false;if(host instanceof ViewGroup)for(int i=0;i<((ViewGroup)host).getChildCount();i++)book|=((ViewGroup)host).getChildAt(i).getClass().getName().equals(HostSymbols.name("com.dragon.read.widget.bookcover.StaggeredTagView"));return squareLineage(compose.getClass().getName(),id((View)fp),id(host),book);}
 static boolean squareLineage(String compose,String frame,String impression,boolean bookTag){return !bookTag&&"f5y".equals(frame)&&"e5u".equals(impression)&&(HostSymbols.name("androidx.compose.ui.precompose.PreComposeView").equals(compose)||HostSymbols.name("androidx.compose.ui.platform.ComposeView").equals(compose));}
 // f5w is the host ComposeView ID, shared by books and square templates; never a page selector.
 String id(View v){try{return HostSymbols.canonicalResource(v.getResources().getResourceEntryName(v.getId()));}catch(Throwable e){return "";}}
 Tile tile(View v){Tile t=tiles.get(v);if(t==null){t=new Tile(v);tiles.put(v,t);try{t.state=publicMethod("androidx.compose.runtime.SnapshotStateKt","mutableStateOf$default",Object.class,cls("androidx.compose.runtime.SnapshotMutationPolicy"),int.class,Object.class).invoke(null,0,null,2,null);}catch(Throwable e){log("state-error "+e);}log("owner="+System.identityHashCode(v)+" template="+v.getParent().getClass().getSimpleName()+"/f5y/e5u structure=direct/no-book-tag"+" size="+v.getWidth()+"x"+v.getHeight());}return t;}
 void invalidateAll(){for(Map.Entry<View,Tile> e:new ArrayList<>(tiles.entrySet())){Tile t=e.getValue();try{if(t.state!=null)publicMethod("androidx.compose.runtime.MutableState","setValue",Object.class).invoke(t.state,++t.revision);}catch(Throwable error){log("invalidate-error "+error.getClass().getSimpleName());}if(e.getKey()!=null)e.getKey().postInvalidateOnAnimation();}}
 void readState(Tile t)throws Exception{if(t.state!=null)publicMethod("androidx.compose.runtime.State","getValue").invoke(t.state);}
 boolean enabled(){GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();return g!=null&&g.cardText();}
 float[] size(Object ds)throws Exception{long n=((Number)publicMethod("f0.h","c").invoke(ds)).longValue();return new float[]{Float.intBitsToFloat((int)(n>>>32)),Float.intBitsToFloat((int)n)};}
 Canvas canvas(Object ds)throws Exception{Object dc=publicMethod("f0.h","U0").invoke(ds),cc=publicMethod("f0.a$b","a").invoke(dc);return (Canvas)publicMethod("androidx.compose.ui.graphics.c","a",cls("androidx.compose.ui.graphics.g0")).invoke(null,cc);}
 Object field(Object o,String name){for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(HostSymbols.fieldName(c,name));f.setAccessible(true);return f.get(o);}catch(Throwable ignored){}return null;}
 Bitmap image(Drawable d,Set<Drawable> visited,int depth){if(d==null||depth>8||!visited.add(d))return null;if(d instanceof BitmapDrawable)return ((BitmapDrawable)d).getBitmap();Drawable cur=d.getCurrent();if(cur!=d){Bitmap b=image(cur,visited,depth+1);if(b!=null)return b;}for(Class<?> c=d.getClass();c!=null&&Drawable.class.isAssignableFrom(c);c=c.getSuperclass())for(Field f:c.getDeclaredFields())if(!Modifier.isStatic(f.getModifiers()))try{f.setAccessible(true);Object v=f.get(d);if(v instanceof Bitmap)return (Bitmap)v;if(v instanceof Drawable){Bitmap b=image((Drawable)v,visited,depth+1);if(b!=null)return b;}if(v instanceof Drawable[])for(Drawable child:(Drawable[])v){Bitmap b=image(child,visited,depth+1);if(b!=null)return b;}}catch(Throwable ignored){}return null;}
 void capture(Tile t,Object painter)throws Exception{String n=painter.getClass().getName();Bitmap b=null;if(n.equals(HostSymbols.name("com.bytedance.kmp.image.painter.DrawablePainter"))){Object d=field(painter,"f");if(d instanceof Drawable)b=image((Drawable)d,Collections.newSetFromMap(new IdentityHashMap<>()),0);}else if(n.equals(HostSymbols.name("androidx.compose.ui.graphics.painter.a"))&&bitmapPainterSymbols()){Object i=field(painter,"f");if(i!=null)b=(Bitmap)method("androidx.compose.ui.graphics.j","a",cls("androidx.compose.ui.graphics.g1")).invoke(null,i);}if(b==null||b.isRecycled()||b.getWidth()<32||b.getHeight()<32)return;int generation=b.getGenerationId();Integer old=t.inputs.get(b);boolean resident=false;for(WeakReference<Bitmap> source:t.sources)if(source.get()==b){resident=true;break;}if(resident&&old!=null&&old==generation)return;Bitmap readable=b.getConfig()==Bitmap.Config.HARDWARE?b.copy(Bitmap.Config.ARGB_8888,false):b;if(readable==null)return;Bitmap copy=Bitmap.createBitmap(48,64,Bitmap.Config.ARGB_8888);try{new Canvas(copy).drawBitmap(readable,null,new Rect(0,0,48,64),new Paint(Paint.FILTER_BITMAP_FLAG));}finally{if(readable!=b)readable.recycle();}int[] pixels=new int[48*64];copy.getPixels(pixels,0,48,0,0,48,64);boolean valid=false;for(int p:pixels)if((p>>>24)>96&&(((p>>>16)&255)<248||((p>>>8)&255)<248||(p&255)<248)){valid=true;break;}if(!valid){copy.recycle();return;}long fingerprint=1469598103934665603L;for(int pixel:pixels){fingerprint^=pixel;fingerprint*=1099511628211L;}int slot=-1;for(int i=0;i<t.sources.size();i++)if(t.sources.get(i).get()==b){slot=i;break;}if(slot<0)for(int i=0;i<t.fingerprints.size();i++)if(t.fingerprints.get(i)==fingerprint&&t.images.get(i).sameAs(copy)){slot=i;break;}if(t.inputs.size()>=12&&!t.inputs.containsKey(b))t.inputs.clear();t.inputs.put(b,generation);if(slot>=0&&t.fingerprints.get(slot)==fingerprint&&t.images.get(slot).sameAs(copy)){t.sources.set(slot,new WeakReference<>(b));copy.recycle();return;}if(slot<0){if(t.images.size()<3){slot=t.images.size();t.images.add(copy);t.sources.add(new WeakReference<>(b));t.fingerprints.add(fingerprint);}else{slot=t.nextSlot++%3;t.images.set(slot,copy);t.sources.set(slot,new WeakReference<>(b));t.fingerprints.set(slot,fingerprint);}}else{t.images.set(slot,copy);t.sources.set(slot,new WeakReference<>(b));t.fingerprints.set(slot,fingerprint);}t.dirty=true;View owner=t.owner.get();if(t.state!=null)publicMethod("androidx.compose.runtime.MutableState","setValue",Object.class).invoke(t.state,++t.revision);if(owner!=null)owner.postInvalidateOnAnimation();log("source owner="+(owner==null?0:System.identityHashCode(owner))+" images="+t.images.size()+" revision="+t.revision+" slot="+slot+" bitmap="+System.identityHashCode(b)+" generation="+generation+" fp="+Long.toHexString(fingerprint)+" painter="+n);}
 void paint(Tile t,Canvas c,float w,float h,Object bgNode){View v=t.owner.get();float density=v.getResources().getDisplayMetrics().density;boolean night=(v.getResources().getConfiguration().uiMode&48)==32;int rw=Math.max(1,Math.round(w*.25f)),rh=Math.max(1,Math.round(h*.25f));if(t.node==null||!t.node.hasDisplayList()||t.w!=rw||t.h!=rh||t.dirty){if(t.node!=null)t.node.discardDisplayList();t.node=new RenderNode("PulpSquareImagesOnly");t.node.setPosition(0,0,rw,rh);RecordingCanvas rc=t.node.beginRecording(rw,rh);try{rc.drawColor(night?0xff252830:0xfff2eee6);for(int i=0;i<t.images.size();i++)rc.drawBitmap(t.images.get(i),null,new RectF(i*rw/(float)t.images.size(),0,(i+1)*rw/(float)t.images.size(),rh),new Paint(Paint.FILTER_BITMAP_FLAG));}finally{t.node.endRecording();}t.node.setRenderEffect(RenderEffect.createBlurEffect(5.5f*density,5.5f*density,Shader.TileMode.CLAMP));t.w=rw;t.h=rh;t.dirty=false;}int save=c.save();try{float radius=8*density;int scale=c.save();c.scale(w/rw,h/rh);c.drawRenderNode(t.node);c.restoreToCount(scale);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setShader(new LinearGradient(0,0,0,h,night?0xB020222A:0x82FFFFFF,night?0xB0252830:0xB2FFFFFF,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(.75f*density);p.setColor(night?0x38FFFFFF:0x88FFFFFF);float inset=.375f*density;c.drawRoundRect(new RectF(inset,inset,w-inset,h-inset),Math.max(0,radius-inset),Math.max(0,radius-inset),p);}finally{c.restoreToCount(save);}log("applied owner="+System.identityHashCode(v)+" blurDp=22 cornersDp=8 images="+t.images.size()+" size="+w+"x"+h);}
 // One antialiased boundary after Compose content, rather than intersecting two path clips.
 // Only the image-only RenderNode above is blurred; this layer never filters text/covers.
 void maskCorners(Tile t,Canvas c,int w,int h){
  View v=t.owner.get();if(v==null)return;float density=v.getResources().getDisplayMetrics().density;
  if(t.maskW!=w||t.maskH!=h||t.maskDensity!=density){
   Path cut=t.cornerCutout;cut.reset();cut.setFillType(Path.FillType.EVEN_ODD);
   cut.addRect(-1,-1,w+1,h+1,Path.Direction.CW);
   float radius=8*density;cut.addRoundRect(new RectF(0,0,w,h),radius,radius,Path.Direction.CW);
   t.cornerClear.setBlendMode(BlendMode.CLEAR);t.maskW=w;t.maskH=h;t.maskDensity=density;
   logCornerLineage(v);
  }
  c.drawPath(t.cornerCutout,t.cornerClear);
 }
 // Read-only diagnostics. Drawable outline is NOT proof of the active ViewOutlineProvider.
 void logCornerLineage(View owner){
  View v=owner;
  for(int depth=0;depth<4&&v!=null;depth++){
   try{
    Drawable bg=v.getBackground();Outline outline=new Outline();Rect rect=new Rect();
    if(bg!=null)bg.getOutline(outline);boolean rectKnown=outline.getRect(rect);
    log("corners owner="+System.identityHashCode(owner)+" depth="+depth+" view="+v.getClass().getName()+" id="+id(v)+" size="+v.getWidth()+"x"+v.getHeight()+" xy="+v.getLeft()+","+v.getTop()+" clipToOutline="+v.getClipToOutline()+" clipBounds="+v.getClipBounds()+" clipChildren="+(v instanceof ViewGroup?((ViewGroup)v).getClipChildren():"n/a")+" background="+(bg==null?"null":bg.getClass().getName())+" drawableOutlineEmpty="+outline.isEmpty()+" drawableOutlineRect="+(rectKnown?rect:"unknown")+" drawableRadius="+outline.getRadius()+" activeOutline=unobserved");
   }catch(Throwable error){log("corners-probe-error depth="+depth+" "+error.getClass().getSimpleName());}
   ViewParent parent=v.getParent();v=parent instanceof View?(View)parent:null;
  }
 }
 static final class Tile{final WeakReference<View> owner;final Map<Bitmap,Integer> inputs=new IdentityHashMap<>();final List<WeakReference<Bitmap>> sources=new ArrayList<>(3);final List<Long> fingerprints=new ArrayList<>(3);final List<Bitmap> images=new ArrayList<>(3);Object state;int revision,w,h,nextSlot;boolean dirty=true,cornersActive;int maskW,maskH;float maskDensity;final Path cornerCutout=new Path();final Paint cornerClear=new Paint(Paint.ANTI_ALIAS_FLAG);RenderNode node;Tile(View v){owner=new WeakReference<>(v);}}
}
