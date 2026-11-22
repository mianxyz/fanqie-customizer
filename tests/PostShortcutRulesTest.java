package io.github.mianxyz.fanqiecustomizer;

/** Keep editor controls and non-main screens intact while suppressing the dedicated floating root. */
public final class PostShortcutRulesTest {
    public static void main(String[] args){
        check(PostShortcutRules.suppress(true,PostShortcutRules.MAIN,PostShortcutRules.ROOT),"main floating root suppressed");
        for(int requested:new int[]{0,4,8}){
            check(PostShortcutRules.visibility(true,PostShortcutRules.MAIN,PostShortcutRules.ROOT,requested)==8,"native show or animation cannot restore a hidden shortcut");
            check(PostShortcutRules.visibility(false,PostShortcutRules.MAIN,PostShortcutRules.ROOT,requested)==requested,"disabled switch preserves all native visibility requests");
        }
        for(String page:new String[]{null,"com.dragon.read.reader.ui.ReaderActivity","com.dragon.read.social.editor.graphpost.GraphPostEditorActivity","example.MainFragmentActivity"})
            check(!PostShortcutRules.suppress(true,page,PostShortcutRules.ROOT),"other page editor roots preserved");
        for(String view:new String[]{null,"android.view.View","android.widget.FrameLayout","com.dragon.read.social.ui.PublishButton","com.dragon.community.common.ui.contentpublish.PublishButton","com.dragon.read.editor.CollapsingPublishLayout","dd5.o2"})
            check(!PostShortcutRules.suppress(true,PostShortcutRules.MAIN,view),"normal editor controls and generic views preserved");
        check(!Rules.defaultOn(PostShortcutRules.KEY)&&PrivacyRules.settingKey(PostShortcutRules.KEY),"existing opt-in setting and sync key retained");
        System.out.println("PostShortcutRulesTest passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
