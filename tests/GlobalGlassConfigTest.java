package io.github.mianxyz.fanqiecustomizer;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/** Exercises the real config implementation; no copied defaults or resolver. */
public final class GlobalGlassConfigTest {
    public static void main(String[] args) throws Exception {
        Map<String,Object> remote=new HashMap<>();
        remote.put("enabled",true);
        remote.put("glass_global_enabled",true);
        check(BookmallBusinessConfig.effective(remote,"enabled"),"module enabled");
        check(BookmallBusinessConfig.effective(remote,"glass_global_enabled"),"global enabled");
        check(BookmallBusinessConfig.effective(remote,"glass_global_card_text"),"missing remote card defaults true");
        check(BookmallBusinessConfig.effective(remote,"glass_global_panels"),"missing remote panels defaults true");
        check(BookmallBusinessConfig.manages("glass_global_rank_cover"),"rank cover managed by live config");
        check(!BookmallBusinessConfig.effective(remote,"glass_global_rank_cover"),"missing remote rank cover defaults false");
        check(!BookmallBusinessConfig.effective(Map.of("glass_global_rank_cover","true"),"glass_global_rank_cover"),"wrong-type rank cover defaults false");
        check(!BookmallBusinessConfig.effective(Map.of(),"glass_global_enabled"),"global master defaults false");
        BookmallBusinessConfig config=new BookmallBusinessConfig();
        check(config.value("glass_global_enabled")==null,"before accept global preserves initial flags fallback");
        check(config.value("glass_global_card_text")==null&&config.value("glass_global_panels")==null,"before accept children preserve initial flags fallback");
        check(config.value("glass_global_rank_cover")==null,"before accept rank cover preserves initial flags fallback");
        for (String key : List.of("glass_global_store_liquid", "glass_global_mine_liquid")) {
            check(BookmallBusinessConfig.manages(key), "liquid option managed: " + key);
            check(!BookmallBusinessConfig.effective(remote,key), "missing liquid option defaults false: " + key);
            check(!BookmallBusinessConfig.effective(Map.of(key,"true"),key), "wrong-type liquid option defaults false: " + key);
            check(config.value(key)==null, "before accept liquid option preserves initial fallback: " + key);
        }
        List<String> logs=new ArrayList<>();
        Field logger=BookmallBusinessConfig.class.getDeclaredField("log");
        logger.setAccessible(true);logger.set(config,(Consumer<String>)logs::add);
        Method accept=BookmallBusinessConfig.class.getDeclaredMethod("accept",Map.class);
        accept.setAccessible(true);accept.invoke(config,remote);
        check(Boolean.TRUE.equals(config.value("glass_global_enabled")),"after accept remote global true retained");
        check(Boolean.TRUE.equals(config.value("glass_global_card_text")),"value uses card effective default");
        check(Boolean.TRUE.equals(config.value("glass_global_panels")),"value uses panels effective default");
        check(Boolean.FALSE.equals(config.value("glass_global_rank_cover")),"after accept rank cover effective default false");
        check(config.value("refresh_confirm")==null,"missing old refresh retains fallback");
        check(config.value("hide_search_bar")==null,"missing old search retains fallback");
        check(config.value(Rules.CHANNEL_KEYS[0])==null,"missing old channel retains fallback");
        check(logs.equals(List.of("GlobalGlass config: enabled=true global=true cardText=true panels=true rankCover=false storeLiquid=false mineLiquid=false")),"first accepted state logged exactly once");
        accept.invoke(config,new HashMap<>(remote));
        remote.put("refresh_confirm",false);accept.invoke(config,remote);
        check(logs.size()==1,"same global state and old-business change do not repeat log");
        check(Boolean.FALSE.equals(config.value("refresh_confirm")),"explicit old false retained");
        remote.put("glass_global_card_text",false);remote.put("glass_global_panels",false);
        check(!BookmallBusinessConfig.effective(remote,"glass_global_card_text"),"explicit card false retained");
        check(!BookmallBusinessConfig.effective(remote,"glass_global_panels"),"explicit panels false retained");
        accept.invoke(config,remote);
        check(Boolean.FALSE.equals(config.value("glass_global_card_text"))&&Boolean.FALSE.equals(config.value("glass_global_panels")),"value preserves explicit child false");
        check(logs.size()==2&&logs.get(1).equals("GlobalGlass config: enabled=true global=true cardText=false panels=false rankCover=false storeLiquid=false mineLiquid=false"),"changed children produce one state log");
        remote.put("glass_global_enabled",false);remote.put("enabled",false);accept.invoke(config,remote);
        check(!BookmallBusinessConfig.effective(remote,"glass_global_enabled")&&!BookmallBusinessConfig.effective(remote,"enabled"),"explicit masters false retained");
        check(logs.size()==3&&logs.get(2).equals("GlobalGlass config: enabled=false global=false cardText=false panels=false rankCover=false storeLiquid=false mineLiquid=false"),"master changes produce one state log");
        accept.invoke(config,remote);check(logs.size()==3,"repeated disabled state silent");
        remote.put("glass_global_rank_cover",true);accept.invoke(config,remote);
        check(Boolean.TRUE.equals(config.value("glass_global_rank_cover")),"explicit rank cover true retained");
        check(logs.size()==4&&logs.get(3).equals("GlobalGlass config: enabled=false global=false cardText=false panels=false rankCover=true storeLiquid=false mineLiquid=false"),"rank cover changes effective state log");
        accept.invoke(config,new HashMap<>(remote));check(logs.size()==4,"repeated rank cover state silent");
        remote.put("glass_global_rank_cover",false);accept.invoke(config,remote);
        check(Boolean.FALSE.equals(config.value("glass_global_rank_cover"))&&logs.size()==5,"rank cover explicit false round trip");
        remote.put("glass_global_rank_cover","true");accept.invoke(config,remote);
        check(Boolean.FALSE.equals(config.value("glass_global_rank_cover"))&&logs.size()==5,"rank cover wrong-type map keeps false effective default without duplicate log");
        for (String key : List.of("glass_global_store_liquid", "glass_global_mine_liquid")) {
            check(Boolean.FALSE.equals(config.value(key)), "accepted missing liquid option returns false: " + key);
            int before = logs.size();
            remote.put(key,true); accept.invoke(config,remote);
            check(Boolean.TRUE.equals(config.value(key)), "liquid true round trip: " + key);
            String label = key.equals("glass_global_store_liquid") ? " storeLiquid=true" : " mineLiquid=true";
            check(logs.size()==before+1 && logs.get(before).contains(label), "liquid state change logged: " + key);
            accept.invoke(config,new HashMap<>(remote));
            check(logs.size()==before+1, "repeated liquid state silent: " + key);
            remote.put(key,false); accept.invoke(config,remote);
            check(Boolean.FALSE.equals(config.value(key)) && logs.size()==before+2, "liquid false round trip: " + key);
            remote.put(key,"true"); accept.invoke(config,remote);
            check(Boolean.FALSE.equals(config.value(key)) && logs.size()==before+2, "wrong-type liquid option falls back without duplicate log: " + key);
            remote.remove(key); accept.invoke(config,remote);
            check(Boolean.FALSE.equals(config.value(key)) && logs.size()==before+2, "removed liquid option returns effective default: " + key);
        }
        for (String key : List.of("hide_search_bar")) {
            check(BookmallBusinessConfig.manages(key), "live config manages " + key);
            remote.put(key, true); accept.invoke(config, remote);
            check(Boolean.TRUE.equals(config.value(key)), "live true " + key);
            remote.put(key, false); accept.invoke(config, remote);
            check(Boolean.FALSE.equals(config.value(key)), "live false " + key);
        }
        check(BookmallBusinessConfig.manages("rank_equal_columns"), "rank layout managed independently");
        remote.remove("rank_equal_columns");accept.invoke(config,remote);
        check(Boolean.FALSE.equals(config.value("rank_equal_columns")), "rank layout missing defaults false");
        remote.put("rank_equal_columns",true);accept.invoke(config,remote);
        check(Boolean.TRUE.equals(config.value("rank_equal_columns")), "rank layout hot enable");
        remote.put("rank_equal_columns",false);accept.invoke(config,remote);
        check(Boolean.FALSE.equals(config.value("rank_equal_columns")), "rank layout hot disable");
        System.out.println("GlobalGlassConfigTest passed (real map/value/accept and deduplicated state logs)");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
