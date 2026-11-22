package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
/** Source isolation checks; screenshots/logs are required separately for optical quality. */
public final class PanelLiquid564Test {
 public static void main(String[] args)throws Exception{
  Path root=Path.of("src/io/github/mianxyz/fanqiecustomizer");
  String s=new String(HostBaselineEquivalenceTest.originalBytes("GlobalGlassSurfaces.java"), java.nio.charset.StandardCharsets.UTF_8);
  check(s.contains("if(dialog||!panels()||Build.VERSION.SDK_INT<33)return false"),"API and panels gate");
  check(s.contains("host.on(\"glass_global_store_liquid\")&&(search||\"iyd\".equals(idName(v)))"),"store liquid excludes ranking and feed");
  check(s.contains("\"mine\".equals(kind)&&host.on(\"glass_global_mine_liquid\")"),"mine liquid independent gate");
  check(s.contains("liquid.apply(node,w,h,opticalRadius*SCALE")&&s.contains("if(!liquidActive)node.setRenderEffect(RenderEffect.createBlurEffect(pixels,pixels,Shader.TileMode.CLAMP))"),"liquid backdrop and Gaussian fallback");
  check(s.contains("recordOwner=bottomMask&&anchor!=null?anchor:v")&&s.contains("parentSurface.radius"),"hp shares full fue optical geometry");
  check(s.contains("0x301E2028")&&s.contains("0x42242832")&&s.contains("0x40FFFFFF"),"drama light mist and bright border");
  check(s.contains("clearDramaSearchBackdrops(search,v)")&&s.contains("originalSuppressed=true blurDp=10"),"drama original solid layer suppressed");
  check(s.contains("((TransparentBackground)entry.getKey().getBackground()).original==entry.getValue()"),"clear is identity checked and reversible");
  String card=HexFormat.of().withUpperCase().formatHex(MessageDigest.getInstance("SHA-256").digest(HostBaselineEquivalenceTest.originalBytes("CardGlassHooks.java")));
  check(card.equals("271FF321655F6E4A0B65F99AD6C828F7520226EF4038F1333481F97B199A0634"),"existing card pixels frozen");


  System.out.println("PanelLiquid564Test passed (gates, fallback, shared mask geometry, reversible drama, frozen cards)");
 }
 static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
}
