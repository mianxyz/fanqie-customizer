package io.github.mianxyz.fanqiecustomizer;
import java.util.*;
import java.nio.file.*;
/** 560 scoped DSL/structure rules; device rendering is verified by lead. */
public final class CardSquare560Test {
 public static void main(String[] args)throws Exception {
  Node dynamic=new Node(3,null),ordinary=new Node(101,null),text=new Node(100,null);
  check(CardGlassHooks.cardShape(new Node(100,new Object(),dynamic,text)),"published dynamic cover matches");
  check(CardGlassHooks.cardShape(new Node(100,new Object(),ordinary,text)),"ordinary 101 preserved");
  check(!CardGlassHooks.cardShape(new Node(101,new Object(),dynamic,text)),"wrong root excluded");
  check(!CardGlassHooks.cardShape(new Node(100,new Object(),dynamic,new Node(100,new Object()))),"nested painted text excluded");
  check(!CardGlassHooks.cardShape(new Node(100,new Object(),new Node(2,null),text)),"text as cover excluded");
  check(CardGlassHooks.mainCoverDraw(632,706,632),"major dynamic cover admitted");
  check(!CardGlassHooks.mainCoverDraw(60,80,632),"badge excluded");
  for(String type:new String[]{"androidx.compose.ui.platform.ComposeView","androidx.compose.ui.precompose.PreComposeView"}) {
   check(SquareGlassHooks.squareLineage(type,"f5y","e5u",false),"generic exact lineage");
   check(!SquareGlassHooks.squareLineage(type,"f5y","e5u",true),"ordinary book host excluded");
   check(!SquareGlassHooks.squareLineage(type,"other","e5u",false),"unrelated frame excluded");
  }
  check(SquareGlassHooks.smallCover(160,220,632),"portrait three-up source");
  check(SquareGlassHooks.smallCover(240,330,1000),"landscape resized source");
  check(!SquareGlassHooks.smallCover(632,706,632),"full-width poster excluded");
  check(!SquareGlassHooks.smallCover(24,24,632),"icon excluded");
  String square=new String(HostBaselineEquivalenceTest.originalBytes("SquareGlassHooks.java"), java.nio.charset.StandardCharsets.UTF_8);
  check(!square.contains("g5o")&&!square.contains("ratio>=")&&!square.contains("getOutlineProvider"),"no page/dimension/host-radius dependence");
  check(square.contains("t.images.size()!=3")&&square.contains("t.images.size()==3"),"three independent cover gate for paint and clip");
  check(square.contains("setStrokeWidth(.75f*density)")&&square.contains("radius=8*density"),"8dp corners and soft 0.75dp rim");
  check(!square.contains("PixelCopy"),"independent image sources only");
  System.out.println("CardSquare560Test passed");
 }
 static final class Node {final int b;final Object c;final List<Node> e;Node(int type,Object bg,Node... children){b=type;c=bg;e=List.of(children);}}
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
