package io.github.mianxyz.fanqiecustomizer;

import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.*;
import android.widget.ImageView;
import java.lang.ref.WeakReference;
import java.util.*;

/** Native 73932 rank background: fixed, theme-only frosted source; no content capture. */
final class NativeCoverGlass {
    static final float SCALE=.25f, BLUR_DP=22f, RADIUS_PX=21f;
    private static final Set<String> diagnostics=new HashSet<>(),layerDiagnostics=new HashSet<>();
    private static final Map<View,CoverBackground> active=new WeakHashMap<>();
    private NativeCoverGlass(){}
    private static boolean guarded;
    static void installGuard(io.github.libxposed.api.XposedModule module){if(guarded)return;try{module.hook(View.class.getDeclaredMethod("setBackgroundDrawable",Drawable.class)).intercept(chain->{View v=(View)chain.getThisObject();CoverBackground cached=active.get(v);GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();Drawable requested=(Drawable)chain.getArg(0);if(cached!=null&&requested!=cached&&g!=null&&g.rankCover()){cached.original=requested;return chain.proceed(new Object[]{cached});}return chain.proceed();});guarded=true;}catch(Throwable e){diagnostic("background-guard "+e.getClass().getSimpleName());}}

    static void imageChanged(){for(CoverBackground g:new ArrayList<>(active.values())){g.image=null;g.imageShader=null;View v=g.owner.get();if(v!=null)v.invalidate();}}
    private static void diagnostic(String text){Set<String> bucket=text.startsWith("layer ")?layerDiagnostics:diagnostics;synchronized(bucket){if(bucket.size()>=96||!bucket.add(text))return;}GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null)g.logIssue("native-rank "+text);}

    /** Candidate is the exact direct ConstraintLayout parent of ti with hlf below ti. */
    static boolean matches(View candidate){
        if(candidate==null||!candidate.getClass().getName().equals("androidx.constraintlayout.widget.ConstraintLayout")||!(candidate instanceof ViewGroup))return false;
        ViewGroup group=(ViewGroup)candidate;
        for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);
            if("ti".equals(id(child))&&child.getClass().getName().equals("com.dragon.read.component.biz.impl.bookmall.widge.AnimatorContainerLayout")&&hasId(child,"hlf"))return true;
        }return false;
    }
    static boolean attach(View candidate,boolean night){
        if(Build.VERSION.SDK_INT<31||!matches(candidate))return false;
        Drawable background=candidate.getBackground();
        CoverBackground glass=active.get(candidate);
        if(glass==null){glass=new CoverBackground(candidate,background);active.put(candidate,glass);candidate.addOnAttachStateChangeListener(glass);candidate.getViewTreeObserver().addOnPreDrawListener(glass);}
        glass.ensureBackground();glass.safeUpdate(night,"tick");return true;
    }
    /** Call from the existing lifecycle tick; only theme and panel size affect the source. */
    static boolean update(View candidate,boolean enabled,boolean night){
        if(!enabled){restore(candidate);return false;}return attach(candidate,night);
    }
    static void restore(View candidate){
        if(candidate==null)return;CoverBackground glass=active.remove(candidate);if(glass==null)return;
        candidate.removeOnAttachStateChangeListener(glass);if(candidate.getViewTreeObserver().isAlive())candidate.getViewTreeObserver().removeOnPreDrawListener(glass);
        if(glass.layer!=null&&glass.layer.getParent() instanceof ViewGroup)((ViewGroup)glass.layer.getParent()).removeView(glass.layer);
        if(candidate.getBackground()==null||candidate.getBackground()==glass)preservePadding(candidate,glass.original);glass.release();candidate.invalidate();
    }
    private static void preservePadding(View view,Drawable background){
        int l=view.getPaddingLeft(),t=view.getPaddingTop(),r=view.getPaddingRight(),b=view.getPaddingBottom(),start=view.getPaddingStart(),end=view.getPaddingEnd();boolean relative=view.isPaddingRelative();
        view.setBackground(background);
        if(l!=view.getPaddingLeft()||t!=view.getPaddingTop()||r!=view.getPaddingRight()||b!=view.getPaddingBottom()){if(relative)view.setPaddingRelative(start,t,end,b);else view.setPadding(l,t,r,b);}
    }
    static int fixedSource(boolean night){return night?0xff20232a:0xffe8edf2;}
    static int mistTop(boolean night){return night?0x482c3038:0x48ffffff;}
    static int mistBottom(boolean night){return night?0x58242830:0x58f6f8fa;}
    static float blurSigma(float density){return BLUR_DP*density*SCALE;}
    static boolean rankEdge(String name){return "fkq".equals(name)||"hd_".equals(name);}
    static boolean whiteFade(int[] colors){
        if(colors==null||colors.length<2)return false;boolean opaque=false,transparent=false;
        for(int c:colors){int r=(c>>>16)&255,g=(c>>>8)&255,b=c&255,a=c>>>24;if(r<230||g<230||b<230||Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))>12)return false;opaque|=a>200;transparent|=a<96;}
        return opaque&&transparent;
    }
    static int textureSize(int pixels){return Math.max(1,Math.round(pixels*SCALE));}
    private static String id(View v){try{return HostSymbols.canonicalResource(v.getResources().getResourceEntryName(v.getId()));}catch(Throwable ignored){return "";}}
    private static String drawOwner(Class<?> type,String name){for(Class<?> c=type;c!=null;c=c.getSuperclass())try{c.getDeclaredMethod(name,Canvas.class);return c.getName();}catch(NoSuchMethodException ignored){}return "none";}
    private static String drawableDetails(Drawable d,int depth){
        if(d==null)return "null";if(depth>4)return d.getClass().getName();String s=d.getClass().getName()+" alpha="+d.getAlpha()+" opacity="+d.getOpacity();
        if(d instanceof ColorDrawable)s+=" color="+String.format("#%08X",((ColorDrawable)d).getColor());
        if(d instanceof GradientDrawable){GradientDrawable g=(GradientDrawable)d;s+=" color="+(g.getColor()==null?"null":String.format("#%08X",g.getColor().getDefaultColor()))+" colors="+Arrays.toString(g.getColors());}
        if(d instanceof LayerDrawable){LayerDrawable l=(LayerDrawable)d;for(int i=0;i<l.getNumberOfLayers();i++)s+=" layer["+i+"]={"+drawableDetails(l.getDrawable(i),depth+1)+"}";}
        if(d instanceof StateListDrawable)s+=" selected={"+drawableDetails(d.getCurrent(),depth+1)+"}";
        return s;
    }
    private static boolean hasId(View v,String name){if(name.equals(id(v)))return true;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)if(hasId(g.getChildAt(i),name))return true;}return false;}
    private static final class CoverBackground extends Drawable implements View.OnAttachStateChangeListener,ViewTreeObserver.OnPreDrawListener {
        final WeakReference<View> owner;Drawable original;final Paint brush=new Paint(Paint.ANTI_ALIAS_FLAG);final Rect padding=new Rect();final boolean paddingResult;
        final Map<View,Drawable> cleared=new WeakHashMap<>(),foregrounds=new WeakHashMap<>();
        final Map<View,Boolean> fading=new WeakHashMap<>();View layer;boolean drawLogged,layersReady;
        boolean neutral(Drawable d){if(d==null)return false;Integer color=null;if(d instanceof ColorDrawable)color=((ColorDrawable)d).getColor();else if(d instanceof GradientDrawable){android.content.res.ColorStateList list=((GradientDrawable)d).getColor();if(list!=null)color=list.getDefaultColor();else{int[] colors=((GradientDrawable)d).getColors();if(colors!=null&&colors.length>0){for(int c:colors)if(!neutral(new ColorDrawable(c)))return false;return true;}}}if(color==null)return false;int r=Color.red(color),g=Color.green(color),b=Color.blue(color);return Color.alpha(color)>200&&Math.max(r,Math.max(g,b))-Math.min(r,Math.min(g,b))<22&&r>150;}
        boolean edgeFade(View v,Drawable drawable){return rankEdge(id(v))&&drawable instanceof GradientDrawable&&whiteFade(((GradientDrawable)drawable).getColors());}
        void inspectLayers(View v,int depth){
            if(depth>24||v==layer)return;if(v.getWidth()>0&&v.getHeight()>0&&v.isShown()){
                // Horizontal alpha fading and white edge masks belong to RankEdgeGlass.
                Drawable bg=v.getBackground(),fg=v.getForeground();
                if(bg!=null||fg!=null)diagnostic("layer id="+id(v)+" class="+v.getClass().getName()+" bg="+drawableDetails(bg,0)+" fg="+drawableDetails(fg,0)+" z="+v.getZ()+" onDraw="+drawOwner(v.getClass(),"onDraw")+" dispatchDraw="+drawOwner(v.getClass(),"dispatchDraw")+" size="+v.getWidth()+"x"+v.getHeight());
                if(v!=owner.get()&&!(v instanceof ImageView)&&neutral(bg)&&!rankEdge(id(v))){cleared.put(v,bg);preservePadding(v,null);}
                if(v!=owner.get()&&!(v instanceof ImageView)&&neutral(fg)&&!rankEdge(id(v))){foregrounds.put(v,fg);v.setForeground(null);}
            }
            if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)inspectLayers(g.getChildAt(i),depth+1);}
        }
        void restoreLayers(){for(Map.Entry<View,Drawable> e:cleared.entrySet())if(e.getKey().getBackground()==null)preservePadding(e.getKey(),e.getValue());for(Map.Entry<View,Drawable> e:foregrounds.entrySet())if(e.getKey().getForeground()==null)e.getKey().setForeground(e.getValue());cleared.clear();foregrounds.clear();for(Map.Entry<View,Boolean> e:fading.entrySet())e.getKey().setHorizontalFadingEdgeEnabled(e.getValue());fading.clear();}
        public boolean onPreDraw(){View view=owner.get();if(view!=null){GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null&&g.rankCover()){ensureBackground();boolean dark=(view.getResources().getConfiguration().uiMode&48)==32;if(night!=dark)safeUpdate(dark,"theme");}else restore(view);}return true;}
        void ensureBackground(){View view=owner.get();if(view==null)return;Drawable replacement=view.getBackground();if(replacement!=this){original=replacement;preservePadding(view,this);diagnostic("background-restored cached=true");}}
        void safeUpdate(boolean dark,String entry){try{update(dark);}catch(Throwable error){diagnostic("update-error entry="+entry+" "+android.util.Log.getStackTraceString(error));}}
        Bitmap image;BitmapShader imageShader;final Matrix imageMatrix=new Matrix();
        RenderNode node;int rw,rh;float renderDensity;boolean night;
        CoverBackground(View view,Drawable old){owner=new WeakReference<>(view);original=old;paddingResult=old!=null&&old.getPadding(padding);}
        void update(boolean dark){View view=owner.get();if(view==null)return;if(!layersReady){inspectLayers(view,0);layersReady=true;}
            if(night!=dark){night=dark;discard();}
            if(layer!=null)layer.invalidate();view.invalidate();
        }
        void discard(){if(node!=null){node.discardDisplayList();node=null;}}
        void release(){restoreLayers();discard();}
        public void draw(Canvas canvas){View view=owner.get();Rect bounds=getBounds();if(view==null||bounds.isEmpty())return;
            int width=textureSize(bounds.width()),height=textureSize(bounds.height());float density=view.getResources().getDisplayMetrics().density;
            boolean hardware=canvas.isHardwareAccelerated();
            if(hardware&&(node==null||rw!=width||rh!=height||renderDensity!=density||!node.hasDisplayList())){
                discard();rw=width;rh=height;renderDensity=density;node=new RenderNode("PulpNativeRankFixedFrost");node.setPosition(0,0,rw,rh);node.setClipToBounds(true);
                RecordingCanvas recording=node.beginRecording(rw,rh);
                try{recording.drawColor(fixedSource(night));}finally{node.endRecording();}
                float sigma=blurSigma(density);node.setRenderEffect(RenderEffect.createBlurEffect(sigma,sigma,Shader.TileMode.CLAMP));
            }
            int save=canvas.save();try{canvas.translate(bounds.left,bounds.top);RectF area=new RectF(0,0,bounds.width(),bounds.height());Path clip=new Path();clip.addRoundRect(area,RADIUS_PX,RADIUS_PX,Path.Direction.CW);canvas.clipPath(clip);
                if(hardware&&node!=null&&node.hasDisplayList()){int scaled=canvas.save();canvas.scale(bounds.width()/(float)rw,bounds.height()/(float)rh);canvas.drawRenderNode(node);canvas.restoreToCount(scaled);}
                else canvas.drawColor(fixedSource(night));
                Bitmap custom=RankImageClient.bitmap;if(custom!=null&&!custom.isRecycled()){if(image!=custom){image=custom;imageShader=new BitmapShader(custom,Shader.TileMode.CLAMP,Shader.TileMode.CLAMP);}float scale=Math.max(area.width()/custom.getWidth(),area.height()/custom.getHeight());imageMatrix.setScale(scale,scale);imageMatrix.postTranslate((area.width()-custom.getWidth()*scale)/2,(area.height()-custom.getHeight()*scale)/2);imageShader.setLocalMatrix(imageMatrix);brush.setShader(imageShader);brush.setAlpha(255);canvas.drawRect(area,brush);brush.setShader(null);canvas.drawColor(night?0x66000000:0x24ffffff);}
                brush.setStyle(Paint.Style.FILL);brush.setColor(Color.WHITE);brush.setAlpha(255);brush.setShader(new LinearGradient(0,0,0,area.height(),mistTop(night),mistBottom(night),Shader.TileMode.CLAMP));canvas.drawRect(area,brush);brush.setShader(null);
                float stroke=.75f*density;brush.setStyle(Paint.Style.STROKE);brush.setStrokeWidth(stroke);brush.setColor(night?0x42FFFFFF:0x99FFFFFF);area.inset(stroke/2,stroke/2);canvas.drawRoundRect(area,Math.max(0,RADIUS_PX-stroke/2),Math.max(0,RADIUS_PX-stroke/2),brush);brush.setStyle(Paint.Style.FILL);
            }finally{canvas.restoreToCount(save);}
            if(!drawLogged){drawLogged=true;diagnostic("render-fixed source="+String.format("#%08X",fixedSource(night))+" blurDp="+BLUR_DP+" night="+night+" hardware="+hardware);}
        }
        void drawOriginal(Canvas c){if(original==null)return;Rect old=new Rect(original.getBounds());try{original.setBounds(getBounds());original.draw(c);}finally{original.setBounds(old);}}
        public boolean getPadding(Rect out){out.set(padding);return paddingResult;}
        public int getMinimumWidth(){return original==null?0:original.getMinimumWidth();}public int getMinimumHeight(){return original==null?0:original.getMinimumHeight();}
        public int getIntrinsicWidth(){return original==null?-1:original.getIntrinsicWidth();}public int getIntrinsicHeight(){return original==null?-1:original.getIntrinsicHeight();}
        public void getOutline(Outline out){out.setRoundRect(getBounds(),RADIUS_PX);}
        public void setAlpha(int value){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        public void onViewAttachedToWindow(View v){if(v.getViewTreeObserver().isAlive()){v.getViewTreeObserver().removeOnPreDrawListener(this);v.getViewTreeObserver().addOnPreDrawListener(this);}ensureBackground();safeUpdate((v.getResources().getConfiguration().uiMode&48)==32,"attach");}
        public void onViewDetachedFromWindow(View v){if(v.getViewTreeObserver().isAlive())v.getViewTreeObserver().removeOnPreDrawListener(this); /* Recycler reuse retains the cached drawable and attach guard. */}
    }
}
