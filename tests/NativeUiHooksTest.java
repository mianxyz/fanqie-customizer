package io.github.mianxyz.fanqiecustomizer;
import java.util.*;
import android.view.View;

/** Verify native cache immutability, channel selection and safe submenu filtering. */
public class NativeUiHooksTest {
    public enum CardType {COMMON,AD_ORDER,MY_MESSAGE,VIDEO_BOOKING,MINI_GAME,FEEDBACK_AND_HELP,UNKNOWN,NOTE_CENTER,DOUYIN_WALLET,VIDEO_CREATOR,CSR_ENTRANCE}
    public static class Card {
        public final CardType a;public final List<Card> b;public Object c;
        public Card(CardType type,List<Card> children){a=type;b=children;}
    }
    public static class RawTabs {public List<Object> tabItem;public int tabIndex;}
    public static class Tab {
        public String tabName;public int tabType;public Object originalTabData,clientTemplate;
        Tab(String title,int type){tabName=title;tabType=type;originalTabData=new Object();clientTemplate=new Object();}
    }
    public static class Mall {
        public List<Tab> bookMallTabDataList;public int selectIndex,defaultTabType;public Object defaultTabClientTemplate;
        public List<Object> defaultTabDataList;public RawTabs originalDataList;
    }
    public enum Entrance {Game,Withdraw,Unknown}
    public static class Item {public Entrance entranceType;Item(Entrance type){entranceType=type;}}
    public static class Entrances {public List<Item> mineEcomEntranceData;}
    public static class SidebarItem {public int a;public String b;SidebarItem(int type,String label){a=type;b=label;}}
    public static class Sidebar {public List<SidebarItem> a;}
    public static void main(String[] args)throws Throwable {
        Set<String> flags=new HashSet<>();
        boolean[] tablet={false};
        NativeUiHooks n=new NativeUiHooks(null,new NativeUiHooks.Host(){
            public boolean on(String key){return flags.contains(key);}public void log(String text){}
            public boolean tabletLayout(){return tablet[0];}
            public void forceHide(View view,String why){}public void dataChanged(){}public void mineRoot(View view){}
        },NativeUiHooksTest.class.getClassLoader());
        Card order=new Card(CardType.AD_ORDER,null),message=new Card(CardType.MY_MESSAGE,null),unknown=new Card(CardType.UNKNOWN,null);
        Card group=new Card(CardType.COMMON,List.of(order,message,unknown));group.c=new Object();
        flags.add("mine_order");Card result=(Card)n.filterCard(group,0);
        check(result!=group&&result.b.equals(List.of(message,unknown)),"native order removed while unknown and message retained");
        check(group.b.equals(List.of(order,message,unknown))&&result.c==group.c,"cached menu and extra picture metadata preserved");
        flags.clear();check(n.filterCard(group,0)==group,"disabled menu leaves input untouched");
        check("mine_reservation".equals(Rules.mineKey(null,"VIDEO_BOOKING")),"verified reservation enum mapped");
        check(Rules.mineKey(null,"NOTE_CENTER")==null,"bookmarks are not reservations");
        Tab rec=new Tab("推荐",2),novel=new Tab("小说",25),future=new Tab("服务器新频道",1000);
        Mall source=new Mall();source.bookMallTabDataList=List.of(rec,novel,future);source.selectIndex=0;source.defaultTabType=2;
        source.defaultTabClientTemplate=rec.clientTemplate;source.defaultTabDataList=List.of(new Object());
        source.originalDataList=new RawTabs();source.originalDataList.tabItem=List.of(rec.originalTabData,novel.originalTabData,future.originalTabData);source.originalDataList.tabIndex=0;
        flags.add("channel_recommend");Mall filtered=(Mall)n.filterBookmall(source);
        check(filtered!=source&&filtered.bookMallTabDataList.equals(List.of(novel,future)),"native display list filters selected channel");
        check(filtered.selectIndex==0&&filtered.defaultTabType==25&&filtered.defaultTabClientTemplate==novel.clientTemplate,"replacement selection and template agree");
        check(filtered.defaultTabDataList.isEmpty(),"removed selection feed is not reused for replacement");
        check(source.bookMallTabDataList.size()==3&&source.originalDataList.tabItem.size()==3&&!source.defaultTabDataList.isEmpty(),"original cache remains unchanged");
        check(filtered.originalDataList!=source.originalDataList&&filtered.originalDataList.tabItem.get(0)==novel.originalTabData,"raw metadata copied and aligned with UI order");
        source.selectIndex=1;source.defaultTabType=25;source.defaultTabClientTemplate=novel.clientTemplate;
        filtered=(Mall)n.filterBookmall(source);check(filtered.selectIndex==0&&filtered.defaultTabDataList==source.defaultTabDataList,"retained selection preserves existing feed");
        flags.add("channel_novel");source.bookMallTabDataList=List.of(rec,novel);filtered=(Mall)n.filterBookmall(source);
        check(filtered.bookMallTabDataList.size()==1,"at least one actual channel remains");
        source.bookMallTabDataList=Collections.emptyList();check(n.filterBookmall(source)==source,"empty response left intact");
        Entrances entries=new Entrances();Item game=new Item(Entrance.Game),withdraw=new Item(Entrance.Withdraw),other=new Item(Entrance.Unknown);
        entries.mineEcomEntranceData=List.of(game,withdraw,other);Entrances kept=(Entrances)n.filterGameEntrances(entries);
        check(kept.mineEcomEntranceData.equals(List.of(withdraw,other))&&entries.mineEcomEntranceData.size()==3,"game filter preserves other benefits and cached data");
        Sidebar menu=new Sidebar();SidebarItem appointment=new SidebarItem(125,"我的预约"),settings=new SidebarItem(128,"设置"),newItem=new SidebarItem(999,"服务器新功能");
        menu.a=new ArrayList<>(List.of(appointment,settings,newItem));flags.clear();flags.add("mine_reservation");n.filterSidebarMenu(menu);
        check(menu.a.equals(List.of(settings,newItem)),"sidebar filters exact reservation title and preserves settings and unknown rows");
        Card notes=new Card(CardType.NOTE_CENTER,null),wallet=new Card(CardType.DOUYIN_WALLET,null),video=new Card(CardType.VIDEO_CREATOR,null);
        Card tabletGroup=new Card(CardType.COMMON,List.of(notes,wallet,video,unknown));
        flags.clear();flags.add("pad_notes");flags.add("pad_wallet");
        check(n.filterCard(tabletGroup,0)==tabletGroup,"tablet switches do not change phone cards");
        tablet[0]=true;Card tabletCopy=(Card)n.filterCard(tabletGroup,0);
        check(tabletCopy!=tabletGroup&&tabletCopy.b.equals(List.of(video,unknown)),"only selected tablet cards removed");
        check(tabletGroup.b.size()==4,"cached tablet card is not mutated");
        Sidebar tabletMenu=new Sidebar();SidebarItem noteItem=new SidebarItem(106,"我的笔记"),walletItem=new SidebarItem(124,"我的钱包"),retained=new SidebarItem(999,"钱包余额说明");
        tabletMenu.a=new ArrayList<>(List.of(noteItem,walletItem,settings,retained));n.filterSidebarMenu(tabletMenu);
        check(tabletMenu.a.equals(List.of(settings,retained)),"tablet sidebar retains settings and unrelated labels");
        flags.clear();check(n.filterCard(tabletGroup,0)==tabletGroup,"disabled tablet switches leave original cards intact");
        Card charity=new Card(CardType.CSR_ENTRANCE,null);Card charityGroup=new Card(CardType.COMMON,List.of(charity,wallet,unknown));
        flags.add("pad_public_welfare");tablet[0]=false;
        check(n.filterCard(charityGroup,0)==charityGroup,"charity switch does not affect phone cards");
        tablet[0]=true;Card charityCopy=(Card)n.filterCard(charityGroup,0);
        check(charityCopy.b.equals(List.of(wallet,unknown))&&charityGroup.b.size()==3,"charity removed without changing wallet or cached source");
        Sidebar charityMenu=new Sidebar();SidebarItem charityItem=new SidebarItem(112,"我的公益"),charityDescription=new SidebarItem(999,"我的公益记录说明");
        charityMenu.a=new ArrayList<>(List.of(charityItem,settings,charityDescription));n.filterSidebarMenu(charityMenu);
        check(charityMenu.a.equals(List.of(settings,charityDescription)),"only the exact charity menu is removed from sidebar");
        flags.clear();check(n.filterCard(charityGroup,0)==charityGroup,"disabled charity switch preserves the menu");
        System.out.println("NativeUiHooksTest passed");
    }
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
