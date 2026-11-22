package io.github.mianxyz.fanqiecustomizer;
import java.util.*;
import java.lang.reflect.*;
public final class HostReflection572Test {
 public static class Node {}
 public static class Background { public void update(Node n){} public void update(Object n){} }
 public static class Adapter { public int getItemCount(){return 7;} }
 public static class Child extends Adapter {}
 public static void main(String[] args)throws Exception {
  List<String> logs=new ArrayList<>();HostSymbols.resetForTest();
  HostSymbols.register("fixture",74532,Map.of("old.Background",Background.class.getName(),"old.Node",Node.class.getName(),"old.Adapter",Adapter.class.getName()),Map.of("old.Background#update","update","old.Background#update(Lold/Node;)V","update","old.Adapter#getItemCount()I","getItemCount"),Map.of(),Map.of());
  HostSymbols.initialize("fixture",74532,HostReflection572Test.class.getClassLoader(),logs::add);
  Method update=Background.class.getDeclaredMethod(HostSymbols.methodName(Background.class,"update"),Node.class);
  if(update.getParameterTypes()[0]!=Node.class)throw new AssertionError("typed update overload");
  Method count=Child.class.getMethod(HostSymbols.publicMethodName(Child.class,"getItemCount"));
  if(count.getDeclaringClass()!=Adapter.class||!count.invoke(new Child()).equals(7))throw new AssertionError("inherited count");
  if(HostSymbols.hasMethodMapping("missing.Page","Z","(Ljava/lang/String;)V"))throw new AssertionError("optional missing");
  if(!logs.isEmpty())throw new AssertionError(logs);
  Field registered=HostSymbolData.class.getDeclaredField("registered");registered.setAccessible(true);
  String[][] profiles={{"com.dragon.read","74132"},{"com.dragon.read","74332"},{"com.dragon.read","74532"},{"com.dragon.read.oversea.gp","73932"},{"com.dragon.read.oversea.gp","74133"}};
  for(String[] p:profiles){HostSymbols.resetForTest();registered.setBoolean(null,false);HostSymbolData.registerAll();HostSymbols.initialize(p[0],Long.parseLong(p[1]),null,logs::add);if(!HostSymbols.methodName("androidx.compose.foundation.BackgroundElement","update").equals("update"))throw new AssertionError(Arrays.toString(p));if(!HostSymbols.hasMethodMapping("androidx.compose.foundation.BackgroundElement","update","(Landroidx/compose/foundation/h;)V"))throw new AssertionError("exact update");}
  HostSymbols.resetForTest();HostSymbols.initialize("com.dragon.read",73932,null,logs::add);if(!HostSymbols.methodName(Background.class,"update").equals("update")||!HostSymbols.hasMethodMapping("missing.Page","Z","(Ljava/lang/String;)V"))throw new AssertionError("baseline");HostSymbols.resetForTest();
  System.out.println("HostReflection572Test passed (typed update reflection, inherited count invocation, five aliases, optional silent gate and baseline)");
 }
}
