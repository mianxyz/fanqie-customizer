package io.github.mianxyz.fanqiecustomizer;
import java.util.*;
import java.lang.reflect.*;
import android.view.View;

/** Exercises filtering invariants with public model fields matching the inspected APK. */
public class FeatureHooksTest {
    public static class Channel {public String title;public int tabType;Channel(String t,int k){title=t;tabType=k;}}
    public static class Tabs {public List<Channel> tabItem;public int tabIndex;Tabs(List<Channel> list,int selected){tabItem=list;tabIndex=selected;}}
    public enum Kind {MyOrder,MyMessage,GoldBalance,Setting,Unknown,Bookmark,PublicWelfare}
    public static class Cell {
        public String myTabCellName;public Kind myTabCellType;public List<Cell> myTabSubCellData;public List<Kind> myTabSubCell;
        Cell(String title,Kind type){myTabCellName=title;myTabCellType=type;}
    }
    public static class Plan {public List<Cell> cellViewList;Plan(List<Cell> data){cellViewList=data;}}
    public static class NativeItem {public CharSequence title;NativeItem(String name){title=name;}}
    public static void main(String[] args)throws Throwable {
        Set<String> flags=new HashSet<>();
        boolean[] tablet={false};
        FeatureHooks f=new FeatureHooks(null,new FeatureHooks.Host(){
            public boolean on(String key){return flags.contains(key);}public void log(String text){}
            public boolean tabletLayout(){return tablet[0];}
            public void forceHide(View view,String reason){}public void dataChanged(){}
        },FeatureHooksTest.class.getClassLoader());
        Channel rec=new Channel("推荐",2),novel=new Channel("小说",25),unknown=new Channel("未知服务器频道",999);
        List<Channel> original=List.of(rec,novel,unknown);
        flags.add("channel_recommend");Tabs t=new Tabs(original,1);invoke(f,"filterChannels",t);
        check(t.tabItem.equals(List.of(novel,unknown)),"unknown channel preserved and original order retained");
        check(t.tabIndex==0,"selected channel remapped");check(original.size()==3,"input list not mutated");
        t=new Tabs(original,0);invoke(f,"filterChannels",t);check(t.tabIndex==0,"removed selection falls back to available channel");
        flags.add("channel_novel");t=new Tabs(List.of(rec,novel),1);invoke(f,"filterChannels",t);
        check(t.tabItem.equals(List.of(rec))&&t.tabIndex==0,"server subset never becomes an empty tab bar");
        t=new Tabs(Collections.emptyList(),0);invoke(f,"filterChannels",t);check(t.tabItem.isEmpty(),"empty server data retained");
        flags.clear();t=new Tabs(original,2);invoke(f,"filterChannels",t);check(t.tabItem==original&&t.tabIndex==2,"disabled settings do not alter data");
        flags.add("mine_order");flags.add("hide_income");
        Cell group=new Cell("常用功能",Kind.Unknown),order=new Cell("我的订单",Kind.MyOrder),message=new Cell("我的消息",Kind.MyMessage),setting=new Cell("设置",Kind.Setting);
        group.myTabSubCellData=List.of(order,message);group.myTabSubCell=List.of(Kind.MyOrder,Kind.Setting,Kind.Unknown);
        Plan p=new Plan(List.of(group,new Cell("收益",Kind.GoldBalance),setting));invoke(f,"filterMinePlan",p);
        check(p.cellViewList.equals(List.of(group,setting)),"income removed but settings retained");
        check(group.myTabSubCellData.equals(List.of(message)),"nested cell filtering");
        check(group.myTabSubCell.equals(List.of(Kind.Setting,Kind.Unknown)),"nested enum filtering preserves unknown items");
        check(Rules.mineKey(null,"Bookmark")==null,"bookmarks are not guessed to be reservations");
        List<NativeItem> nativeItems=List.of(new NativeItem("我的订单"),new NativeItem("设置"));
        Object filtered=invoke(f,"filterNativeItems",nativeItems);
        check(((List<?>)filtered).size()==1&&nativeItems.size()==2,"legacy items filtered without modifying source list");
        flags.clear();flags.add("pad_notes");
        Cell notes=new Cell("我的笔记",Kind.Bookmark);Plan phone=new Plan(List.of(notes,setting));invoke(f,"filterMinePlan",phone);
        check(phone.cellViewList.equals(List.of(notes,setting)),"tablet setting does not alter phone RPC menus");
        tablet[0]=true;Plan pad=new Plan(List.of(notes,setting));invoke(f,"filterMinePlan",pad);
        check(pad.cellViewList.equals(List.of(setting)),"selected tablet entry removed from RPC menu");
        List<NativeItem> tabletItems=List.of(new NativeItem("我的笔记"),new NativeItem("设置"),new NativeItem("我的钱包"));
        check(((List<?>)invoke(f,"filterNativeItems",tabletItems)).size()==2,"unselected tablet wallet and settings retained");
        flags.clear();pad=new Plan(List.of(notes,setting));invoke(f,"filterMinePlan",pad);
        check(pad.cellViewList.equals(List.of(notes,setting)),"disabled tablet entries preserved");
        Cell charity=new Cell("我的公益",Kind.PublicWelfare);flags.add("pad_public_welfare");tablet[0]=false;
        Plan charityPlan=new Plan(List.of(charity,setting));invoke(f,"filterMinePlan",charityPlan);
        check(charityPlan.cellViewList.equals(List.of(charity,setting)),"charity RPC menu retained on phone");
        tablet[0]=true;invoke(f,"filterMinePlan",charityPlan);
        check(charityPlan.cellViewList.equals(List.of(setting)),"charity RPC type hidden on tablet");
        List<NativeItem> charityItems=List.of(new NativeItem("我的公益"),new NativeItem("我的公益记录说明"),new NativeItem("设置"));
        check(((List<?>)invoke(f,"filterNativeItems",charityItems)).size()==2,"charity title matches exactly without removing other text");
        flags.clear();charityPlan=new Plan(List.of(charity,setting));invoke(f,"filterMinePlan",charityPlan);
        check(charityPlan.cellViewList.size()==2,"disabled charity RPC switch retains entry");
        System.out.println("FeatureHooksTest passed");
    }
    static Object invoke(Object object,String name,Object argument)throws Throwable {
        for(Method m:object.getClass().getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==1){m.setAccessible(true);try{return m.invoke(object,argument);}catch(InvocationTargetException e){throw e.getCause();}}
        throw new AssertionError("Method not found: "+name);
    }
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
}
