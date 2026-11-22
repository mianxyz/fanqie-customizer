package io.github.mianxyz.fanqiecustomizer;
import java.util.*;
public final class HostAdaptation570Test {
 static void fresh() throws Exception { HostSymbols.resetForTest(); var field=HostSymbolData.class.getDeclaredField("registered"); field.setAccessible(true); field.setBoolean(null,false); HostSymbolData.registerAll(); }
 public static void main(String[] args) throws Exception {
  String[][] rows={{"com.dragon.read","74132","g62","l1","me4.n0","h3m","e3j"},{"com.dragon.read","74332","g_h","l1","qf4.n0","h88","e76"},{"com.dragon.read","74532","gb7","l1","og4.n0","h9v","e8b"},{"com.dragon.read.oversea.gp","73932","mine_search_bar","recycler_view","pa4.n0","recommend_bg_view","header_layout_bg"}};
  for(String[] r:rows){fresh();HostSymbols.initialize(r[0],Long.parseLong(r[1]),HostAdaptation570Test.class.getClassLoader(),s->{});
   String[] keys={"g64","l1","h3q","e3m"};int[] positions={2,3,5,6};
   for(int i=0;i<keys.length;i++){String actual=HostSymbols.resourceName(keys[i]);if(!actual.equals(r[positions[i]])||!HostSymbols.canonicalResource(actual).equals(keys[i]))throw new AssertionError(Arrays.toString(r)+" "+keys[i]);}
   if(!HostSymbols.name("ke4.n0").equals(r[4]))throw new AssertionError("mine role "+Arrays.toString(r));
   if(!HostSymbols.name("com.dragon.read.widget.nestedrecycler.a").equals("com.dragon.read.widget.nestedrecycler.a"))throw new AssertionError("nested role");
  }
  fresh();HostSymbols.initialize("com.dragon.read",73932,HostAdaptation570Test.class.getClassLoader(),s->{throw new AssertionError("baseline logged");});
  for(String n:List.of("g64","l1","h3q","e3m"))if(!HostSymbols.resourceName(n).equals(n))throw new AssertionError("baseline resource");
  if(!HostSymbols.name("ke4.n0").equals("ke4.n0"))throw new AssertionError("baseline class");HostSymbols.resetForTest();
  System.out.println("HostAdaptation570Test passed (four profile role consumption and baseline identity)");
 }
}
