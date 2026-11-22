package io.github.mianxyz.fanqiecustomizer;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;

/** Local launcher state belongs to PackageManager, independently of hook preferences. */
final class LauncherVisibility {
    static final String ALIAS=".LauncherAlias";
    private LauncherVisibility() {}

    static boolean isHidden(Context context){return hidden(access(context).state());}
    static boolean setHidden(Context context,boolean hide){return setHidden(access(context),hide);}

    interface ComponentAccess {int state();void set(int state);}
    private static ComponentAccess access(Context context){
        PackageManager manager=context.getPackageManager();
        ComponentName alias=new ComponentName(context.getPackageName(),context.getPackageName()+ALIAS);
        return new ComponentAccess(){
            public int state(){return manager.getComponentEnabledSetting(alias);}
            public void set(int state){manager.setComponentEnabledSetting(alias,state,PackageManager.DONT_KILL_APP);}
        };
    }
    static boolean setHidden(ComponentAccess access,boolean hide){
        if(hidden(access.state())==hide)return true;
        access.set(hide?PackageManager.COMPONENT_ENABLED_STATE_DISABLED:PackageManager.COMPONENT_ENABLED_STATE_ENABLED);
        return hidden(access.state())==hide;
    }
    static boolean hidden(int state){
        switch(state){
            // LauncherAlias is enabled by default in the manifest.
            case PackageManager.COMPONENT_ENABLED_STATE_DEFAULT:
            case PackageManager.COMPONENT_ENABLED_STATE_ENABLED:return false;
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER:
            case PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED:return true;
            default:throw new IllegalStateException("Unknown component state");
        }
    }
}
