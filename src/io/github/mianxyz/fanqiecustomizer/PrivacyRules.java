package io.github.mianxyz.fanqiecustomizer;

/** Keep diagnostic and preference keys limited to names defined by this module. */
final class PrivacyRules {
    private PrivacyRules() {}

    static String errorType(Throwable error) {
        if(error==null)return "UnknownError";
        // Do not log Throwable.toString, messages, causes' messages or stack traces.
        // A bounded walk also handles unusual cyclic cause chains.
        for(int i=0;i<8&&error.getCause()!=null&&error.getCause()!=error;i++)error=error.getCause();
        return error.getClass().getSimpleName();
    }

    static boolean knownConfigName(String name) {
        if(name==null)return false;
        switch(name) {
            case "reader_hiden_audio_btn_v561":
            case "new_post_bookstore_entrance_config_v717":
            case "bookshelf_unfold_config_v685":
            case "key_check_book_status":
            case "key_show_reward_entrance_latest_chapter":return true;
            default:return false;
        }
    }

    /** 是否为受管的整型参数键（滑块）。供 Kotlin 侧 ConfigBridge 使用。 */
    public static boolean isIntKey(String key) {
        return Rules.isIntKey(key);
    }

    static boolean settingKey(String key) {
        if(key==null)return false;
        if(Rules.isIntKey(key))return true;
        for(String known:Rules.TAB_KEYS)if(known.equals(key))return true;
        for(String known:Rules.CHANNEL_KEYS)if(known.equals(key))return true;
        for(String known:Rules.MINE_KEYS)if(known.equals(key))return true;
        for(String known:StartupTabRules.KEYS)if(known.equals(key))return true;
        for(String known:TabletRules.KEYS)if(known.equals(key))return true;
        for(String known:Rules.EXTRA_KEYS)if(known.equals(key))return true;
        return false;
    }
}
