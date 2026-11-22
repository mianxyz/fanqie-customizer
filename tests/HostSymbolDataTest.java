package io.github.mianxyz.fanqiecustomizer;
import java.util.*;
public final class HostSymbolDataTest {
 public static void main(String[] args){
  HostSymbols.resetForTest();HostSymbolData.registerAll();HostSymbolData.registerAll();
  String[][] profiles={{"com.dragon.read","73932"},{"com.dragon.read","74132"},{"com.dragon.read","74332"},{"com.dragon.read","74532"},{"com.dragon.read.oversea.gp","73932"},{"com.dragon.read.oversea.gp","74133"}};
  for(String[] p:profiles){long code=Long.parseLong(p[1]);String[][] rows=HostSymbolData.validation(p[0],code);if(rows.length==0)throw new AssertionError("empty profile "+Arrays.toString(p));Set<String> keys=new HashSet<>();for(String[] r:rows){if(r.length<5)throw new AssertionError("bad validation row");String key=String.join("|",r);if(!keys.add(key))throw new AssertionError("duplicate "+key);}}
  HostSymbols.resetForTest();System.out.println("HostSymbolDataTest passed (five profiles, idempotent registration, exact validation inventories)");
 }
}
