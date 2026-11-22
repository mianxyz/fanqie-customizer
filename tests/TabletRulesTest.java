package io.github.mianxyz.fanqiecustomizer;

/** Tablet filtering must match menu identities, not unrelated text or phone layouts. */
public final class TabletRulesTest {
    public static void main(String[] args){
        String[] rpc={"VideoCreation","ReadPreferences","Bookmark","PeopleHasSeen","Guide","DouyinWallet","ActivityCenter","PublicWelfare","MyUrge"};
        String[] nativeKinds={"VIDEO_CREATOR","READING_PREFERENCE","NOTE_CENTER","MY_FOLLOW","USER_GUIDE","DOUYIN_WALLET","ACTIVITY_CENTER","CSR_ENTRANCE","MY_URGE_UPDATE"};
        check(!TabletRules.layout(0)&&!TabletRules.layout(599)&&TabletRules.layout(600)&&TabletRules.layout(900),"large-layout boundary");
        for(int i=0;i<TabletRules.KEYS.length;i++){
            String key=TabletRules.KEYS[i];
            check(key.equals(TabletRules.key(true,TabletRules.LABELS[i],null)),"exact label mapped");
            check(key.equals(TabletRules.key(true,null,rpc[i])),"RPC enum mapped");
            check(key.equals(TabletRules.key(true,null,nativeKinds[i])),"native card mapped");
            check(TabletRules.key(false,TabletRules.LABELS[i],rpc[i])==null,"phone layout retained");
            check(!Rules.defaultOn(key)&&PrivacyRules.settingKey(key),"optional setting is synchronized");
        }
        check(TabletRules.KEYS.length==9&&TabletRules.LABELS.length==9,"nine tablet entries");
        for(String type:new String[]{"MyUrge","MY_URGE_UPDATE"})check(TabletRules.key(false,null,type)==null,"phone enum retained");
        check(TabletRules.key(false,"我的催更",null)==null,"phone title retained");
        check(TabletRules.key(true,"我的催更说明",null)==null,"urge substring retained");
        check("pad_notes".equals(TabletRules.key(true,"我的笔记\u00a0",null)),"native label spacing accepted");
        for(String title:new String[]{null,"设置","关于番茄指南的小说","我的钱包余额说明","兴趣偏好说明","我的公益记录说明","我的预约"})
            check(TabletRules.key(true,title,null)==null,"unrelated or substring labels retained");
        for(String kind:new String[]{"Setting","MyOrder","VideoSubscribe","InterestedPeople","DouyinMonthlyPay","CREATION_CENTER","WRITER_CENTER"})
            check(TabletRules.key(true,null,kind)==null,"other native menu types retained");
        System.out.println("TabletRulesTest passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
