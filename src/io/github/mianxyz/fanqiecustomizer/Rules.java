package io.github.mianxyz.fanqiecustomizer;

import java.util.*;

public final class Rules {
    public static final String PACKAGE = "io.github.mianxyz.fanqiecustomizer";
    public static final String TARGET = "com.dragon.read";
    public static final String PREFS = "settings";
    /** Setting keys that are not part of the tab / channel / mine groups. */
    public static final String[] EXTRA_KEYS = {
        "enabled","in_app_entry","debug","trace_ab","local_vip",
        "hide_welfare_float","hide_post_float","hide_drama_resume","auto_expand_shelf","hide_coin",
        "hide_reader_video_entry","hide_reader_audio",
        "hide_banner","hide_reader_ads","hide_reader_live_popup","hide_games",
        "hide_income","hide_frequent_games","show_book_id","book_id_search",
        "force_open_removed","raw_read_counts","hide_update_popup"
    };
    /** All known switches, used by the optional shared-settings fallback. */
    public static String[] allSettingKeys() {
        List<String> keys = new ArrayList<>();
        Collections.addAll(keys, TAB_KEYS);
        Collections.addAll(keys, CHANNEL_KEYS);
        Collections.addAll(keys, MINE_KEYS);
        Collections.addAll(keys, StartupTabRules.KEYS);
        Collections.addAll(keys, TabletRules.KEYS);
        Collections.addAll(keys, EXTRA_KEYS);
        return keys.toArray(new String[0]);
    }
    public static final String LOG_GUIDE = "开启模块中的‘输出命中日志’，确认配置已同步。强行停止并重新打开番茄，在问题页面复现后，进入 LSPosed → 日志 → 模块日志，导出并保存日志。\n\n日志可能包含个人信息，分享前请检查并遮盖敏感内容。";
    public static final String[] TAB_KEYS = {"hide_store", "hide_series", "hide_earn", "hide_shelf", "hide_mine"};
    public static final String[] TAB_LABELS = {"书城", "短剧", "赚钱", "书架", "我的"};
    public static final String[] CHANNEL_LABELS={"推荐","小说","听书","看剧","经典","短篇","视频","知识","漫画","新书","商城"};
    public static final String[] CHANNEL_KEYS={"channel_recommend","channel_novel","channel_audio","channel_drama","channel_classic","channel_story","channel_video","channel_knowledge","channel_comic","channel_new","channel_shop"};
    public static final String[] MINE_LABELS={"我的订单","浏览记录","我的消息","创作中心","我的预约","我的点赞","我的下载","游戏中心","反馈与帮助"};
    public static final String[] MINE_KEYS={"mine_order","mine_history","mine_message","mine_creator","mine_reservation","mine_like","mine_download","mine_game","mine_help"};
    private Rules() {}
    public static boolean defaultOn(String key){return "in_app_entry".equals(key)||"hide_reader_live_popup".equals(key);}

    public static String tabKey(String name) {
        if (name == null) return null;
        switch (name) {
            case "BookStore": return "hide_store";
            case "LuckyBenefit": return "hide_earn";
            case "Bookshelf": case "BookShelf": return "hide_shelf";
            case "MyProfile": return "hide_mine";
            case "VideoSeriesFeedTab": return "hide_series";
            default: return null;
        }
    }

    public static boolean allTabsHidden(Map<String, ?> values) {
        for (String key : TAB_KEYS) if (!Boolean.TRUE.equals(values.get(key))) return false;
        return true;
    }

    public static boolean allChannelsHidden(Map<String,?> values){for(String key:CHANNEL_KEYS)if(!Boolean.TRUE.equals(values.get(key)))return false;return true;}

    public static String channelKey(String title,int type){
        if(title!=null)for(int i=0;i<CHANNEL_LABELS.length;i++)if(CHANNEL_LABELS[i].equals(title.trim()))return CHANNEL_KEYS[i];
        switch(type){
            case 2:return CHANNEL_KEYS[0];case 25:return CHANNEL_KEYS[1];case 5:return CHANNEL_KEYS[2];
            case 8:case 17:case 37:case 38:return CHANNEL_KEYS[3];
            case 11:case 6:return CHANNEL_KEYS[4];case 13:return CHANNEL_KEYS[5];
            case 4:case 16:return CHANNEL_KEYS[6];case 7:case 12:return CHANNEL_KEYS[7];
            case 9:case 32:return CHANNEL_KEYS[8];case 3:case 31:return CHANNEL_KEYS[9];case 15:return CHANNEL_KEYS[10];
            default:return null;
        }
    }

    public static String mineKey(String title,String type){
        if(title!=null)for(int i=0;i<MINE_LABELS.length;i++)if(MINE_LABELS[i].equals(title.trim()))return MINE_KEYS[i];
        if(type==null)return null;
        switch(type){
            case "MyOrder":case "AD_ORDER":return "mine_order";
            case "ReadHistory":case "ReadHistoryAll":case "ReadHistoryVideo":case "READING_HISTORY":case "MINE_HISTORY_CARD":return "mine_history";
            case "MyMessage":case "MY_MESSAGE":return "mine_message";
            case "WriterCenter":case "CreatorCenter":case "CreationCenter":case "WRITER_CENTER":case "CREATION_CENTER":return "mine_creator";
            case "VideoSubscribe":case "VIDEO_BOOKING":return "mine_reservation";
            case "MyDigg":case "DiggContent":case "DiggVideo":case "VIDEO_LIKE":case "DIGG_CONTENT":case "MINE_LIKE":return "mine_like";
            case "Download":case "BOOK_DOWNLOAD":return "mine_download";
            case "GameCenter":case "MINI_GAME":return "mine_game";
            case "FeedbackHelp":case "FEEDBACK_AND_HELP":return "mine_help";
            case "GoldBalance":case "POLARIS":case "CREATION_INCOME":return "hide_income";
            default:return null;
        }
    }

    public static String viewRule(String name) {
        switch (name) {
            case "com.bytedance.ug.sdk.kmp.novel.pendant.w":return "hide_welfare_float";
            case "com.bytedance.ug.sdk.kmp.novel.pendant.widget.InnerPendantView":
            case "com.bytedance.ug.sdk.kmp.novel.pendant.widget.PendantHideView":
            case "com.bytedance.ug.sdk.luckydog.task.tasktimer.view.LuckyCommonPendantView":return "hide_welfare_float";
            case "com.dragon.read.ad.banner.ui.BaseBannerView":
            case "com.dragon.read.reader.extend.banner.BannerBackgroundView":
            case "com.dragon.read.reader.extend.banner.CoverView":return "hide_banner";
            case "com.dragon.read.ad.brand.ui.BrandChapterFrontAdView":
            case "com.dragon.read.ad.brand.ui.BrandChapterFrontTopView":
            case "com.dragon.read.ad.cartoon.ui.CartoonCardAdView": return "hide_reader_ads";
            case "com.dragon.read.component.biz.impl.bookshelf.polaris.BookshelfCoinBarView":
            case "com.dragon.read.widget.GoldCoinStickerView":
            case "com.dragon.read.goldcoinbox.widget.GoldCoinBoxCircleTipView":
            case "com.dragon.read.goldcoinbox.widget.GoldCoinBoxTextView":
            case "com.dragon.read.ad.coinreward.ui.BaseCoinRewardView": return "hide_coin";
            case "com.dragon.read.ad.chapterend.view.ChapterEndGameCenterItemView": return "hide_games";
            default: return null;
        }
    }
}
