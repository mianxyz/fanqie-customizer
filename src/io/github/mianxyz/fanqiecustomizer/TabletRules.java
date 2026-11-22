package io.github.mianxyz.fanqiecustomizer;

/** Exact native menu identities; opt-in filtering is limited to tablet-sized layouts. */
final class TabletRules {
    static final String[] LABELS={"视频创作","兴趣偏好","我的笔记","看过的人","番茄指南","我的钱包","活动中心","我的公益","我的催更"};
    static final String[] KEYS={"pad_video_creation","pad_interest","pad_notes","pad_seen_people","pad_guide","pad_wallet","pad_activity","pad_public_welfare","pad_urge_update"};
    private TabletRules() {}
    static boolean layout(int smallestWidthDp){return smallestWidthDp>=600;}
    static String key(boolean tablet,String title,String type){
        if(!tablet)return null;
        if(type!=null)switch(type){
            case "VideoCreation":case "VIDEO_CREATOR":return KEYS[0];
            case "ReadPreferences":case "READING_PREFERENCE":return KEYS[1];
            case "Bookmark":case "NOTE_CENTER":return KEYS[2];
            case "PeopleHasSeen":case "MY_FOLLOW":return KEYS[3];
            case "Guide":case "USER_GUIDE":return KEYS[4];
            case "DouyinWallet":case "DOUYIN_WALLET":return KEYS[5];
            case "ActivityCenter":case "ACTIVITY_CENTER":return KEYS[6];
            case "PublicWelfare":case "CSR_ENTRANCE":return KEYS[7];
            case "MyUrge":case "MY_URGE_UPDATE":return KEYS[8];
            default:break;
        }
        if(title!=null){String text=title.replace('\u00a0',' ').trim();for(int i=0;i<LABELS.length;i++)if(LABELS[i].equals(text))return KEYS[i];}
        return null;
    }
}
