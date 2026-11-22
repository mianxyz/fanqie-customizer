package io.github.mianxyz.fanqiecustomizer;

import java.util.*;

/** Cover real server subsets, stable indices, unknown entries and the independent startup icon row. */
public final class BottomNavigationRulesTest {
    public static void main(String[] args){
        List<String> actual=List.of("BookStore","VideoSeriesFeedTab","LuckyBenefit","BookShelf","MyProfile");
        Set<String> keys=Set.of("hide_store","hide_earn","hide_mine");
        check(Arrays.equals(BottomNavigationRules.hidden(true,actual,keys),new boolean[]{true,false,true,false,true}),"only configured buttons hidden with original index positions");
        check(actual.equals(List.of("BookStore","VideoSeriesFeedTab","LuckyBenefit","BookShelf","MyProfile")),"native list/order remains unchanged");
        check(Arrays.equals(BottomNavigationRules.hidden(false,actual,keys),new boolean[5]),"master off leaves all native choices intact");
        check(Arrays.equals(BottomNavigationRules.hidden(true,actual,Set.of()),new boolean[5]),"no hide settings preserves bar");
        check(BottomNavigationRules.hidden(true,List.of(),keys).length==0&&BottomNavigationRules.hidden(true,null,keys).length==0,"empty native lists not fabricated");
        check(Arrays.equals(BottomNavigationRules.hidden(true,List.of("BookStore","MyProfile"),keys),new boolean[]{false,true}),"server subset retains first real button when every real entry is hidden");
        check(!BottomNavigationRules.hidden(true,List.of("BookStore"),keys)[0],"single real entry is kept");
        check(Arrays.equals(BottomNavigationRules.hidden(true,List.of("BookStore","FutureTab"),keys),new boolean[]{true,false}),"unknown actual entry stays visible without restoring hidden store");
        check(Arrays.equals(BottomNavigationRules.hidden(true,Arrays.asList(null,"ShopMall","Bookshelf"),Set.of("hide_shelf")),new boolean[]{false,false,true}),"unknown enum and shopping entry retained; shelf alias mapped");
        check(!BottomNavigationRules.animation(true,keys,true),"five-icon startup overlay disabled while hiding entries");
        check(BottomNavigationRules.animation(false,keys,true)&&BottomNavigationRules.animation(true,Set.of(),true),"animation behavior retained when hiding inactive");
        check(!BottomNavigationRules.animation(true,Set.of(),false),"native disabled animation is not enabled");
        check(BottomNavigationRules.animation(true,Set.of("unknown_setting"),true),"unrelated settings do not disable animation");
        String selected=StartupTabRules.choose(true,null,List.of("BookStore","MyProfile"),Set.of("BookStore","MyProfile"),"MyProfile");
        check("BookStore".equals(selected),"all-hidden native subset agrees with existing startup fallback");
        System.out.println("BottomNavigationRulesTest passed");
    }
    private static void check(boolean value,String label){if(!value)throw new AssertionError(label);}
}
