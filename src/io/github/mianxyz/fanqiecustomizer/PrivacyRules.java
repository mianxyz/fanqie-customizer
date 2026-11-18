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

    static boolean settingKey(String key) {
        if(key==null)return false;
        for(String known:Rules.TAB_KEYS)if(known.equals(key))return true;
        for(String known:Rules.CHANNEL_KEYS)if(known.equals(key))return true;
        for(String known:Rules.MINE_KEYS)if(known.equals(key))return true;
        for(String known:StartupTabRules.KEYS)if(known.equals(key))return true;
        for(String known:TabletRules.KEYS)if(known.equals(key))return true;
        switch(key) {
            case "enabled":case "in_app_entry":case "debug":case "trace_ab":case "local_vip":
            case "hide_welfare_float":case "hide_post_float":case "hide_drama_resume":case "auto_expand_shelf":case "hide_coin":
            case "hide_reader_video_entry":case "hide_reader_audio":
            case "hide_banner":case "hide_reader_ads":case "hide_reader_live_popup":case "hide_short_drama_ad":case "hide_games":
            case "hide_income":case "hide_frequent_games":case "show_book_id":case "book_id_search":
            case "force_open_removed":case "raw_read_counts":case "hide_update_popup":return true;
            default:return false;
        }
    }
}
