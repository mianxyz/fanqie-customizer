package io.github.mianxyz.fanqiecustomizer;
import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import io.github.libxposed.api.XposedModule;
final class BookmallBusinessConfig {
    private volatile Map<String,Boolean> values=Collections.emptyMap();
    private volatile boolean loading;
    private volatile boolean accepted;
    private java.util.function.Consumer<String> log=message->{};
    private String lastGlobalConfig;
    private SharedPreferences remote;
    private SharedPreferences.OnSharedPreferenceChangeListener listener;
    static boolean manages(String key){return Arrays.asList(Rules.MINE_KEYS).contains(key)||Arrays.asList(TabletRules.KEYS).contains(key)|| "enabled".equals(key)||"refresh_confirm".equals(key)||"hide_search_bar".equals(key)||"glass_global_enabled".equals(key)||"glass_global_card_text".equals(key)||"glass_global_panels".equals(key)||"rank_equal_columns".equals(key)||("glass_global_rank_cover".equals(key)||"glass_rank_edge_feather".equals(key))||"glass_global_store_liquid".equals(key)||"glass_global_mine_liquid".equals(key)||Arrays.asList(Rules.CHANNEL_KEYS).contains(key);}
    /** Shared effective boolean contract for accepted maps and diagnostic snapshots. */
    static boolean effective(Map<String,?> all,String key){
        Object value=all.get(key);
        return value instanceof Boolean?(Boolean)value:Rules.defaultOn(key);
    }
    private static boolean globalKey(String key){return "glass_global_enabled".equals(key)||"glass_global_card_text".equals(key)||"glass_global_panels".equals(key)||"rank_equal_columns".equals(key)||("glass_global_rank_cover".equals(key)||"glass_rank_edge_feather".equals(key))||"glass_global_store_liquid".equals(key)||"glass_global_mine_liquid".equals(key);}
    Boolean value(String key){return accepted&&(globalKey(key)||Arrays.asList(Rules.MINE_KEYS).contains(key)||Arrays.asList(TabletRules.KEYS).contains(key))?Boolean.valueOf(effective(values,key)):values.get(key);}
    void install(XposedModule module, Application app, java.util.function.Consumer<String> log){
        this.log=log;
        Runnable refresh=()->{
            if(loading)return; loading=true;
            new Thread(()->{try{
                SharedPreferences prefs=module.getRemotePreferences(Rules.PREFS);
                if(prefs!=null&&prefs.getAll().get("enabled") instanceof Boolean){
                    accept(prefs.getAll());
                    if(remote!=prefs){if(remote!=null&&listener!=null)remote.unregisterOnSharedPreferenceChangeListener(listener);remote=prefs;listener=(p,k)->accept(p.getAll());prefs.registerOnSharedPreferenceChangeListener(listener);}
                    log.accept("Bookmall business config: source=remote search="+values.get("hide_search_bar")+" refresh="+values.get("refresh_confirm"));
                }else{
                    Bundle b=app.getContentResolver().call(android.net.Uri.parse("content://"+SettingsProvider.AUTHORITY),SettingsProvider.METHOD,null,null);
                    if(b!=null&&b.containsKey("enabled")){Map<String,Object> m=new HashMap<>();for(String k:b.keySet())m.put(k,b.get(k));accept(m);log.accept("Bookmall business config: source=provider");}
                }
            }catch(Throwable e){log.accept("Bookmall business config FAILED: "+e.getClass().getSimpleName());}finally{loading=false;}},"Pulp-bookmall-config").start();
        };
        refresh.run();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks(){
            public void onActivityResumed(Activity a){refresh.run();RankImageClient.refresh(module,app);}
            public void onActivityCreated(Activity a,Bundle b){} public void onActivityStarted(Activity a){} public void onActivityPaused(Activity a){} public void onActivityStopped(Activity a){} public void onActivitySaveInstanceState(Activity a,Bundle b){} public void onActivityDestroyed(Activity a){}
        });
    }
    private synchronized void accept(Map<String,?> all){
        Map<String,Boolean> next=new HashMap<>();
        for(Map.Entry<String,?> e:all.entrySet())if(manages(e.getKey())&&e.getValue() instanceof Boolean)next.put(e.getKey(),(Boolean)e.getValue());
        values=Collections.unmodifiableMap(next);
        accepted=true;
        GlobalGlassSurfaces.configurationChanged();
        String state="GlobalGlass config: enabled="+effective(next,"enabled")
                +" global="+effective(next,"glass_global_enabled")
                +" cardText="+effective(next,"glass_global_card_text")
                +" panels="+effective(next,"glass_global_panels")
                +" rankCover="+effective(next,"glass_global_rank_cover")
                +" storeLiquid="+effective(next,"glass_global_store_liquid")
                +" mineLiquid="+effective(next,"glass_global_mine_liquid");
        if(!state.equals(lastGlobalConfig)){lastGlobalConfig=state;log.accept(state);}
    }
}
