package io.github.mianxyz.fanqiecustomizer;

/** Ad queue scope must preserve login, permissions, security and ordinary reader menus. */
public final class ReaderAdRulesTest {
    public static void main(String[] args){
        String reader="com.dragon.read.reader.ui.ReaderActivity";
        String main="com.dragon.read.pages.main.MainFragmentActivity";
        for(String ad:new String[]{"not_standard_ad_dialog","marketing_task_dialog","marketing_task_dialog_p0","watch_video_for_no_ad_dialog","bookmall_ad_popup","live_streaming_top_banner"}){
            check(ReaderAdRules.blockPopup(true,reader,false,ad),"reader-owned ad refused: "+ad);
            check(ReaderAdRules.blockPopup(true,main,true,ad),"earlier queued ad refused while reading: "+ad);
            check(!ReaderAdRules.blockPopup(true,main,false,ad),"other pages unchanged: "+ad);
            check(!ReaderAdRules.blockPopup(false,reader,true,ad),"disabled switch allows original behavior: "+ad);
        }
        for(String ordinary:new String[]{null,"privacy_dialog","system_premission_dialog","login_guide_dialog","force_upgrade_dialog","unknown_lynx_popup_dialog","reader_menu","payment_confirm","security_warning"})
            check(!ReaderAdRules.blockPopup(true,reader,true,ordinary),"non-ad UI preserved: "+ordinary);
        check(!ReaderAdRules.blockPopup(true,null,false,"marketing_task_dialog"),"unknown context does not broaden scope");
        check(ReaderAdRules.blockLivePromotion(true,reader,false),"reader-owned live promotion refused");
        check(ReaderAdRules.blockLivePromotion(true,main,true),"live promotion refused while reader visible");
        check(!ReaderAdRules.blockLivePromotion(false,reader,true),"live promotion respects disabled switch");
        check(!ReaderAdRules.blockLivePromotion(true,main,false),"live promotion on other pages preserved");
        check(!ReaderAdRules.blockLivePromotion(true,null,false),"unknown owner requires foreground reader");
        for(String window:new String[]{"lh.a","mb3.b"}){
            check(ReaderAdRules.blockAdWindow(true,reader,false,window),"reader ad window refused: "+window);
            check(ReaderAdRules.blockAdWindow(true,main,true,window),"ad window refused with foreground reader: "+window);
            check(!ReaderAdRules.blockAdWindow(true,main,false,window),"ad window on other pages preserved: "+window);
            check(!ReaderAdRules.blockAdWindow(false,reader,true,window),"ad window respects switch: "+window);
        }
        for(String window:new String[]{null,"android.app.Dialog","androidx.appcompat.app.AlertDialog","login_dialog","security_warning","com.bytedance.android.livesdk.pannel.SheetBaseDialog"})
            check(!ReaderAdRules.blockAdWindow(true,reader,true,window),"ordinary or unknown window preserved: "+window);
        String live="com.dragon.read.plugin.live.XsLivePlayerActivity";
        check(ReaderAdRules.blockLiveLaunch(true,true,reader,false,live),"reader source blocks known live player launch");
        check(ReaderAdRules.blockLiveLaunch(true,true,null,true,live),"foreground reader blocks known live player launch");
        check(!ReaderAdRules.blockLiveLaunch(false,true,reader,true,live),"live player launch respects ad switch");
        check(!ReaderAdRules.blockLiveLaunch(true,false,reader,true,live),"live player launch respects independent opt-out");
        check(!ReaderAdRules.blockLiveLaunch(true,true,main,false,live),"live player from other pages preserved");
        for(String target:new String[]{null,main,"com.dragon.read.reader.ui.ReaderActivity","com.dragon.read.plugin.live.OtherActivity","com.dragon.read.plugin.live.XsLivePlayerActivityHelper","com.bytedance.mira.stub.p0.StubSingleTaskActivity1","io.github.mianxyz.fanqiecustomizer.SettingsActivity"})
            check(!ReaderAdRules.blockLiveLaunch(true,true,reader,true,target),"non-live target preserved: "+target);
        check(ReaderAdRules.blockLiveTransition(true,true,reader,0,live),"immediate reader transition blocked");
        check(ReaderAdRules.blockLiveTransition(true,true,reader,1500,live),"transition boundary included");
        check(!ReaderAdRules.blockLiveTransition(true,true,reader,1501,live),"old reader visit does not block later live page");
        check(!ReaderAdRules.blockLiveTransition(true,true,reader,-1,live),"missing pause never creates a transition");
        check(!ReaderAdRules.blockLiveTransition(true,true,main,100,live),"different preceding page preserved");
        check(!ReaderAdRules.blockLiveTransition(true,false,reader,100,live),"transition respects independent opt-out");
        check(!ReaderAdRules.blockLiveTransition(false,true,reader,100,live),"transition respects ad switch");
        check(ReaderAdRules.blockDynamicPage(true,reader),"native reader dynamic ad page blocked");
        check(!ReaderAdRules.blockDynamicPage(false,reader),"dynamic ad page respects disabled switch");
        for(String owner:new String[]{null,main,"com.dragon.read.story.impl.activity.ShortStoryActivity","com.dragon.read.reader.detail.BookDetailActivity"})
            check(!ReaderAdRules.blockDynamicPage(true,owner),"dynamic ad guard retains exact long-reader scope: "+owner);
        System.out.println("ReaderAdRulesTest passed");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
