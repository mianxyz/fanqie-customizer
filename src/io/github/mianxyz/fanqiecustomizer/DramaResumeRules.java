package io.github.mianxyz.fanqiecustomizer;

/** The automatic history pendant and explicit background player use different native modes. */
final class DramaResumeRules {
    static final String KEY="hide_drama_resume";
    static final String MAIN="com.dragon.read.pages.main.MainFragmentActivity";
    private DramaResumeRules() {}
    static boolean automatic(boolean enabled,boolean video,boolean playerMode){return enabled&&video&&!playerMode;}
    static boolean attach(boolean enabled,boolean video,boolean playerMode,String activity){
        return MAIN.equals(activity)&&automatic(enabled,video,playerMode);
    }
}
