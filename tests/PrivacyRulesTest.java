package io.github.mianxyz.fanqiecustomizer;

/** Diagnostic output must not include runtime values, even through exception causes. */
public final class PrivacyRulesTest {
    public static void main(String[] args) {
        String secret="sample-account sample-book private-path https://example.invalid/private";
        Throwable nested=new IllegalStateException(secret,new IllegalArgumentException(secret));
        check("IllegalArgumentException".equals(PrivacyRules.errorType(nested)),"only root exception type emitted");
        check(!PrivacyRules.errorType(nested).contains(secret),"error details withheld");
        Throwable cycleA=new RuntimeException(secret),cycleB=new IllegalStateException(secret);
        cycleA.initCause(cycleB);cycleB.initCause(cycleA);
        check(!PrivacyRules.errorType(cycleA).contains(secret),"cyclic cause does not hang or expose details");
        for(String value:new String[]{null,secret,"user_123456789","reader_hiden_audio_btn_v561_123","https://example.invalid"}) {
            check(!PrivacyRules.knownConfigName(value),"unknown runtime name withheld");
            check(!PrivacyRules.settingKey(value),"unknown preference name not mirrored");
        }
        check(PrivacyRules.knownConfigName("reader_hiden_audio_btn_v561"),"verified config remains traceable");
        check(PrivacyRules.settingKey("hide_update_popup"),"new switch synchronizes");
        check(PrivacyRules.settingKey("hide_short_drama_ad"),"short-drama ad switch synchronizes");
        check(!Rules.defaultOn("hide_short_drama_ad"),"short-drama ad suppression defaults off");
        for(String[] keys:new String[][]{Rules.TAB_KEYS,Rules.CHANNEL_KEYS,Rules.MINE_KEYS})
            for(String key:keys)check(PrivacyRules.settingKey(key),"existing grouped switch synchronizes");
        check(!Rules.defaultOn("hide_update_popup"),"update suppression defaults off");
        check(!PrivacyRules.settingKey("reader_page_index"),"retired page index preference is not loaded or synchronized");
        check(!PrivacyRules.knownConfigName("key_show_page_index"),"retired page index configuration is no longer exposed in diagnostics");
        System.out.println("PrivacyRulesTest passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
