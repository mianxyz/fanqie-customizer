package io.github.mianxyz.fanqiecustomizer;

/** Advertising popup allowlist; unrelated dialogs keep their normal behavior. */
final class ReaderAdRules {
    private ReaderAdRules(){}
    static boolean adPopup(String name){
        return "not_standard_ad_dialog".equals(name)||"marketing_task_dialog".equals(name)
            ||"marketing_task_dialog_p0".equals(name)||"watch_video_for_no_ad_dialog".equals(name)
            ||"bookmall_ad_popup".equals(name)||"live_streaming_top_banner".equals(name);
    }
    static boolean blockLivePromotion(boolean enabled,String ownerClass,boolean readerVisible){
        return enabled&&(BookRules.isReaderPage(ownerClass)||readerVisible);
    }
    static boolean adWindow(String className){
        return "lh.a".equals(className)||"mb3.b".equals(className);
    }
    static boolean blockAdWindow(boolean enabled,String ownerClass,boolean readerVisible,String className){
        return blockLivePromotion(enabled,ownerClass,readerVisible)&&adWindow(className);
    }
    static boolean livePlayer(String name){return "com.dragon.read.plugin.live.XsLivePlayerActivity".equals(name);}
    static boolean blockLiveLaunch(boolean enabled,boolean liveGuard,String ownerClass,boolean readerVisible,String targetClass){
        return liveGuard&&livePlayer(targetClass)&&blockLivePromotion(enabled,ownerClass,readerVisible);
    }
    static boolean blockLiveTransition(boolean enabled,boolean liveGuard,String lastPage,long sinceReaderPaused,String targetClass){
        return enabled&&liveGuard&&livePlayer(targetClass)&&BookRules.isReaderPage(lastPage)
            &&sinceReaderPaused>=0&&sinceReaderPaused<=1500;
    }
    static boolean blockDynamicPage(boolean enabled,String ownerClass){
        return enabled&&BookRules.isReaderPage(ownerClass);
    }
    static boolean adExitTitle(String title){
        return title!=null&&title.contains("关闭广告")&&title.contains("退出");
    }
    static boolean blockPopup(boolean enabled,String ownerClass,boolean readerVisible,String name){
        return enabled&&(BookRules.isReaderPage(ownerClass)||readerVisible)&&adPopup(name);
    }
}
