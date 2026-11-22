package io.github.mianxyz.fanqiecustomizer;

/** Update suppression must not hide security, permissions or similarly named UI. */
public final class UpdatePopupRulesTest {
    public static void main(String[] args) {
        for(String name:new String[]{"gray_upgrade_dialog","official_upgrade_dialog","force_upgrade_dialog"}) {
            check(UpdatePopupRules.block(true,UpdatePopupRules.POP,name),"recognized automatic upgrade refused");
            check(!UpdatePopupRules.block(false,UpdatePopupRules.POP,name),"disabled setting preserves upgrade");
            check(!UpdatePopupRules.block(true,"other.application.Pop",name),"same name in unrelated enum preserved");
            check(!UpdatePopupRules.block(true,null,name),"unknown properties preserved");
        }
        for(String name:new String[]{null,"privacy_dialog","system_premission_dialog","login_guide_dialog","security_warning",
            "ILLEGAL_ACCESS","reader_menu","unknown_lynx_popup_dialog","force_upgrade_dialog_other","manual_check_update","book_chapter_update"})
            check(!UpdatePopupRules.block(true,UpdatePopupRules.POP,name),"ordinary UI and unknown update types preserved");
        System.out.println("UpdatePopupRulesTest passed");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
