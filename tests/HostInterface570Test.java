package io.github.mianxyz.fanqiecustomizer;
import java.util.*;
public final class HostInterface570Test {
 interface Parent {}
 interface Child extends Parent {}
 static class Ordinary {}
 public static void main(String[] args)throws Exception {
  HostSymbols.resetForTest();
  HostSymbols.register("fixture",570,Map.of("old.iface",Child.class.getName(),"old.class",Ordinary.class.getName()),Map.of(),Map.of(),Map.of());
  HostSymbols.registerValidation("fixture",570,new String[][]{{"C","old.iface",Child.class.getName(),"java.lang.Object",Parent.class.getName()},{"C","old.class",Ordinary.class.getName(),"java.lang.String",""}});
  HostSymbols.initialize("fixture",570,HostInterface570Test.class.getClassLoader(),s->{});
  if(HostSymbols.type("old.iface")!=Child.class)throw new AssertionError("DEX interface Object parent");
  boolean rejected=false;try{HostSymbols.type("old.class");}catch(ClassNotFoundException expected){rejected=true;}
  if(!rejected)throw new AssertionError("ordinary superclass mismatch admitted");
  HostSymbols.resetForTest();HostSymbols.initialize("com.dragon.read",73932,HostInterface570Test.class.getClassLoader(),s->{throw new AssertionError(s);});
  if(HostSymbols.type(Child.class.getName())!=Child.class)throw new AssertionError("baseline interface identity");HostSymbols.resetForTest();
  System.out.println("HostInterface570Test passed (DEX interface parent, ordinary shape rejection, baseline bypass)");
 }
}
