package io.github.mianxyz.fanqiecustomizer;
import java.util.*;
public final class HostPublic571Test {
 public static class Parent { public List<String> getFragments(){return List.of();} public int unrelated(){return 0;} public String getTag(){return "tag";} }
 public static class Child extends Parent {}
 public interface Scope { void D1(); }
 public static class Factory { public static Object create(Object x,int mask){return x;} public static Object unrelated(Object x,int mask){return x;} }
 public static void main(String[] args)throws Exception {
  HostSymbols.resetForTest();List<String> logs=new ArrayList<>();
  Map<String,String> c=Map.of("old.Parent",Parent.class.getName(),"old.Child",Child.class.getName(),"old.Scope",Scope.class.getName(),"old.Factory",Factory.class.getName());
  Map<String,String> m=Map.of("old.Parent#getTag()Ljava/lang/String;","getTag","old.Parent#getFragments()Ljava/util/List;","getFragments","old.Scope#A1()V","D1","old.Factory#factory(Ljava/lang/Object;I)Ljava/lang/Object;","create");
  HostSymbols.register("fixture",74133,c,m,Map.of(),Map.of());
  HostSymbols.initialize("fixture",74133,HostPublic571Test.class.getClassLoader(),logs::add);
  if(!HostSymbols.publicMethodName(Child.class,"getFragments").equals("getFragments"))throw new AssertionError("inherited declaring owner");
  if(!HostSymbols.publicMethodName(Scope.class,"A1").equals("D1"))throw new AssertionError("renamed scope");
  if(!HostSymbols.publicMethodName(Factory.class,"factory",Object.class,int.class).equals("create"))throw new AssertionError("exact static");
  if(!HostSymbols.publicMethodName(Child.class,"getTag").equals("getTag"))throw new AssertionError("Fragment-style inherited getter");
  if(!logs.isEmpty())throw new AssertionError("candidate enumeration emitted false failures: "+logs);
  if(!HostSymbols.publicMethodName(Child.class,"unmapped").contains("__missing_host_symbol__"))throw new AssertionError("unmapped bypass");
  if(logs.size()!=1)throw new AssertionError("one terminal diagnostic");
  HostSymbols.resetForTest();HostSymbols.initialize("com.dragon.read",73932,null,logs::add);
  if(!HostSymbols.publicMethodName(Scope.class,"A1").equals("A1"))throw new AssertionError("baseline changed");
  HostSymbols.resetForTest();System.out.println("HostPublic571Test passed (inheritance, exact static, rename, no candidate false errors, missing and baseline)");
 }
}
