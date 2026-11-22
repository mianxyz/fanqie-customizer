package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.*;

/** Production cache/identity behavior; screen output remains an on-device acceptance check. */
public final class CardGlass559Test {
 public static void main(String[] args)throws Exception {
  check(CardGlassHooks.SourceRules.cacheKey(null,null,false)==null,"no empty cache keys");
  check(CardGlassHooks.SourceRules.cacheKey("  ","",true)==null,"blank values excluded");
  String day=CardGlassHooks.SourceRules.cacheKey("cover-A","cover-B",false);
  String night=CardGlassHooks.SourceRules.cacheKey("cover-A","cover-B",true);
  check(day.equals("day:cover-A")&&night.equals("night:cover-B"),"exact day/night URL selection");
  check(!day.equals(CardGlassHooks.SourceRules.cacheKey("cover-A",null,true)),"night fallback stays partitioned");
  check(!day.equals(CardGlassHooks.SourceRules.cacheKey("cover-C","cover-B",false)),"book URL isolation");
  CardGlassHooks.SourceCache<Integer> cache=new CardGlassHooks.SourceCache<>(48);
  cache.put(null,7);check(cache.size()==0,"null key never retained");
  for(int i=0;i<48;i++)cache.put("day:"+i,i);
  check(cache.get("day:0")==0,"touch oldest");cache.put("day:48",48);
  check(cache.size()==48&&cache.get("day:1")==null&&cache.get("day:0")==0,"access-order LRU eviction");
  check(cache.get("night:0")==null,"no cross-mode cache reuse");
  cache.put("day:0",99);check(cache.size()==48&&cache.get("day:0")==99,"replace exact-key source");
  String source=new String(HostBaselineEquivalenceTest.originalBytes("CardGlassHooks.java"), java.nio.charset.StandardCharsets.UTF_8);
  check(source.contains("!card.render.hasDisplayList()"),"discarded display list is rebuilt");
  check(source.contains("nodeElements")&&source.contains("draw-reattach")&&source.contains("bgNodes.removeValue(c)"),"detach and reused-node rebinding");
  check(source.contains("Objects.equals(bound.key,activeKey(card))"),"late painter must match current URL");
  check(source.contains("if(activeKey(card)==null)return;"),"unknown URLs never populate cache");
  check(source.contains("0x52FFFFFF,bottom,Shader.TileMode.CLAMP")&&source.contains("0x78FFFFFF:0x72FFFFFF")&&source.contains("0x99FFFFFF"),"558 day appearance preserved");
  check(source.contains("hw=true")&&source.contains("hw=false")&&source.contains("drawCover(c,scope)"),"cover hard/software branches retain content");
  for(String name:new String[]{"render-rebuild","render-state","cache-hit","rebind","root","new"})check(source.contains("\""+name+"\""),"diagnostic "+name);
  check(SquareGlassHooks.full(631,706,631,706),"full square background accepted");
  check(!SquareGlassHooks.full(100,40,631,706),"small square button excluded");
  check(!SquareGlassHooks.full(Float.NaN,706,631,706),"invalid square scope excluded");
  String square=new String(HostBaselineEquivalenceTest.originalBytes("SquareGlassHooks.java"), java.nio.charset.StandardCharsets.UTF_8);
  check(square.contains("PreComposeView")&&square.contains("f5y")&&square.contains("e5u")&&square.contains("f5w"),"exact square lineage");
  check(!square.contains("PixelCopy")&&!square.contains("source.draw("),"square uses images only");
  check(square.contains("readState(t)")&&square.contains("if(!enabled())return c.proceed()"),"square restores original background when off");
  System.out.println("CardGlass559Test passed (URL identity, bounded LRU, return-path boundaries)");
 }
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
}
