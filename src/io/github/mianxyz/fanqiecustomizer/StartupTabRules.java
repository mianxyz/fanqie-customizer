package io.github.mianxyz.fanqiecustomizer;

import java.util.*;

/** Resolve a startup destination against the actual server-provided bottom tabs. */
final class StartupTabRules {
    static final String[] KEYS={"start_store","start_series","start_earn","start_shelf","start_mine"};
    static final String[] TYPES={"BookStore","VideoSeriesFeedTab","LuckyBenefit","BookShelf","MyProfile"};
    private StartupTabRules() {}

    static String preferred(Map<String,?> values) {
        for(int i=0;i<KEYS.length;i++)if(Boolean.TRUE.equals(values.get(KEYS[i])))return TYPES[i];
        return null;
    }
    static boolean internalMain(String data) {
        return data!=null&&data.matches("(?i)^(?:dragon1967|sslocal)://main/?(?:\\?[^#]*)?(?:#.*)?$");
    }
    static boolean normalEntry(boolean restored,String action,String data,String tabName,Integer numericTarget) {
        if(restored||(data!=null&&!internalMain(data))||(action!=null&&!"android.intent.action.MAIN".equals(action)))return false;
        // AppNavigator.h supplies bookmall even for an ordinary startup. Other names are explicit routes.
        if(tabName!=null&&!tabName.trim().isEmpty()&&!"bookmall".equalsIgnoreCase(tabName.trim()))return false;
        return numericTarget==null||numericTarget==0;
    }
    static String choose(boolean enabled,String preferred,List<String> actual,Set<String> hidden,String original) {
        if(!enabled||actual==null||actual.isEmpty())return original;
        List<String> visible=new ArrayList<>();
        for(String name:actual)if(name!=null&&!hidden.contains(name))visible.add(name);
        // Match the bottom-bar guard if a server subset happens to contain only hidden tabs.
        if(visible.isEmpty())return actual.get(0);
        if(preferred!=null&&visible.contains(preferred))return preferred;
        if(visible.contains(original))return original;
        return visible.get(0);
    }
}
