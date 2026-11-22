package io.github.mianxyz.fanqiecustomizer;

/** Only the native floating editor root on the main activity is a shortcut target. */
final class PostShortcutRules {
    static final String KEY="hide_post_float";
    static final String MAIN="com.dragon.read.pages.main.MainFragmentActivity";
    static final String ROOT="dd5.o";
    private PostShortcutRules() {}
    static boolean suppress(boolean enabled,String activity,String view){
        return enabled&&MAIN.equals(activity)&&ROOT.equals(view);
    }
    static int visibility(boolean enabled,String activity,String view,int requested){
        return suppress(enabled,activity,view)?8:requested;
    }
}
