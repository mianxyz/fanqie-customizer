package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
public final class GlobalGlass559Test {
 public static void main(String[] args)throws Exception{
  for(String id:new String[]{"ik","c7q","gat","fa_","kn0","ar"})check(GlobalGlassSurfaces.TopRules.candidate(id),"candidate "+id);
  check(!GlobalGlassSurfaces.TopRules.candidate("arw"),"bottom navigation excluded");
  check(!GlobalGlassSurfaces.TopRules.candidate("fue"),"mine excluded");
  check(GlobalGlassSurfaces.TopRules.whitePixel(0xffffffff),"opaque white");
  check(GlobalGlassSurfaces.TopRules.whitePixel(0xfff6f6f6),"neutral page");
  check(!GlobalGlassSurfaces.TopRules.whitePixel(0x08ffffff),"transparent pixels not white proof");
  check(!GlobalGlassSurfaces.TopRules.whitePixel(0xffe8ffed),"green pixels not white proof");
  check(!GlobalGlassSurfaces.TopRules.whitePixel(0xff202020),"dark pixels retained");
  String s=new String(HostBaselineEquivalenceTest.originalBytes("GlobalGlassSurfaces.java"), java.nio.charset.StandardCharsets.UTF_8);
  check(s.contains("for(View child:descendants(header))")&&s.contains("state.headerTree=true"),"whole background subtree enrolled");
  check(s.contains("state.headerTree||(exact&&state.pixels)"),"header identity independent of blank sample");
  check(s.contains("at=at.getSuperclass())try{")&&s.contains("getDeclaredMethod(name,Canvas.class)"),"overridden and inherited draw chains covered");
  check(s.contains("top559-bg class=")&&s.contains("topDrawableFact(current,0)"),"background evidence includes nested drawable facts");
  check(s.contains("if(white&&current!=null&&panels()&&visibleStoreOwner(view))"),"candidate replacement requires pixel proof and store");
  check(s.contains("restoreTopBackdrops(false)")&&s.contains("setPreservingPadding(view,bg.original)"),"original background restored");
  check(!s.contains("StoreTopGlassView"),"old feather is untouched");
  System.out.println("GlobalGlass559Test passed (top rules and source boundaries; device visuals pending)");
 }
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
