import io.github.mianxyz.fanqiecustomizer.Rules;
import java.util.*;
public class RulesTest {
    public static void main(String[] args) {
        String[] enums={"BookStore","VideoSeriesFeedTab","LuckyBenefit","BookShelf","MyProfile"};
        for(int i=0;i<enums.length;i++)check(Rules.TAB_KEYS[i].equals(Rules.tabKey(enums[i])),"tab mapping "+enums[i]);
        check(Rules.tabKey("UnknownTabBar")==null,"unknown tabs preserved");
        check(Rules.tabKey("ShopMall")==null,"shopping tab not confused with bookstore");
        Map<String,Object> flags=new HashMap<>();check(!Rules.allTabsHidden(flags),"default keeps navigation");
        for(String key:Rules.TAB_KEYS)flags.put(key,true);
        check(Rules.allTabsHidden(flags),"all-hidden configuration rejected");
        for(String key:Rules.TAB_KEYS){flags.put(key,false);check(!Rules.allTabsHidden(flags),"one remaining tab "+key);flags.put(key,true);}
        check("hide_games".equals(Rules.viewRule("com.dragon.read.ad.chapterend.view.ChapterEndGameCenterItemView")),"game rule independent from ads");
        check("hide_coin".equals(Rules.viewRule("com.dragon.read.component.biz.impl.bookshelf.polaris.BookshelfCoinBarView")),"coin bar");
        check(Rules.viewRule("android.widget.TextView")==null,"text never filtered generically");
        check("hide_banner".equals(Rules.viewRule("com.dragon.read.reader.extend.banner.BannerBackgroundView")),"bottom banner host hidden with the banner switch");
        check("hide_banner".equals(Rules.viewRule("com.dragon.read.reader.extend.banner.CoverView")),"dedicated banner cover hidden");
        check(Rules.viewRule("com.dragon.read.reader.ui.ReaderViewLayout")==null,"reader content root must remain visible");
        check(Rules.viewRule("android.widget.FrameLayout")==null,"ordinary layout containers must remain visible");
        check(Rules.viewRule("com.dragon.read.ad.feedback.ui.RoundCornerTextView")==null,"no blanket ad package filtering");
        for(int i=0;i<Rules.CHANNEL_LABELS.length;i++)check(Rules.CHANNEL_KEYS[i].equals(Rules.channelKey(Rules.CHANNEL_LABELS[i],999)),"known channel title");
        check(Rules.channelKey("未知频道",999)==null,"unknown channel unchanged");
        check("mine_creator".equals(Rules.mineKey(null,"CreatorCenter")),"creator type variant");
        check(Rules.mineKey("普通正文中提到了我的订单",null)==null,"no substring hiding");
        System.out.println("RulesTest passed");
    }
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
