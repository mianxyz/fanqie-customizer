package io.github.mianxyz.fanqiecustomizer;

import android.graphics.*;
import android.graphics.drawable.*;
import android.os.Build;
import android.view.View;
import java.lang.ref.*;
import java.lang.reflect.*;
import java.util.*;
import io.github.libxposed.api.XposedModule;

/** 73932 bytecode-backed candidate; staging only. No shared DSL field writes.
 * Signatures are offline verified; runtime classes are logged only when observed.
 */
final class CardGlassHooks {
    interface DrawAction { void draw(Object contentScope) throws Throwable; }
    interface DrawBridge { Object append(Object modifier, DrawAction action) throws Throwable; }
    private final XposedModule module;
    private final ClassLoader loader;
    private final DrawBridge bridge;
    private final IdentityWeakMap<State> states=new IdentityWeakMap<>();
    private final IdentityWeakMap<Card> bgElements=new IdentityWeakMap<>(), bgNodes=new IdentityWeakMap<>();
    private final ThreadLocal<Frame> current=new ThreadLocal<>();
    private final ThreadLocal<Card> markingBackground=new ThreadLocal<>(), coverDrawing=new ThreadLocal<>();
    private final Set<View> composeOwners=Collections.newSetFromMap(new WeakHashMap<>());
    private final ThreadLocal<View> drawingOwner=new ThreadLocal<>();
    private final Set<String> seen=new HashSet<>();
    private int diagnosticCards;
    private final IdentityWeakMap<PainterBinding> painterCards=new IdentityWeakMap<>();
    private final ThreadLocal<Boolean> extracting=new ThreadLocal<>();
    private final SourceCache<CachedSource> sourceCache=new SourceCache<>(48);
    private final IdentityWeakMap<WeakReference<Object>> elementNodes=new IdentityWeakMap<>(),nodeElements=new IdentityWeakMap<>();
    private final View.OnAttachStateChangeListener ownerLifecycle=new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){invalidateAll();}public void onViewDetachedFromWindow(View v){for(Card c:allCards())if(c.owner.get()==v){bgNodes.removeValue(c);if(c.render!=null)c.render.discardDisplayList();event(c,"detach","root=cleared");}}};
    private final List<WeakReference<Card>> cardRefs=new ArrayList<>();
    // A nested image/badge gets its own draw boundary, so it never inherits the cover capture.
    private final CachedAction nonCoverAction=new CachedAction(scope->{Card previous=coverDrawing.get();if(previous==null||!previous.dynamicCover)coverDrawing.remove();try{content(scope);}finally{if(previous!=null)coverDrawing.set(previous);}});
    CardGlassHooks(XposedModule m,ClassLoader l){module=m;loader=l;bridge=this::appendDraw;}
    CardGlassHooks(XposedModule m,ClassLoader l,DrawBridge b){module=m;loader=l;bridge=b;}
    private Object appendDraw(Object base,DrawAction action)throws Throwable{
        Class<?> mod=cls("androidx.compose.ui.Modifier"),draw=cls("androidx.compose.ui.draw.i");
        Class<?> f1=cls("kotlin.jvm.functions.Function1"),f2=cls("kotlin.jvm.functions.Function2");
        Method one=f1.getMethod(HostSymbols.publicMethodName(f1,"invoke",Object.class),Object.class),two=f2.getMethod(HostSymbols.publicMethodName(f2,"invoke",Object.class,Object.class),Object.class,Object.class);
        Method then=method("androidx.compose.ui.Modifier$-CC","$default$then",mod,mod);then.setAccessible(true);
        CachedAction cached=action instanceof CachedAction?(CachedAction)action:null;
        Object proxy=cached==null?null:cached.proxy;
        if(proxy==null)proxy=Proxy.newProxyInstance(loader,new Class<?>[]{draw},(self,method,args)->{
            if(method.getName().equals(HostSymbols.methodName(draw,"s"))){action.draw(args[0]);return null;}
            String proxyName=method.getName();
            for(String canonical:new String[]{"foldIn","foldOut","all","any","then"})if(proxyName.equals(HostSymbols.methodName(mod,canonical))){proxyName=canonical;break;}
            switch(proxyName){
                case "foldIn":return two.invoke(args[1],args[0],self);
                case "foldOut":return two.invoke(args[1],self,args[0]);
                case "all":case "any":return one.invoke(args[0],self);
                case "then":return then.invoke(null,self,args[0]);
                case "equals":return self==args[0];
                case "hashCode":return System.identityHashCode(self);
                case "toString":return "PulpCardDrawModifier";
                default:throw new UnsupportedOperationException(method.toString());
            }
        });
        if(cached!=null)cached.proxy=proxy;
        return mod.getMethod(HostSymbols.publicMethodName(mod,"then",mod),mod).invoke(base,proxy);
    }
    private Class<?> cls(String n)throws ClassNotFoundException{return HostSymbols.type(n,loader);}
    private Method publicMethod(String owner,String name,Class<?>... parameters)throws Exception{Class<?> type=cls(owner);return type.getMethod(HostSymbols.publicMethodName(type,name,parameters),parameters);}
    private Field declaredField(String owner,String name)throws Exception{Class<?> type=cls(owner);return type.getDeclaredField(HostSymbols.fieldName(type,name));}
    private Field publicField(String owner,String name)throws Exception{Class<?> type=cls(owner);return type.getField(HostSymbols.publicFieldName(type,name));}
    private Method method(String c,String n,Class<?>... p)throws Exception{Method m=cls(c).getDeclaredMethod(HostSymbols.methodName(cls(c),n),p);m.setAccessible(true);return m;}
    interface Setup {void run() throws Throwable;}
    private void isolated(String target,Setup setup){try{setup.run();report("card hook-installed "+target);}catch(Throwable e){failure(target,e);}}
    private void failure(String target,Throwable e){while(e instanceof InvocationTargetException&&e.getCause()!=null)e=e.getCause();report("card error "+target+" "+e.getClass().getName()+": "+String.valueOf(e.getMessage()));}
    private void once(String text){if(!GlobalGlassSurfaces.debugEnabled())return;synchronized(seen){if(!seen.add(text))return;}report(text);}
    private SquareGlassHooks square;
    private boolean bitmapPainterSymbols(){return HostSymbols.requireCapability("CardGlassHooks.bitmapPainter",new String[]{"androidx.compose.ui.graphics.painter.a","androidx.compose.ui.graphics.g1","androidx.compose.ui.graphics.j"},new String[]{"androidx.compose.ui.graphics.j#a(Landroidx/compose/ui/graphics/g1;)Landroid/graphics/Bitmap;"},new String[]{"androidx.compose.ui.graphics.painter.a#f:Landroidx/compose/ui/graphics/g1;"},new String[]{});}

    private boolean symbolPreflight(){if(HostSymbols.isBaseline())return true;try{
        Class<?> mod=cls("androidx.compose.ui.Modifier");publicField("androidx.compose.ui.Modifier","Companion");
        method("androidx.compose.ui.Modifier$-CC","$default$then",mod,mod);
        for(String name:new String[]{"foldIn","foldOut"})publicMethod("androidx.compose.ui.Modifier",name,Object.class,cls("kotlin.jvm.functions.Function2"));
        for(String name:new String[]{"all","any"})publicMethod("androidx.compose.ui.Modifier",name,cls("kotlin.jvm.functions.Function1"));
        publicMethod("androidx.compose.runtime.SnapshotStateKt","mutableStateOf$default",Object.class,cls("androidx.compose.runtime.SnapshotMutationPolicy"),int.class,Object.class);
        publicMethod("f0.d","A1");
        for(String name:new String[]{"a","b"})declaredField("aq6.k0",name);
        for(String name:new String[]{"d","e"})declaredField("aq6.g0",name);
        return true;
    }catch(Throwable error){once("card symbols unavailable "+error.getClass().getSimpleName());return false;}}

    void install(){
        isolated("square-glass",()->{square=new SquareGlassHooks(module,loader);square.install();});
        if(!symbolPreflight())return;
        if(!HostSymbols.requireCapability("CardGlassHooks",new String[]{"androidx.compose.foundation.BackgroundElement","androidx.compose.foundation.h","androidx.compose.runtime.Composer","androidx.compose.runtime.MutableState","androidx.compose.runtime.SnapshotMutationPolicy","androidx.compose.runtime.SnapshotStateKt","androidx.compose.runtime.State","androidx.compose.ui.Modifier","androidx.compose.ui.Modifier$-CC","androidx.compose.ui.draw.i","androidx.compose.ui.graphics.c","androidx.compose.ui.graphics.g0","androidx.compose.ui.graphics.n0","androidx.compose.ui.graphics.painter.Painter","androidx.compose.ui.platform.AndroidComposeView","aq6.d","aq6.i1","aq6.s","com.bytedance.kmp.image.painter.DrawablePainter","com.dragon.read.kmp.compose.common.uicontext.color.Theme","com.dragon.read.kmp.dsl.config.DynamicViewKitKt","com.dragon.read.kmp.dsl.config.d","com.dragon.read.kmp.dsl.config.j","com.dragon.read.kmp.dsl.element.DynamicLinearLayoutKt","com.dragon.read.kmp.dsl.element.DynamicLoadImageKt","com.dragon.read.kmp.dsl.tool.f","f0.a$b","f0.d","f0.h","kotlin.jvm.functions.Function1","kotlin.jvm.functions.Function2","uv5.b"},new String[]{"androidx.compose.foundation.BackgroundElement#b()Landroidx/compose/ui/Modifier$c;","androidx.compose.foundation.BackgroundElement#update(Landroidx/compose/foundation/h;)V","androidx.compose.foundation.h#s(Lf0/d;)V","androidx.compose.runtime.MutableState#setValue(Ljava/lang/Object;)V","androidx.compose.runtime.State#getValue()Ljava/lang/Object;","androidx.compose.ui.Modifier#then(Landroidx/compose/ui/Modifier;)Landroidx/compose/ui/Modifier;","androidx.compose.ui.draw.i#s(Lf0/d;)V","androidx.compose.ui.graphics.c#a(Landroidx/compose/ui/graphics/g0;)Landroid/graphics/Canvas;","androidx.compose.ui.graphics.painter.Painter#f(Lf0/h;JFLandroidx/compose/ui/graphics/n0;)V","androidx.compose.ui.platform.AndroidComposeView#dispatchDraw(Landroid/graphics/Canvas;)V","com.bytedance.kmp.image.painter.DrawablePainter#j(Lf0/h;)V","com.dragon.read.kmp.dsl.config.DynamicViewKitKt#a(Landroidx/compose/ui/Modifier;Laq6/d;Luv5/b;Lcom/dragon/read/kmp/dsl/tool/f;Lcom/dragon/read/kmp/compose/common/uicontext/color/Theme;)Landroidx/compose/ui/Modifier;","com.dragon.read.kmp.dsl.config.d#e(Landroidx/compose/ui/Modifier;Laq6/s;Luv5/b;Lcom/dragon/read/kmp/dsl/tool/f;Lcom/dragon/read/kmp/compose/common/uicontext/color/Theme;Landroidx/compose/runtime/Composer;I)Landroidx/compose/ui/Modifier;","com.dragon.read.kmp.dsl.config.j#i(Laq6/i1;Luv5/b;Lcom/dragon/read/kmp/dsl/tool/f;)Ljava/lang/String;","com.dragon.read.kmp.dsl.element.DynamicLinearLayoutKt#a(Landroidx/compose/ui/Modifier;Laq6/s;Luv5/b;Lcom/dragon/read/kmp/dsl/tool/f;Landroidx/compose/runtime/Composer;I)V","com.dragon.read.kmp.dsl.element.DynamicLoadImageKt#a(Landroidx/compose/ui/Modifier;Laq6/s;Luv5/b;Lcom/dragon/read/kmp/dsl/tool/f;Landroidx/compose/runtime/Composer;I)V","f0.a$b#a()Landroidx/compose/ui/graphics/g0;","f0.h#U0()Lf0/a$b;","f0.h#c()J","kotlin.jvm.functions.Function1#invoke(Ljava/lang/Object;)Ljava/lang/Object;","kotlin.jvm.functions.Function2#invoke(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"},new String[]{"aq6.s#b:Ljava/lang/Integer;","aq6.s#c:Laq6/d;","aq6.s#d:Laq6/k0;","aq6.s#e:Ljava/util/List;","aq6.s#f:Laq6/g0;","com.bytedance.kmp.image.painter.DrawablePainter#f:Landroid/graphics/drawable/Drawable;"},new String[]{}))return;
        isolated("androidx.compose.ui.platform.AndroidComposeView.dispatchDraw(Canvas)",()->module.hook(method("androidx.compose.ui.platform.AndroidComposeView","dispatchDraw",Canvas.class)).intercept(chain->{
            View v=(View)chain.getThisObject(),old=drawingOwner.get();registerComposeOwner(v);drawingOwner.set(v);for(Card candidate:allCards())if(candidate.dynamicCover&&candidate.owner.get()==v)candidate.dynamicArea=0;
            try{return chain.proceed();}finally{if(old==null)drawingOwner.remove();else drawingOwner.set(old);}
        }));
        isolated("DynamicLinearLayoutKt.a",()->module.hook(method("com.dragon.read.kmp.dsl.element.DynamicLinearLayoutKt","a",cls("androidx.compose.ui.Modifier"),cls("aq6.s"),cls("uv5.b"),cls("com.dragon.read.kmp.dsl.tool.f"),cls("androidx.compose.runtime.Composer"),int.class)).intercept(chain->{
            Frame old=current.get();Object node=chain.getArg(1);State state=state(chain.getArg(3),chain.getArg(2));
            try{bind(node,state);}catch(Throwable e){failure("DynamicLinearLayoutKt.a.bind",e);}
            current.set(new Frame(node,state));
            try{
                // Text is attached at config.d.e, independent of same-method recursive entry hooks.
                if(state.textNodes.get(node)!=null)once("card text linear-entry");
                return chain.proceed();
            }finally{if(old==null)current.remove();else current.set(old);}
        }));
        isolated("DynamicLinearLayoutKt.a.deoptimize",()->module.deoptimize(method("com.dragon.read.kmp.dsl.element.DynamicLinearLayoutKt","a",cls("androidx.compose.ui.Modifier"),cls("aq6.s"),cls("uv5.b"),cls("com.dragon.read.kmp.dsl.tool.f"),cls("androidx.compose.runtime.Composer"),int.class)));
        isolated("config.d.e.text",()->module.hook(method("com.dragon.read.kmp.dsl.config.d","e",cls("androidx.compose.ui.Modifier"),cls("aq6.s"),cls("uv5.b"),cls("com.dragon.read.kmp.dsl.tool.f"),cls("com.dragon.read.kmp.compose.common.uicontext.color.Theme"),cls("androidx.compose.runtime.Composer"),int.class)).intercept(chain->{
            Object node=chain.getArg(1);State state=state(chain.getArg(3),chain.getArg(2));
            // This also binds when a compiled caller bypassed the Linear entry interceptor.
            try{bind(node,state);}catch(Throwable e){failure("config.d.e.bind",e);}
            // Bind the root background even when a compiled Linear caller bypasses its entry hook.
            Card rootCard=state.roots.get(node),previous=markingBackground.get();Object result;
            if(rootCard!=null)markingBackground.set(rootCard);
            try{result=chain.proceed();}finally{if(previous==null)markingBackground.remove();else markingBackground.set(previous);}
            if(rootCard!=null)tagBackgrounds(result,rootCard);
            Card dynamic=state.coverNodes.get(node);
            if(dynamic!=null&&dynamic.dynamicCover)try{once("card dynamic cover modifier-attached via=config.d.e");return bridge.append(result,dynamic.coverAction);}catch(Throwable e){failure("config.d.e.dynamicCover",e);return result;}
            Card card=state.textNodes.get(node);
            if(card==null)return result;
            once("card text config-helper reached");
            try{Object empty=publicField("androidx.compose.ui.Modifier","Companion").get(null);Object front=bridge.append(empty,card.textAction);Object modified=publicMethod("androidx.compose.ui.Modifier","then",cls("androidx.compose.ui.Modifier")).invoke(front,result);once("card text modifier-attached via=config.d.e prepend-padding");return modified;}
            catch(Throwable e){failure("config.d.e.appendDraw",e);return result;}
        }));
        isolated("DynamicLoadImageKt.a",()->module.hook(method("com.dragon.read.kmp.dsl.element.DynamicLoadImageKt","a",cls("androidx.compose.ui.Modifier"),cls("aq6.s"),cls("uv5.b"),cls("com.dragon.read.kmp.dsl.tool.f"),cls("androidx.compose.runtime.Composer"),int.class)).intercept(chain->{
            Object[] args=chain.getArgs().toArray();State state=state(args[3],args[2]);Card card=state.coverNodes.get(args[1]);
            if(card!=null)try{refreshCoverKey(card,args[1],args[2],args[3]);}catch(Throwable e){failure("DynamicLoadImageKt.a.refreshCoverKey",e);}
            if(card!=null||state.imageNodes.get(args[1])!=null)try{args[0]=bridge.append(args[0],card!=null?card.coverAction:nonCoverAction);args[5]=(((Number)args[5]).intValue()&~14)|4;if(card!=null)once("card cover modifier-attached");}catch(Throwable e){failure("DynamicLoadImageKt.a.appendDraw",e);}
            return chain.proceed(args);
        }));
        isolated("DynamicViewKitKt.a",()->module.hook(method("com.dragon.read.kmp.dsl.config.DynamicViewKitKt","a",cls("androidx.compose.ui.Modifier"),cls("aq6.d"),cls("uv5.b"),cls("com.dragon.read.kmp.dsl.tool.f"),cls("com.dragon.read.kmp.compose.common.uicontext.color.Theme"))).intercept(chain->{
            Frame frame=current.get();Card card=frame==null?null:frame.state.roots.get(frame.node),old=markingBackground.get();
            if(card==null||chain.getArg(1)!=field(frame.node,"c"))return chain.proceed();markingBackground.set(card);
            try{return chain.proceed();}finally{if(old==null)markingBackground.remove();else markingBackground.set(old);}
        }));
        // Every constructor is isolated, as are create and update; one missing member leaves others active.
        isolated("BackgroundElement.constructors",()->{for(Constructor<?> ctor:cls("androidx.compose.foundation.BackgroundElement").getDeclaredConstructors())isolated(ctor.toString(),()->module.hook(ctor).intercept(chain->{Object result=chain.proceed();Card card=markingBackground.get();if(card!=null)bgElements.put(chain.getThisObject(),card);return result;}));});
        isolated("BackgroundElement.b()",()->module.hook(method("androidx.compose.foundation.BackgroundElement","b")).intercept(chain->{Object result=chain.proceed();Card card=bgElements.get(chain.getThisObject());elementNodes.put(chain.getThisObject(),new WeakReference<>(result));nodeElements.put(result,new WeakReference<>(chain.getThisObject()));bindBackground(result,card,"create");return result;}));
        isolated("BackgroundElement.update(foundation.h)",()->module.hook(method("androidx.compose.foundation.BackgroundElement","update",cls("androidx.compose.foundation.h"))).intercept(chain->{Object result=chain.proceed();Card card=bgElements.get(chain.getThisObject());elementNodes.put(chain.getThisObject(),new WeakReference<>(chain.getArg(0)));nodeElements.put(chain.getArg(0),new WeakReference<>(chain.getThisObject()));bindBackground(chain.getArg(0),card,"update");return result;}));
        isolated("foundation.h.s(f0.d)",()->module.hook(method("androidx.compose.foundation.h","s",cls("f0.d"))).intercept(chain->{
            Object bgNode=chain.getThisObject();WeakReference<Object> element=nodeElements.get(bgNode);Card card=element==null?bgNodes.get(bgNode):bgElements.get(element.get());bindBackground(bgNode,card,"draw-reattach");if(card!=null){rememberOwner(card);syncSource(card);card.rootBgSkipped=false;event(card,"root","mapped=true source="+(card.source!=null));}if(card!=null&&card.drawVersion!=null)publicMethod("androidx.compose.runtime.State","getValue").invoke(card.drawVersion);if(enabled()&&card!=null&&card.source!=null&&!card.source.isRecycled()){
                try{card.rootBgSkipped=true;content(chain.getArg(0));return null;}catch(Throwable e){failure("foundation.h.s.A1",e);throw e;}
            }return chain.proceed();
        }));
        isolated("DrawablePainter.j(f0.h)",()->module.hook(method("com.bytedance.kmp.image.painter.DrawablePainter","j",cls("f0.h"))).intercept(chain->{
            Object painter=chain.getThisObject();Card scoped=coverDrawing.get();if(scoped!=null)painterCards.put(painter,new PainterBinding(scoped,activeKey(scoped)));
            Object result=chain.proceed();PainterBinding bound=painterCards.get(painter);Card card=scoped!=null?scoped:bound==null?null:bound.card.get();if(scoped==null&&card!=null&&!Objects.equals(bound.key,activeKey(card)))card=null;
            if(card==null)card=dynamicOwnerCard();
            if(card!=null&&(!card.dynamicCover||dynamicDraw(card,chain.getArg(0)))&&!Boolean.TRUE.equals(extracting.get()))try{if(card.dynamicCover)event(card,"dynamic-retry","via=DrawablePainter.j owner=matched");capture(card,painter);}catch(Throwable e){failure("DrawablePainter.j.capture",e);}return result;
        }));
        isolated("Painter.f(f0.h,long,float,n0)",()->module.hook(method("androidx.compose.ui.graphics.painter.Painter","f",cls("f0.h"),long.class,float.class,cls("androidx.compose.ui.graphics.n0"))).intercept(chain->{
            Object painter=chain.getThisObject();once("card painter runtime-class="+painter.getClass().getName());
            Object result=chain.proceed();Card card=coverDrawing.get();if(card==null)card=dynamicOwnerCard();if(card!=null)painterCards.put(painter,new PainterBinding(card,activeKey(card)));
            long drawSize=((Number)chain.getArg(1)).longValue();
            if(card!=null&&(!card.dynamicCover||dynamicSize(card,Float.intBitsToFloat((int)(drawSize>>>32)),Float.intBitsToFloat((int)drawSize)))&&!Boolean.TRUE.equals(extracting.get()))try{capture(card,painter);}catch(Throwable e){failure("Painter.f.capture("+painter.getClass().getName()+")",e);}
            return result;
        }));
    }
    private void tagBackgrounds(Object modifier,Card card){try{Class<?> f2=cls("kotlin.jvm.functions.Function2");Object visitor=Proxy.newProxyInstance(loader,new Class<?>[]{f2},(self,m,args)->{if(m.getName().equals(HostSymbols.methodName(f2,"invoke"))){Object element=args[1];if(element!=null&&element.getClass().getName().equals(HostSymbols.name("androidx.compose.foundation.BackgroundElement"))){if(card==null)bgElements.remove(element);else bgElements.put(element,card);WeakReference<Object> ref=elementNodes.get(element);if(ref!=null)bindBackground(ref.get(),card,"config.d.e");}return args[0];}if(m.getName().equals("hashCode"))return System.identityHashCode(self);if(m.getName().equals("equals"))return self==args[0];return "PulpBackgroundVisitor";});publicMethod("androidx.compose.ui.Modifier","foldIn",Object.class,f2).invoke(modifier,null,visitor);}catch(Throwable e){failure("card.tagBackgrounds",e);}}
    /** Call from existing lifecycle tick on setting change and during async image loads. */
    void invalidateAll(){if(square!=null)square.invalidateAll();synchronized(composeOwners){for(View v:new ArrayList<>(composeOwners))if(v!=null&&v.isAttachedToWindow())v.postInvalidateOnAnimation();}}
    /** Integration may register the host Compose view while discovering existing native sources. */
    void registerComposeOwner(View v){if(v!=null)synchronized(composeOwners){if(composeOwners.add(v))v.addOnAttachStateChangeListener(ownerLifecycle);}}
    private State state(Object tool,Object data){Object key=data!=null?data:tool;State s=states.get(key);if(s==null){s=new State();s.data=new WeakReference<>(data);states.put(key,s);}return s;}
    private void bind(Object root,State state){
        if(!Integer.valueOf(100).equals(field(root,"b"))||field(root,"c")==null)return;
        List<?> children=list(field(root,"e"));if(children.size()!=2)return;
        Object coverBox=children.get(0),text=children.get(1);
        if(!cardShape(root))return;
        boolean dynamic=Integer.valueOf(3).equals(field(coverBox,"b"));
        Object cover=dynamic?coverBox:selectCover(list(field(coverBox,"e")));
        if(cover==null)return;
        Card card=state.roots.get(root);if(card==null){card=new Card();cardRefs.add(new WeakReference<>(card));event(card,"new","source=false");try{Method factory=publicMethod("androidx.compose.runtime.SnapshotStateKt","mutableStateOf$default",Object.class,cls("androidx.compose.runtime.SnapshotMutationPolicy"),int.class,Object.class);factory.setAccessible(true);card.drawVersion=factory.invoke(null,0,null,2,null);}catch(Throwable e){failure("card.drawVersion.create",e);}final Card c=card;
            c.textAction=new CachedAction(scope->{once("card text modifier-draw");rememberOwner(c);try{drawText(c,scope);}catch(Throwable e){failure("DrawModifier.text.drawText",e);}finally{content(scope);}diagnose(c);});
            c.coverAction=new CachedAction(scope->{once("card cover modifier-draw");drawCover(c,scope);});
            card.dynamicCover=dynamic;state.roots.put(root,card);once("card bind root=100 coverBox="+(dynamic?3:101)+" text=100 children=2");}Object oldText=card.textNode.get(),oldCover=card.coverNode.get();if(oldText!=text||oldCover!=cover){state.textNodes.remove(oldText);state.coverNodes.remove(oldCover);card.textNode=new WeakReference<>(text);card.coverNode=new WeakReference<>(cover);}
        if(oldText!=text||oldCover!=cover)event(card,"rebind","nodes=changed");state.textNodes.put(text,card);state.coverNodes.put(cover,card);markImageSubtree(coverBox,state,card,0);
    }
    private static void markImageSubtree(Object node,State state,Card card,int depth){
        if(depth>8)return;if(field(node,"f")!=null)state.imageNodes.put(node,card);
        for(Object child:list(field(node,"e")))markImageSubtree(child,state,card,depth+1);
    }
    /** Exact two-part DSL card; type 3 is the published chapter dynamic cover slot. */
    static boolean cardShape(Object root){List<?> children=list(field(root,"e"));if(!Integer.valueOf(100).equals(field(root,"b"))||field(root,"c")==null||children.size()!=2)return false;Object box=children.get(0),text=children.get(1);Object kind=field(box,"b");return (Integer.valueOf(101).equals(kind)||Integer.valueOf(3).equals(kind))&&Integer.valueOf(100).equals(field(text,"b"))&&field(text,"c")==null;}
    static boolean mainCoverDraw(float w,float h,float slotWidth){return Float.isFinite(w)&&Float.isFinite(h)&&slotWidth>0&&w>=slotWidth*.8f&&h>=w*.65f;}
    /** Observed 73932 cover: direct image child, match-parent width and wrap-content height. */
    static Object selectCover(List<?> children){
        Object cover=null;
        for(Object child:children){Object layout=field(child,"d");if(!Integer.valueOf(1).equals(field(child,"b"))||field(child,"f")==null||!SourceRules.numberEquals(field(layout,"a"),-1)||!SourceRules.numberEquals(field(layout,"b"),-2))continue;
            if(cover!=null)return null;cover=child;
        }return cover;
    }
    /** JVM-testable rules; node/model allocation identities are deliberately absent. */
    static final class SourceRules {
        static boolean numberEquals(Object value,double expected){return value instanceof Number&&((Number)value).doubleValue()==expected;}
        static String cacheKey(String day,String night,boolean dark){String url=known(dark?night:day);if(url==null&&dark)url=known(day);return url==null?null:(dark?"night:":"day:")+url;}
        static boolean dark(int argb){return (((argb>>>16)&255)*299+((argb>>>8)&255)*587+(argb&255)*114)<85000;}
        static String known(String value){if(value==null)return null;String s=value.trim();return s.isEmpty()?null:s;}
        static final class Identity {
            String day,night;
            boolean update(String nextDay,String nextNight){
                nextDay=known(nextDay);nextNight=known(nextNight);
                boolean changed=(day!=null&&nextDay!=null&&!day.equals(nextDay))||(night!=null&&nextNight!=null&&!night.equals(nextNight));
                if(changed){day=nextDay;night=nextNight;}else{if(nextDay!=null)day=nextDay;if(nextNight!=null)night=nextNight;}
                return changed;
            }
        }
    }
    private void capture(Card card,Object painter)throws Throwable{
        String name=painter.getClass().getName();Drawable drawable=null;Bitmap actual=null;
        card.painterClass=name;
        if(name.equals(HostSymbols.name("com.bytedance.kmp.image.painter.DrawablePainter"))){Object raw=exactField(painter,"com.bytedance.kmp.image.painter.DrawablePainter","f");if(raw instanceof Drawable){drawable=(Drawable)raw;actual=drawableBitmap(drawable,Collections.newSetFromMap(new IdentityHashMap<>()),0);once("card drawable type="+drawable.getClass().getName()+" intrinsic="+drawable.getIntrinsicWidth()+"x"+drawable.getIntrinsicHeight()+" bounds="+drawable.getBounds()+" bitmap="+(actual==null?"none":actual.getWidth()+"x"+actual.getHeight()));}}
        else if(name.equals(HostSymbols.name("androidx.compose.ui.graphics.painter.a"))&&bitmapPainterSymbols()){Object image=exactField(painter,"androidx.compose.ui.graphics.painter.a","f");if(image!=null)actual=(Bitmap)method("androidx.compose.ui.graphics.j","a",cls("androidx.compose.ui.graphics.g1")).invoke(null,image);}
        else {if(card.painter.get()!=painter){report("card cover-change id="+System.identityHashCode(card)+" event="+(++card.coverEvent)+" painter="+name+" painterId="+System.identityHashCode(painter)+" bitmap=unavailable");card.painter=new WeakReference<>(painter);}return;}
        if(!card.dynamicCover){if(activeKey(card)==null)return;}
        // Resource placeholders, missing painters and recycled loader inputs are not book changes.
        if(actual==null&&drawable==null)return;
        long now=android.os.SystemClock.uptimeMillis();
        if(actual!=null&&actual.isRecycled())return;
        int generation=actual==null?0:actual.getGenerationId();
        if(card.painter.get()!=painter||card.observedBitmap.get()!=actual||card.observedGeneration!=generation){
            report("card cover-change id="+System.identityHashCode(card)+" event="+(++card.coverEvent)+" painter="+name+" painterId="+System.identityHashCode(painter)+" bitmap="+(actual==null?"null":actual.getWidth()+"x"+actual.getHeight()+"@"+System.identityHashCode(actual))+" generation="+generation+" config="+(actual==null?"drawable":actual.getConfig()));
            card.painter=new WeakReference<>(painter);card.observedBitmap=new WeakReference<>(actual);card.observedGeneration=generation;
        }
        if(card.drawable.get()==drawable&&card.bitmap.get()==actual&&card.source!=null&&card.generation==generation&&(actual!=null||now-card.stamp<500))return;
        if(card.rejection.skip(actual,generation,drawable,now))return;
        extracting.set(true);
        try{
            Bitmap source;
            if(actual!=null&&!actual.isRecycled()){
                int w=Math.max(1,Math.min(160,actual.getWidth())),h=Math.max(1,Math.round(actual.getHeight()*(w/(float)actual.getWidth())));
                h=Math.min(240,h);source=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                // Software Canvas rejects HARDWARE loader bitmaps; retain an independent software copy.
                Bitmap readable=actual.getConfig()==Bitmap.Config.HARDWARE?actual.copy(Bitmap.Config.ARGB_8888,false):actual;
                if(readable==null)return;
                try{new Canvas(source).drawBitmap(readable,null,new Rect(0,0,w,h),new Paint(Paint.FILTER_BITMAP_FLAG));}finally{if(readable!=actual)readable.recycle();}
            }else{
                int iw=drawable.getIntrinsicWidth(),ih=drawable.getIntrinsicHeight();Rect bounds=drawable.getBounds();if(iw<=0)iw=bounds.width();if(ih<=0)ih=bounds.height();if(iw<=0||ih<=0){once("card drawable-await-bounds type="+drawable.getClass().getName());return;}
                int w=Math.min(160,iw),h=Math.max(1,Math.min(240,Math.round(ih*(w/(float)iw))));
                source=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Rect old=new Rect(drawable.getBounds());
                try{drawable.setBounds(0,0,w,h);drawable.draw(new Canvas(source));}finally{drawable.setBounds(old);}
            }
            int[] pixels=new int[source.getWidth()*source.getHeight()];source.getPixels(pixels,0,source.getWidth(),0,0,source.getWidth(),source.getHeight());
            int average=bottomAveragePixels(pixels,source.getWidth(),source.getHeight());
            // Uniform white/transparent loader placeholders must never replace a ready cover.
            // Bitmap generations invalidate rejection immediately; mutable wrappers retry after 180ms.
            if(placeholderPixels(pixels)){card.rejection.reject(actual,generation,drawable,now);report("card placeholder id="+System.identityHashCode(card)+" painter="+name+" bottomAvgARGB="+argb(average));source.recycle();return;}
            card.rejection.clear();
            if(card.dynamicCover){long hash=1469598103934665603L;for(int pixel:pixels){hash^=pixel;hash*=1099511628211L;}card.identityResolved=true;card.resolvedDay="dynamic-content:"+source.getWidth()+"x"+source.getHeight()+":"+Long.toHexString(hash);card.resolvedNight=card.resolvedDay;event(card,"dynamic-source","identity=content-digest painter="+name);}
            card.bottomAvgARGB=average;card.sourcePainterClass=name;
            String key=activeKey(card);card.sourceKey=key;if(key!=null)sourceCache.put(key,new CachedSource(source,average,name));
            report("card source-pixels id="+System.identityHashCode(card)+" event="+(card.sourceEvent+1)+" painter="+name+" src="+source.getWidth()+"x"+source.getHeight()+" bottomAvgARGB="+argb(average));
            // Do not recycle old bitmaps while a RenderNode display list may reference them.
            card.source=source;card.drawable=new WeakReference<>(drawable);card.bitmap=new WeakReference<>(actual);card.stamp=now;card.generation=generation;once("card source-ready "+name+" card="+System.identityHashCode(card)+" event="+(++card.sourceEvent));card.software=null;bumpDraw(card);if(card.render!=null)card.render.discardDisplayList();card.render=null;invalidateAll();
        }finally{extracting.remove();}
    }
    private void drawText(Card card,Object scope)throws Throwable{
        syncSource(card);
        if(card.drawVersion!=null)publicMethod("androidx.compose.runtime.State","getValue").invoke(card.drawVersion);
        if(!enabled()){once("card skip disabled card="+System.identityHashCode(card));return;}
        if(card.source==null||card.source.isRecycled()){once("card skip source-null-or-recycled card="+System.identityHashCode(card));return;}
        long size=((Number)publicMethod("f0.h","c").invoke(scope)).longValue();
        float width=Float.intBitsToFloat((int)(size>>>32)),height=Float.intBitsToFloat((int)size);
        if(!Float.isFinite(width)||!Float.isFinite(height)||width<=0||height<=0){once("card skip size card="+System.identityHashCode(card)+" raw="+Long.toHexString(size)+" w="+width+" h="+height);return;}
        Object context=publicMethod("f0.h","U0").invoke(scope);Object composeCanvas=publicMethod("f0.a$b","a").invoke(context);
        Canvas canvas=(Canvas)publicMethod("androidx.compose.ui.graphics.c","a",cls("androidx.compose.ui.graphics.g0")).invoke(null,composeCanvas);
        boolean hardware=canvas.isHardwareAccelerated();event(card,"render-state","source=true has="+(card.render!=null&&card.render.hasDisplayList())+" hw="+hardware+" root="+card.rootBgSkipped);once("card canvas card="+System.identityHashCode(card)+" type="+canvas.getClass().getName()+" hardware="+hardware+" w="+width+" h="+height);
        View owner=card.owner.get();float density=owner!=null?owner.getResources().getDisplayMetrics().density:((Number)method("e1.e","getDensity").invoke(scope)).floatValue();
        float radius=8f*density;
        int rw=Math.max(1,Math.round(width*.25f)),rh=Math.max(1,Math.round(height*.25f));
        if(hardware&&(card.render==null||!card.render.hasDisplayList()||card.rw!=rw||card.rh!=rh)){
            event(card,"render-rebuild","reason="+(card.render==null?"null":!card.render.hasDisplayList()?"display-list-lost":"size")+" root="+card.rootBgSkipped);
            if(card.render!=null)card.render.discardDisplayList();RenderNode node=new RenderNode("PulpCardCoverOnly");node.setPosition(0,0,rw,rh);
            RecordingCanvas recording=node.beginRecording(rw,rh);try{recording.drawBitmap(card.source,new Rect(0,Math.min(card.source.getHeight()-1,(int)(card.source.getHeight()*.65f)),card.source.getWidth(),card.source.getHeight()),new Rect(0,0,rw,rh),new Paint(Paint.FILTER_BITMAP_FLAG));}finally{node.endRecording();}
            node.setRenderEffect(RenderEffect.createBlurEffect(5.5f*density,5.5f*density,Shader.TileMode.CLAMP));card.render=node;card.rw=rw;card.rh=rh;
        }
        int saved=canvas.save();try{Path clip=new Path();clip.addRoundRect(new RectF(0,0,width,height),new float[]{0,0,0,0,radius,radius,radius,radius},Path.Direction.CW);canvas.clipPath(clip);int scaled=canvas.save();canvas.scale(width/rw,height/rh);if(hardware)canvas.drawRenderNode(card.render);else{if(card.software==null||card.sw!=rw||card.sh!=rh){card.software=gaussianCover(card.source,rw,rh,5.5f*density);card.sw=rw;card.sh=rh;}canvas.drawBitmap(card.software,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));}canvas.restoreToCount(scaled);Paint tint=new Paint();if(night())tint.setColor(0xA61E2026);else{int bottom=SourceRules.dark(card.bottomAvgARGB)?0x78FFFFFF:0x72FFFFFF;tint.setShader(new LinearGradient(0,0,0,height,0x52FFFFFF,bottom,Shader.TileMode.CLAMP));}canvas.drawRect(0,0,width,height,tint);tint.setShader(null);tint.setColor(night()?0x28ffffff:0x99FFFFFF);canvas.drawRect(0,0,width,.75f*density,tint);}finally{canvas.restoreToCount(saved);}
        card.lastHardware=hardware;card.textWidth=width;card.textHeight=height;card.textDrawn=true;
        report("tier=medium kind=cardText blurDp=22 source=card-cover-bottom35");
    }
    private void diagnose(Card card){
        if(!card.textDrawn||card.source==null)return;
        if(!card.diagnosed){synchronized(this){if(!card.diagnosed){card.diagnosed=true;if(diagnosticCards<3){diagnosticCards++;report("card diag id="+System.identityHashCode(card)+" painter="+card.sourcePainterClass+" src="+card.source.getWidth()+"x"+card.source.getHeight()+" bottomAvgARGB="+argb(card.bottomAvgARGB)+" rootBgSkipped="+card.rootBgSkipped+" hw="+card.lastHardware+" textBox="+card.textWidth+"x"+card.textHeight);}}}}
    }
    private static String argb(int color){return String.format(java.util.Locale.ROOT,"#%08X",color);}
    static int bottomAveragePixels(int[] pixels,int width,int height){long a=0,r=0,g=0,b=0,n=0;for(int i=(int)(height*.65f)*width;i<pixels.length;i++){int p=pixels[i];a+=p>>>24;r+=(p>>>16)&255;g+=(p>>>8)&255;b+=p&255;n++;}return n==0?0:((int)(a/n)<<24)|((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);}
    static boolean placeholderPixels(int[] pixels){for(int p:pixels)if((p>>>24)>96&&(((p>>>16)&255)<248||((p>>>8)&255)<248||(p&255)<248))return false;return true;}
    private static int bottomAverage(Bitmap source){
        long a=0,r=0,g=0,b=0,n=0;int top=(int)(source.getHeight()*.65f);
        for(int y=top;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){int p=source.getPixel(x,y);a+=p>>>24;r+=(p>>>16)&255;g+=(p>>>8)&255;b+=p&255;n++;}
        return n==0?0:((int)(a/n)<<24)|((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);
    }
    private static Bitmap drawableBitmap(Drawable drawable,Set<Drawable> visited,int depth){
        if(drawable==null||depth>8||!visited.add(drawable))return null;
        if(drawable instanceof BitmapDrawable)return ((BitmapDrawable)drawable).getBitmap();
        Drawable current=drawable.getCurrent();if(current!=drawable){Bitmap b=drawableBitmap(current,visited,depth+1);if(b!=null)return b;}
        // Inspect Drawable-typed wrapper fields only: never walk Context/loader/global object graphs.
        for(Class<?> c=drawable.getClass();c!=null&&Drawable.class.isAssignableFrom(c);c=c.getSuperclass())for(Field f:c.getDeclaredFields()){if(Modifier.isStatic(f.getModifiers()))continue;try{f.setAccessible(true);Object value=f.get(drawable);if(value instanceof Bitmap)return (Bitmap)value;if(value instanceof Drawable){Bitmap b=drawableBitmap((Drawable)value,visited,depth+1);if(b!=null)return b;}if(value instanceof Drawable[])for(Drawable child:(Drawable[])value){Bitmap b=drawableBitmap(child,visited,depth+1);if(b!=null)return b;}}catch(Throwable ignored){}}
        return null;
    }
    private static boolean placeholder(Bitmap source){
        // Whole-image check protects real covers whose bottom strip happens to be white.
        for(int y=0;y<source.getHeight();y++)for(int x=0;x<source.getWidth();x++){int p=source.getPixel(x,y);if((p>>>24)>96&&(((p>>>16)&255)<248||((p>>>8)&255)<248||(p&255)<248))return false;}
        return true;
    }
    private void bumpDraw(Card c){if(c.drawVersion!=null)try{publicMethod("androidx.compose.runtime.MutableState","setValue",Object.class).invoke(c.drawVersion,++c.revision);}catch(Throwable e){failure("card.drawVersion.set",e);}}
    /** Separable Gaussian over independent cover pixels only; sigma is in quarter-size pixels. */
    private static Bitmap gaussianCover(Bitmap source,int w,int h,float sigma){
        Bitmap out=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(source,new Rect(0,Math.min(source.getHeight()-1,(int)(source.getHeight()*.65f)),source.getWidth(),source.getHeight()),new Rect(0,0,w,h),new Paint(Paint.FILTER_BITMAP_FLAG));
        int n=w*h;int[] pixels=new int[n],tmp=new int[n],result=new int[n];out.getPixels(pixels,0,w,0,0,w,h);
        sigma=Math.max(.5f,sigma);int radius=Math.max(1,(int)Math.ceil(3*sigma));float[] kernel=new float[2*radius+1];float sum=0;
        for(int k=-radius;k<=radius;k++){float weight=(float)Math.exp(-(k*(double)k)/(2*sigma*sigma));kernel[k+radius]=weight;sum+=weight;}for(int k=0;k<kernel.length;k++)kernel[k]/=sum;
        for(int pass=0;pass<2;pass++){int[] input=pass==0?pixels:tmp,output=pass==0?tmp:result;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++){float a=0,r=0,g=0,b=0;for(int k=-radius;k<=radius;k++){int xx=pass==0?Math.max(0,Math.min(w-1,x+k)):x,yy=pass==0?y:Math.max(0,Math.min(h-1,y+k));int p=input[yy*w+xx];float weight=kernel[k+radius],alpha=(p>>>24)/255f;a+=alpha*weight;r+=((p>>>16)&255)*alpha*weight;g+=((p>>>8)&255)*alpha*weight;b+=(p&255)*alpha*weight;}int ai=Math.max(0,Math.min(255,Math.round(a*255))),ri=a>0?Math.max(0,Math.min(255,Math.round(r/a))):0,gi=a>0?Math.max(0,Math.min(255,Math.round(g/a))):0,bi=a>0?Math.max(0,Math.min(255,Math.round(b/a))):0;output[y*w+x]=(ai<<24)|(ri<<16)|(gi<<8)|bi;}
        }out.setPixels(result,0,w,0,0,w,h);return out;
    }
    private boolean night(){try{return Boolean.TRUE.equals(publicMethod("com.dragon.read.base.skin.SkinManager","isNightMode").invoke(null));}catch(Throwable failure){synchronized(composeOwners){for(View v:composeOwners)if(v!=null)return (v.getResources().getConfiguration().uiMode&48)==32;}return false;}}
    private static final class State {WeakReference<Object> data=new WeakReference<>(null);final IdentityWeakMap<Card> roots=new IdentityWeakMap<>(),textNodes=new IdentityWeakMap<>(),coverNodes=new IdentityWeakMap<>(),imageNodes=new IdentityWeakMap<>();}
    private static final class Frame {final Object node;final State state;Frame(Object n,State s){node=n;state=s;}}
    private void content(Object scope)throws Throwable{publicMethod("f0.d","A1").invoke(scope);}
    private static boolean enabled(){GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();return Build.VERSION.SDK_INT>=31&&g!=null&&g.cardText();}
    private void report(String message){if(message==null||(!GlobalGlassSurfaces.debugEnabled()&&!message.contains("error")&&!message.contains("FAILED")&&!message.contains("Exception")))return;synchronized(seen){if(!seen.add("log:"+message))return;}if(message.startsWith("tier=")){GlobalGlassSurfaces g=GlobalGlassSurfaces.instance();if(g!=null)g.logApplied(message);}else AsyncLog.log(module,android.util.Log.INFO,"FanqieCustomizer","GlobalGlass "+message);}
    private static List<?> list(Object o){return o instanceof List?(List<?>)o:Collections.emptyList();}
    private static Object field(Object o,String n){if(o==null)return null;for(Class<?> c=o.getClass();c!=null;c=c.getSuperclass())try{Field f=c.getDeclaredField(HostSymbols.fieldName(c,n));f.setAccessible(true);return f.get(o);}catch(NoSuchFieldException ignored){}catch(ReflectiveOperationException ignored){return null;}return null;}
    private interface Drawing {void run(Object scope)throws Throwable;}
    private static final class CachedAction implements DrawAction {Object proxy;final Drawing action;CachedAction(Drawing a){action=a;}public void draw(Object scope)throws Throwable{action.run(scope);}}
    /** Owner fallback is admitted only when exactly one card belongs to the currently drawing Compose owner. */
    private Card dynamicOwnerCard(){View owner=drawingOwner.get();if(owner==null)return null;Card match=null;for(Card candidate:allCards())if(candidate.owner.get()==owner){if(match!=null)return null;match=candidate;}return match!=null&&match.dynamicCover?match:null;}
    private float dynamicWidth(Card c){View owner=c.owner.get();return c.coverWidth>0?c.coverWidth:owner==null?0:owner.getWidth();}
    private boolean dynamicSize(Card c,float w,float h){if(c.owner.get()==null||c.owner.get()!=drawingOwner.get()||!mainCoverDraw(w,h,dynamicWidth(c)))return false;float area=w*h;if(area+1<c.dynamicArea)return false;c.dynamicArea=area;return true;}
    private boolean dynamicDraw(Card c,Object scope){try{long packed=((Number)publicMethod("f0.h","c").invoke(scope)).longValue();return dynamicSize(c,Float.intBitsToFloat((int)(packed>>>32)),Float.intBitsToFloat((int)packed));}catch(Throwable e){failure("dynamic.draw-size",e);return false;}}
    private void rememberOwner(Card c){View v=drawingOwner.get();if(v!=null){c.owner=new WeakReference<>(v);registerComposeOwner(v);}}
    private void refreshCoverKey(Card c,Object node,Object data,Object tool)throws Throwable{
        Object config=field(node,"f");String[] resolved=new String[2];
        // Resolve transactionally: a failed/partial model read must not erase the last good identity.
        String[] names={"d","e"};
        for(int i=0;i<names.length;i++){Object value=field(config,names[i]);resolved[i]=value==null?null:(String)method("com.dragon.read.kmp.dsl.config.j","i",cls("aq6.i1"),cls("uv5.b"),cls("com.dragon.read.kmp.dsl.tool.f")).invoke(null,value,data,tool);}
        if(c.coverIdentity.update(resolved[0],resolved[1]))clearSource(c,"cover-url-changed");
        c.identityResolved=SourceRules.known(resolved[0])!=null||SourceRules.known(resolved[1])!=null;c.resolvedDay=SourceRules.known(resolved[0]);c.resolvedNight=SourceRules.known(resolved[1]);syncSource(c);
    }
    private void drawCover(Card c,Object scope)throws Throwable{rememberOwner(c);if(c.dynamicCover){long size=((Number)publicMethod("f0.h","c").invoke(scope)).longValue();c.coverWidth=Float.intBitsToFloat((int)(size>>>32));}boolean hw=false;try{hw=nativeCanvas(scope).isHardwareAccelerated();}catch(Throwable e){failure("cover.canvas",e);}event(c,"cover",hw?"hw=true":"hw=false");Card prev=coverDrawing.get();coverDrawing.set(c);try{content(scope);}finally{if(prev==null)coverDrawing.remove();else coverDrawing.set(prev);}}
    private Canvas nativeCanvas(Object scope)throws Throwable{Object context=publicMethod("f0.h","U0").invoke(scope);Object cc=publicMethod("f0.a$b","a").invoke(context);return (Canvas)publicMethod("androidx.compose.ui.graphics.c","a",cls("androidx.compose.ui.graphics.g0")).invoke(null,cc);}
    private void bindBackground(Object node,Card card,String reason){if(node==null)return;Card old=bgNodes.get(node);if(card==null)bgNodes.remove(node);else bgNodes.put(node,card);if(card!=null&&old!=card)event(card,"rebind","reason="+reason+" node="+System.identityHashCode(node)+" root=mapped");}
    private List<Card> allCards(){List<Card> out=new ArrayList<>();for(Iterator<WeakReference<Card>> it=cardRefs.iterator();it.hasNext();){Card c=it.next().get();if(c==null)it.remove();else out.add(c);}return out;}
    private void event(Card c,String name,String detail){if(!GlobalGlassSurfaces.debugEnabled())return;if(!detail.equals(c.lastEvents.put(name,detail)))report("card "+name+" id="+System.identityHashCode(c)+" event="+(++c.eventSerial)+" rev="+c.revision+" "+detail);}
    private String activeKey(Card c){return c.identityResolved?SourceRules.cacheKey(c.resolvedDay,c.resolvedNight,night()):null;}
    private void syncSource(Card c){String key=activeKey(c);if(c.source!=null&&!Objects.equals(c.sourceKey,key)){c.source=null;c.software=null;if(c.render!=null)c.render.discardDisplayList();c.render=null;bumpDraw(c);}if(key==null){event(c,"source","resolved=false ready="+(c.source!=null));return;}if(c.source!=null)return;CachedSource cached=sourceCache.get(key);if(cached==null||cached.bitmap.isRecycled()){event(c,"cache-miss","key="+Integer.toHexString(key.hashCode()));return;}c.source=cached.bitmap;c.sourceKey=key;c.bottomAvgARGB=cached.average;c.sourcePainterClass=cached.painter;c.software=null;c.render=null;bumpDraw(c);event(c,"cache-hit","key="+Integer.toHexString(key.hashCode())+" mode="+(night()?"night":"day"));invalidateAll();}
    static final class SourceCache<V>{final int limit;final LinkedHashMap<String,V> entries=new LinkedHashMap<>(16,.75f,true);SourceCache(int limit){this.limit=limit;}synchronized V get(String key){return key==null?null:entries.get(key);}synchronized void put(String key,V value){if(key==null||value==null)return;entries.put(key,value);while(entries.size()>limit){Iterator<String> it=entries.keySet().iterator();it.next();it.remove();}}synchronized int size(){return entries.size();}}
    private static final class PainterBinding{final WeakReference<Card> card;final String key;PainterBinding(Card c,String k){card=new WeakReference<>(c);key=k;}}
    private static final class CachedSource{final Bitmap bitmap;final int average;final String painter;CachedSource(Bitmap b,int a,String p){bitmap=b;average=a;painter=p;}}
    private Object exactField(Object o,String owner,String name)throws Throwable{Field f=declaredField(owner,name);f.setAccessible(true);return f.get(o);}
    private void clearSource(Card c,String reason){if(c.source==null)return;once("card source-clear reason="+reason+" card="+System.identityHashCode(c)+" event="+(++c.sourceEvent));c.source=null;c.software=null;bumpDraw(c);if(c.render!=null)c.render.discardDisplayList();c.render=null;invalidateAll();}
    /** Per-card rejection only: never publishes placeholder pixels to sourceCache. */
    static final class CaptureRejection {
        static final long COOLDOWN_MS=180;
        private WeakReference<Object> rejectedBitmap=new WeakReference<>(null),rejectedDrawable=new WeakReference<>(null);
        private int rejectedGeneration;
        private long lastRejectStamp;
        private boolean rejected;
        boolean skip(Object actual,int generation,Object drawable,long now){
            if(!rejected)return false;
            if(actual!=null)return actual==rejectedBitmap.get()&&generation==rejectedGeneration;
            return rejectedBitmap.get()==null&&drawable!=null&&drawable==rejectedDrawable.get()&&now>=lastRejectStamp&&now-lastRejectStamp<COOLDOWN_MS;
        }
        void reject(Object actual,int generation,Object drawable,long now){
            rejectedBitmap=new WeakReference<>(actual);rejectedDrawable=new WeakReference<>(actual==null?drawable:null);
            rejectedGeneration=generation;lastRejectStamp=now;rejected=true;
        }
        void clear(){rejected=false;rejectedBitmap.clear();rejectedDrawable.clear();}
    }
    private static final class Card {final CaptureRejection rejection=new CaptureRejection();int eventSerial;String sourceKey,resolvedDay,resolvedNight;boolean identityResolved,dynamicCover;float coverWidth,dynamicArea;final Map<String,String> lastEvents=new HashMap<>();Object drawVersion;int revision,sourceEvent,coverEvent,observedGeneration,bottomAvgARGB;boolean rootBgSkipped,diagnosed,lastHardware,textDrawn;float textWidth,textHeight;String painterClass="none",sourcePainterClass="none";WeakReference<Object> painter=new WeakReference<>(null);WeakReference<Bitmap> observedBitmap=new WeakReference<>(null);Bitmap software;int sw,sh;final SourceRules.Identity coverIdentity=new SourceRules.Identity();WeakReference<Object> textNode=new WeakReference<>(null),coverNode=new WeakReference<>(null);CachedAction textAction,coverAction;WeakReference<View> owner=new WeakReference<>(null);int generation;WeakReference<Drawable> drawable=new WeakReference<>(null);WeakReference<Bitmap> bitmap=new WeakReference<>(null);Bitmap source;RenderNode render;int rw,rh;long stamp;}
    /** Weak identity keys: host Modifier.equals compares values and must not merge cards. */
    private static final class IdentityWeakMap<V>{
        final ReferenceQueue<Object> queue=new ReferenceQueue<>();final Map<Key,V> values=new HashMap<>();
        synchronized V get(Object key){clean();return key==null?null:values.get(new Key(key,null));}
        synchronized void put(Object key,V value){if(key!=null){clean();values.put(new Key(key,queue),value);}}
        synchronized void remove(Object key){clean();if(key!=null)values.remove(new Key(key,null));}
        synchronized void removeValue(V value){clean();values.values().removeIf(v->v==value);}
        void clean(){Reference<?> r;while((r=queue.poll())!=null)values.remove(r);}
        static final class Key extends WeakReference<Object>{final int hash;Key(Object o,ReferenceQueue<Object> q){super(o,q);hash=System.identityHashCode(o);}public int hashCode(){return hash;}public boolean equals(Object o){return this==o||(o instanceof Key&&get()!=null&&get()==((Key)o).get());}}
    }
}
