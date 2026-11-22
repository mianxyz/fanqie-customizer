package io.github.mianxyz.fanqiecustomizer;
import java.util.*;import java.lang.reflect.*;import android.view.View;
public final class MineField600Test {
 public enum Kind{COMMON,READING_HISTORY,CREATION_CENTER,POLARIS,UNKNOWN}
 public static final class Card{public final Kind a;public final List<Card> b;public List<?> c;public Card(Kind kind,List<Card> children){a=kind;b=children;}}
 public static void main(String[] args)throws Throwable{
  for(String pkg:new String[]{"com.dragon.read","com.dragon.read.oversea.gp"})for(long code:(pkg.endsWith("gp")?new long[]{73932,74133}:new long[]{74132,74332,74532})){
   HostSymbols.resetForTest();HostSymbolData.registerCurrent(pkg,code);
   Field profiles=HostSymbols.class.getDeclaredField("PROFILES");profiles.setAccessible(true);Map<?,?> all=(Map<?,?>)profiles.get(null);
   if(all.size()!=1)throw new AssertionError("eager profiles "+all.size());
   Object p=all.values().iterator().next();Field ff=p.getClass().getDeclaredField("fields");ff.setAccessible(true);Map<?,?> fields=(Map<?,?>)ff.get(p);
   Map<String,String> mapped=new HashMap<>();for(String key:List.of("sw3.d#a","sw3.d#b","sw3.d#c","sw3.d#a:Lcom/dragon/read/component/biz/api/model/CardType;","sw3.d#b:Ljava/util/List;","sw3.d#c:Ljava/util/List;")){Object v=fields.get(key);if(v==null)throw new AssertionError(pkg+code+key);mapped.put(key,(String)v);}
   HostSymbols.resetForTest();HostSymbols.register(pkg,code,Map.of("sw3.d",Card.class.getName(),"com.dragon.read.component.biz.api.model.CardType",Kind.class.getName()),Map.of(),mapped,Map.of());HostSymbols.initialize(pkg,code,Card.class.getClassLoader(),s->{});
   Set<String> flags=new HashSet<>(List.of("mine_history","mine_creator","hide_income"));
   NativeUiHooks hooks=new NativeUiHooks(null,new NativeUiHooks.Host(){public boolean on(String key){return flags.contains(key);}public void log(String text){}public void forceHide(View v,String why){}public void mineRoot(View v){}public void dataChanged(){}},Card.class.getClassLoader());
   Card keep=new Card(Kind.UNKNOWN,null),source=new Card(Kind.COMMON,List.of(new Card(Kind.READING_HISTORY,null),new Card(Kind.CREATION_CENTER,null),new Card(Kind.POLARIS,null),keep));source.c=List.of("metadata");
   Card filtered=(Card)hooks.filterCard(source,0);if(filtered==source||!filtered.b.equals(List.of(keep))||source.b.size()!=4||filtered.c!=source.c)throw new AssertionError("filter/copy "+code);
   flags.clear();if(hooks.filterCard(source,0)!=source)throw new AssertionError("off restore");
  }HostSymbols.resetForTest();System.out.println("MineField600Test passed (five nonbaseline profiles; reflective filtering and cache preservation)");
 }
}
