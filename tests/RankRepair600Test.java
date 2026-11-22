package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
public final class RankRepair600Test {
 static void ok(boolean x,String m){if(!x)throw new AssertionError(m);}
 static String source(String n)throws Exception{return Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer",n));}
 public static void main(String[] args)throws Exception{
  ok(Rules.allSettingKeys().length==90,"90 keys");ok(!Rules.defaultOn("glass_rank_edge_feather")&&!Rules.isIntKey("glass_rank_edge_feather"),"feather opt-in boolean");
  ok(BookmallBusinessConfig.manages("glass_rank_edge_feather"),"feather live config");
  String n=source("NativeCoverGlass.java"),e=source("RankEdgeGlass.java"),c=source("RankImageClient.java");
  ok(!n.contains("now-lastPreDraw")&&!n.contains("parent.addView(layer")&&!n.contains("layer.layout("),"no delayed or geometry restore");
  String detach=n.substring(n.indexOf("public void onViewDetachedFromWindow"));ok(!detach.contains("restore(v)"),"detach retains cache");
  ok(n.contains("chain.proceed(new Object[]{cached})")&&n.contains("preservePadding(view,this)"),"background boundary and cached restore");
  ok(e.contains("setHorizontalFadingEdgeEnabled(feather)")&&!e.contains("Color.WHITE")&&!e.contains("new ColorDrawable"),"alpha fade without white overlay");
  ok(n.contains("BitmapShader")&&n.contains("Math.max(area.width()/custom.getWidth()"),"cached crop shader");
  ok(RankImageStore.sampleSize(20000,10000)>=8,"bounded decode");ok(RankImageStore.sampleSize(1440,800)==1,"normal decode");
  ok(RankImageStore.validRevision("default")&&!RankImageStore.validRevision("../image")&&!RankImageStore.validRevision("content://image"),"revision validation");
  ok(RankImageStore.validRevision("a".repeat(64))&&RankImageStore.fileName("a".repeat(64)).endsWith(".jpg"),"hash revision");
  ok(c.indexOf("next.equals(revision)")<c.indexOf("module.openRemoteFile"),"unchanged skips image disk decode");
  ok(c.contains("Image hash mismatch")&&c.contains("MAX_BYTES")&&c.contains("MAX_EDGE"),"remote input bounds");
  String w=source("WidgetPromptHooks.java");ok(w.contains("hide_widget_prompt")&&w.contains("Boolean.FALSE")&&w.contains("getParameterCount()==9"),"permission gate and typed return");
  System.out.println("RankRepair600Test passed");
 }
}
