package io.github.mianxyz.fanqiecustomizer;

/** Preserve explicit playback, non-video records, other pages and disabled settings. */
public final class DramaResumeRulesTest {
    public static void main(String[] args){
        check(!Rules.defaultOn(DramaResumeRules.KEY)&&PrivacyRules.settingKey(DramaResumeRules.KEY),"opt-in setting participates in remote preference synchronization");
        check(DramaResumeRules.automatic(true,true,false),"automatic video history request blocked");
        check(DramaResumeRules.attach(true,true,false,DramaResumeRules.MAIN),"delayed home attachment blocked");
        for(boolean video:new boolean[]{false,true})for(boolean player:new boolean[]{false,true}){
            check(!DramaResumeRules.automatic(false,video,player),"disabled option keeps original request");
            check(!DramaResumeRules.attach(false,video,player,DramaResumeRules.MAIN),"disabled option keeps original attachment");
        }
        check(!DramaResumeRules.automatic(true,false,false),"non-video recent reading retained");
        check(!DramaResumeRules.automatic(true,true,true),"explicit background mini player retained");
        check(!DramaResumeRules.attach(true,true,true,DramaResumeRules.MAIN),"explicit player can attach to main");
        for(String page:new String[]{null,"com.dragon.read.reader.ui.ReaderActivity","com.dragon.read.component.biz.impl.FanqieSearchActivity","example.MainFragmentActivity"})
            check(!DramaResumeRules.attach(true,true,false,page),"attachment scope limited to the exact native home activity");
        System.out.println("DramaResumeRulesTest passed");
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
