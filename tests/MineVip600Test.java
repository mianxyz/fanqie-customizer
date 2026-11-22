package io.github.mianxyz.fanqiecustomizer;
import java.util.*;import java.nio.file.*;import android.view.View;
public final class MineVip600Test {
 public enum Kind{COMMON,AD_ORDER,READING_HISTORY,MY_MESSAGE,WRITER_CENTER,VIDEO_BOOKING,MINE_LIKE,BOOK_DOWNLOAD,MINI_GAME,FEEDBACK_AND_HELP,VIP,VIDEO_CREATOR,READING_PREFERENCE,NOTE_CENTER,MY_FOLLOW,USER_GUIDE,DOUYIN_WALLET,ACTIVITY_CENTER,CSR_ENTRANCE,MY_URGE_UPDATE,BOOK_COIN}
 public static final class Card{public final Kind a;public final List<Card> b;public Object c;public Card(Kind k,List<Card> l){a=k;b=l;}}
 static void ok(boolean v,String m){if(!v)throw new AssertionError(m);}
 public static void main(String[] args)throws Throwable{
  ok(Rules.allSettingKeys().length==90&&!Rules.defaultOn("mine_vip")&&!Rules.isIntKey("mine_vip"),"89/default/type");
  ok(Rules.MINE_KEYS.length==10&&Rules.MINE_LABELS[9].equals("会员中心"),"menu");
  for(String type:List.of("VIP","MemberCenter"))ok("mine_vip".equals(Rules.mineKey(null,type)),type);
  ok("mine_vip".equals(Rules.mineKey("会员中心",null)),"title");ok(Rules.mineKey(null,"BOOK_COIN")==null,"coin preserved");
  Set<String> flags=new HashSet<>();boolean[] tablet={true};
  NativeUiHooks h=new NativeUiHooks(null,new NativeUiHooks.Host(){public boolean on(String k){return flags.contains(k);}public boolean tabletLayout(){return tablet[0];}public void log(String s){}public void forceHide(View v,String s){}public void dataChanged(){}public void mineRoot(View v){}},Card.class.getClassLoader());
  List<Card> children=new ArrayList<>();for(Kind k:Kind.values())if(k!=Kind.COMMON)children.add(new Card(k,null));Card src=new Card(Kind.COMMON,List.copyOf(children));src.c=new Object();
  for(String key:Rules.MINE_KEYS){flags.clear();flags.add(key);Card out=(Card)h.filterCard(src,0);ok(out.b.size()==children.size()-1,"single "+key);}
  flags.clear();flags.add("pad_urge_update");Card urgeOut=(Card)h.filterCard(src,0);ok(urgeOut.b.size()==children.size()-1&&urgeOut.b.stream().noneMatch(c->c.a==Kind.MY_URGE_UPDATE),"urge only clean removal");tablet[0]=false;ok(h.filterCard(src,0)==src,"phone urge preserved");tablet[0]=true;
  ok(!Rules.defaultOn("pad_urge_update")&&!Rules.isIntKey("pad_urge_update"),"urge default/type");
  ok(!BookmallBusinessConfig.effective(Map.of(),"pad_urge_update")&&BookmallBusinessConfig.effective(Map.of("pad_urge_update",true),"pad_urge_update")&&!BookmallBusinessConfig.effective(Map.of("pad_urge_update",false),"pad_urge_update"),"urge live on/off/missing");
  ok(ControlCatalog.grouped().stream().anyMatch(e->e.id.equals("pad_urge_update")&&e.label.equals("我的催更")),"catalog urge");
  flags.addAll(Arrays.asList(Rules.MINE_KEYS));flags.addAll(Arrays.asList(TabletRules.KEYS));Card out=(Card)h.filterCard(src,0);ok(out.b.size()==1&&out.b.get(0).a==Kind.BOOK_COIN,"all hide/coin");ok(src.b.size()==20&&out.c==src.c,"cache metadata");
  Card all=new Card(Kind.COMMON,src.b.subList(0,19));ok(((Card)h.filterCard(all,0)).b.isEmpty(),"empty stays empty");tablet[0]=false;ok(((Card)h.filterCard(src,0)).b.size()==10,"phone keeps pad-only controls");flags.clear();ok(h.filterCard(src,0)==src,"off restores source");
  for(String key:Rules.MINE_KEYS)ok(BookmallBusinessConfig.manages(key),"live mine "+key);for(String key:TabletRules.KEYS)ok(BookmallBusinessConfig.manages(key),"live pad "+key);
  String module=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/ModuleHook.java"));ok(module.contains("public boolean tabletLayout(){return host.tabletLayout();}"),"native layout forwarding");ok(module.contains("new FeatureHooks(this,nativeHost,"),"shared live host");
  System.out.println("MineVip600Test passed (single/all/empty/phone/tablet/coin/cache/restore/live config/89 keys)");
 }
}
