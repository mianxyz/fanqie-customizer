package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;import java.util.*;
public final class GlassBars600Test {
 public static void main(String[] args)throws Exception {
  for(int value:new int[]{Integer.MIN_VALUE,-1,0,50,100,999,Integer.MAX_VALUE})check(value);for(int value=0;value<=100;value++)check(value);
  near(LiquidGlassRenderer.Rules.bounceMap(50,1.04f,1.12f,1.18f),1.12f);var middle=LiquidGlassRenderer.Rules.bounce(50);near(middle.posDamping(),.5f);near(middle.sxDamping(),.6f);near(middle.syDamping(),.7f);near(middle.pressScale(),1.16f);near(middle.velocityScale(),.04f);
  for(int width:new int[]{360,375,393,412,768,2000})for(int count=1;count<=5;count++){int end=0;for(int n=0;n<count;n++){int next=BottomNavigationRules.slotEdge(width,n+1,count);ok(next>end);ok(Math.abs((next-end)-width/(float)count)<1.1f);end=next;}ok(end==width);}
  ok(!BottomNavigationRules.visibleSlot(true,false,8,100,false,true));ok(!BottomNavigationRules.visibleSlot(true,false,0,0,false,true));ok(!BottomNavigationRules.visibleSlot(true,false,0,100,true,true));ok(!BottomNavigationRules.visibleSlot(true,true,0,100,false,true));ok(BottomNavigationRules.visibleSlot(true,false,0,0,false,false));
  for(int x=-100;x<600;x++)ok(Set.of(0,2,3,4).contains(BottomNavigationRules.nearest(x,new int[]{0,2,3,4},new float[]{50,150,250,350})));ok(BottomNavigationRules.nearest(2,new int[]{},new float[]{})==-1);
  var types=List.of("BookStore","VideoSeriesFeedTab","LuckyBenefit","Bookshelf","MyProfile");for(String label:List.of("福利","领现金","0.5元","","赚钱")){ok(!BottomNavigationRules.hidden(true,types,Set.of("hide_welfare_float"))[2]);ok(BottomNavigationRules.hidden(true,types,Set.of("hide_earn"))[2]);}
  ok(Rules.allSettingKeys().length==90&&Rules.intDefault("glass_bar_bounce")==50&&PrivacyRules.settingKey("glass_bar_bounce"));
  String source=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/GlassSurfaces.java"));ok(source.contains("!visibleTab(bar,index)"));ok(source.contains("fitVisibleTabs(bar,track)"));ok(source.contains("c instanceof ViewGroup || c instanceof GlassBackdropView"));ok(source.contains("+400L"));ok(source.contains("rc.drawRenderNode(backdrop.sourceNode)"));
  System.out.println("GlassBars600Test passed (all bounce values and extremes converge; visible slots; 360..412dp/ tablet equal widths; earn enum independent of title)");
 }
 static void check(int value){var p=LiquidGlassRenderer.Rules.bounce(value);for(float d:new float[]{p.posDamping(),p.sxDamping(),p.syDamping()}){ok(d>.30f&&d<.92f);LiquidGlassRenderer.Spring spring=new LiquidGlassRenderer.Spring(0);spring.target=400;spring.velocity=8000;for(int n=0;n<2400;n++){spring.step(1f/120f,300,d);ok(Float.isFinite(spring.value)&&Float.isFinite(spring.velocity));}ok(spring.settled());}ok(p.pressScale()>=1.08f&&p.pressScale()<=1.22001f);}
 static void near(float a,float b){ok(Math.abs(a-b)<.00001f);}static void ok(boolean b){if(!b)throw new AssertionError();}
}
