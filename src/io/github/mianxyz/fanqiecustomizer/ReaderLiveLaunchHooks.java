package io.github.mianxyz.fanqiecustomizer;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Build;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

/** A live plugin Activity can look like a popup while never calling Dialog.show(). */
final class ReaderLiveLaunchHooks {
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    ReaderLiveLaunchHooks(XposedModule module,FeatureHooks.Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}

    void install(){
        if(!host.on("hide_reader_ads")||!host.on("hide_reader_live_popup"))return;
        launchMethods("com.bytedance.mira.hook.delegate.MiraInstrumentation","execStartActivity");
        launchMethods("android.app.Activity","startActivityForResult");
    }
    private void launchMethods(String className,String methodName){
        int count=0;
        try{
            Class<?> type=Class.forName(className,false,loader);
            for(Method method:type.getDeclaredMethods()){
                if(!methodName.equals(method.getName()))continue;
                Class<?>[] parameters=method.getParameterTypes();int intentIndex=-1;
                for(int i=0;i<parameters.length;i++)if(parameters[i]==Intent.class){if(intentIndex>=0){intentIndex=-1;break;}intentIndex=i;}
                if(intentIndex<0||!(method.getReturnType()==void.class||method.getReturnType().getName().equals("android.app.Instrumentation$ActivityResult")))continue;
                final int index=intentIndex;
                module.hook(method).intercept(chain->{
                    try{
                        String target=liveTarget((Intent)chain.getArg(index));
                        if(target!=null){
                            String owner=readerOwner(chain.getThisObject());
                            for(Object argument:chain.getArgs())if(owner==null)owner=readerOwner(argument);
                            if(ReaderAdRules.blockLiveLaunch(host.on("hide_reader_ads"),host.on("hide_reader_live_popup"),owner,host.readerVisible(),target)){
                                host.log("Reader live Activity launch refused: "+target+" via "+className+"."+methodName);
                                return null;
                            }
                        }
                    }catch(Throwable error){host.log("Reader live launch adaptation failed: "+error.getClass().getSimpleName());}
                    return chain.proceed();
                });count++;
            }
        }catch(Throwable error){host.log("Reader live launch hook unavailable: "+className+" "+error.getClass().getSimpleName());}
        host.log("Reader live Activity launch hooks registered: "+className+"."+methodName+" = "+count);
    }
    void created(Activity activity,String lastPage,long sinceReaderPaused){
        String target=activity.getClass().getName();
        if(!ReaderAdRules.livePlayer(target))return;
        host.log("Reader live Activity observed: "+target);
        boolean enabled=host.on("hide_reader_ads"),guard=host.on("hide_reader_live_popup");
        if(!ReaderAdRules.blockLiveLaunch(enabled,guard,null,host.readerVisible(),target)
            &&!ReaderAdRules.blockLiveTransition(enabled,guard,lastPage,sinceReaderPaused,target))return;
        try{
            // onCreate has completed; finish normally before its window becomes foreground.
            activity.finish();
            host.log("Reader live Activity closed before foreground: "+target);
        }catch(Throwable error){host.log("Reader live Activity cleanup failed: "+error.getClass().getSimpleName());}
    }
    @SuppressWarnings("deprecation")
    private static String liveTarget(Intent intent){
        for(int depth=0;depth<4&&intent!=null;depth++){
            ComponentName component=intent.getComponent();String name=component==null?null:component.getClassName();
            if(ReaderAdRules.livePlayer(name))return name;
            // Only unwrap the plugin containers confirmed in Mira's wrapIntent implementation.
            if(name==null||!name.startsWith("com.bytedance.mira.stub."))return null;
            ActivityInfo info=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra("target_activityinfo",ActivityInfo.class)
                :(ActivityInfo)intent.getParcelableExtra("target_activityinfo");
            if(info!=null&&ReaderAdRules.livePlayer(info.name))return info.name;
            Intent next=Build.VERSION.SDK_INT>=33?intent.getParcelableExtra("target_intent",Intent.class)
                :(Intent)intent.getParcelableExtra("target_intent");
            if(next==intent)return null;intent=next;
        }
        return null;
    }
    private static String readerOwner(Object owner){
        for(int i=0;i<12&&owner!=null;i++){
            if(BookRules.isReaderPage(owner.getClass().getName()))return owner.getClass().getName();
            if(!(owner instanceof ContextWrapper))return null;
            Context next=((ContextWrapper)owner).getBaseContext();if(next==owner)return null;owner=next;
        }
        return null;
    }
}
