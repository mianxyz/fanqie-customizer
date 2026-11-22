package io.github.mianxyz.fanqiecustomizer;

import java.util.*;

/** Prevent hidden/missing destinations, unexpected hot-start resets and fabricated tabs. */
public final class StartupTabRulesTest {
    public static void main(String[] args) {
        List<String> actual=List.of("BookStore","VideoSeriesFeedTab","LuckyBenefit","BookShelf","MyProfile");
        Set<String> hidden=Set.of("BookStore");
        check("BookShelf".equals(StartupTabRules.choose(true,"BookShelf",actual,hidden,"BookStore")),"preferred visible tab wins");
        check("VideoSeriesFeedTab".equals(StartupTabRules.choose(true,null,actual,hidden,"BookStore")),"automatic skips hidden original");
        check("BookShelf".equals(StartupTabRules.choose(true,null,actual,hidden,"BookShelf")),"automatic keeps visible native choice");
        check("BookStore".equals(StartupTabRules.choose(false,"BookShelf",actual,hidden,"BookStore")),"master off preserves native behavior");
        check("MyProfile".equals(StartupTabRules.choose(true,"BookShelf",List.of("BookStore","MyProfile"),hidden,"BookStore")),"missing preference falls back to actual visible tab");
        check("BookStore".equals(StartupTabRules.choose(true,"BookShelf",List.of(),hidden,"BookStore")),"no guessed tab before native list is ready");
        check("BookStore".equals(StartupTabRules.choose(true,"BookShelf",actual,new HashSet<>(actual),"MyProfile")),"all-hidden server subset matches bar fallback");
        check("FutureTab".equals(StartupTabRules.choose(true,"BookShelf",List.of("BookStore","FutureTab"),hidden,"BookStore")),"unknown visible tab retained");
        check("MyProfile".equals(StartupTabRules.choose(true,"BookStore",actual,hidden,"MyProfile")),"hidden preference never chosen");
        check(StartupTabRules.normalEntry(false,null,null,"bookmall",null),"native AppNavigator startup recognized");
        check(StartupTabRules.normalEntry(false,"android.intent.action.MAIN",null,null,0),"ordinary numeric bookstore default recognized");
        for(String route:new String[]{"dragon1967://main","dragon1967://main/","dragon1967://main/?from=launcher","sslocal://main?from=launcher"}){
            check(StartupTabRules.normalEntry(false,null,route,"bookmall",0),"native routed cold start must not be rejected");
            check(!StartupTabRules.normalEntry(false,"android.intent.action.VIEW",route,"bookmall",0),"external VIEW still retains its route");
            check(!StartupTabRules.normalEntry(false,null,route,"bookshelf",3),"explicit native routed tab still preserved");
        }
        for(String route:new String[]{"https://example.invalid/main","dragon1967://book/123","dragon1967://main.evil/","dragon1967://main/other","other://main","dragon1967://main@other"})
            check(!StartupTabRules.normalEntry(false,null,route,"bookmall",0),"other authorities and paths preserved");
        check(!StartupTabRules.normalEntry(true,null,null,"bookmall",null),"restored activity preserved");
        check(!StartupTabRules.normalEntry(false,"android.intent.action.VIEW",null,null,null),"external VIEW preserved");
        check(!StartupTabRules.normalEntry(false,null,"https://example.invalid/book","bookmall",null),"deep link preserved");
        for(String route:new String[]{"mine","bookshelf","goldcoin","seriesmall","shop","unknown-route"})
            check(!StartupTabRules.normalEntry(false,null,null,route,null),"explicit named route preserved");
        check(!StartupTabRules.normalEntry(false,null,null,null,3),"explicit shelf numeric route preserved");
        Map<String,Boolean> preferences=new HashMap<>();check(StartupTabRules.preferred(preferences)==null,"default is automatic");
        preferences.put("start_shelf",true);check("BookShelf".equals(StartupTabRules.preferred(preferences)),"Boolean startup selection");
        for(String key:StartupTabRules.KEYS)check(PrivacyRules.settingKey(key),"startup selection synchronized");
        System.out.println("StartupTabRulesTest passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
