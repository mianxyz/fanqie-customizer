package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
public final class RankGlass560Test {
 public static void main(String[] args)throws Exception {
  String rank=new String(HostBaselineEquivalenceTest.originalBytes("NativeCoverGlass.java"), java.nio.charset.StandardCharsets.UTF_8);
  check(rank.contains("restoreLayers();")&&rank.contains("neutral(bg)")&&rank.contains("foregrounds.put"),"neutral layers restored");
  check(rank.contains("recording.drawColor(fixedSource(night))")&&rank.contains("else canvas.drawColor(fixedSource(night))"),"full panel has a fixed source on both canvas paths");
  check(!rank.contains("drawBitmap")&&!rank.contains("coverRegion")&&!rank.contains("drawPureSource")&&!rank.contains("Snapshot"),"rank has no cover stitching or host-source fallback");
  check(rank.contains("area.height(),mistTop(night),mistBottom(night)")&&rank.contains("canvas.drawRect(area,brush)"),"mist fills complete panel without cell seams");
  String spacing=new String(HostBaselineEquivalenceTest.originalBytes("BookmallBusinessViews.java"), java.nio.charset.StandardCharsets.UTF_8);
  check(spacing.contains("!hideSearch&&hideChannels")&&spacing.contains("if(breathing)desired+=Math.round(10f*"),"spacing only with visible search and collapsed channels");
  System.out.println("RankGlass560Test passed (fixed-source isolation and panel coverage; device rendering checked separately)");
 }
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
