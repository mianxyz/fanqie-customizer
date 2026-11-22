package io.github.mianxyz.fanqiecustomizer;

import android.content.pm.PackageManager;

/** Exercise component-state readback and reversible state changes without changing real packages. */
public final class LauncherVisibilityTest {
    private static final class Component implements LauncherVisibility.ComponentAccess {
        int value,writes;boolean ignore,deny;
        public int state(){return value;}
        public void set(int next){writes++;if(deny)throw new SecurityException();if(!ignore)value=next;}
    }
    public static void main(String[] args){
        Component component=new Component();
        check(!LauncherVisibility.hidden(component.state()),"manifest default keeps the launcher icon visible");
        check(LauncherVisibility.setHidden(component,true)&&component.value==PackageManager.COMPONENT_ENABLED_STATE_DISABLED,"hiding disables only the selected launcher component");
        check(LauncherVisibility.setHidden(component,true)&&component.writes==1,"already hidden state does not emit another component change");
        check(LauncherVisibility.setHidden(component,false)&&component.value==PackageManager.COMPONENT_ENABLED_STATE_ENABLED,"restoring explicitly enables the launcher alias");
        check(!LauncherVisibility.hidden(component.state()),"readback reflects restored state");
        for(int disabled:new int[]{PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED}){
            component.value=disabled;
            check(LauncherVisibility.hidden(component.state()),"other disabled states are correctly reported as hidden");
            check(LauncherVisibility.setHidden(component,false)&&!LauncherVisibility.hidden(component.state()),"all disabled states can be restored");
        }
        component.ignore=true;
        check(!LauncherVisibility.setHidden(component,true)&&!LauncherVisibility.hidden(component.state()),"unconfirmed package-manager write is not reported as success");
        component.ignore=false;component.deny=true;
        try{LauncherVisibility.setHidden(component,true);throw new AssertionError("component denial must reach the UI");}catch(SecurityException expected){}
        check(!LauncherVisibility.hidden(component.state()),"failed write does not fabricate a new icon state");
        System.out.println("LauncherVisibilityTest passed");
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
