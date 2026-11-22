package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
/** Source guards complement, but do not replace, Android transition frame review. */
public final class LiquidTransition568Test {
 public static void main(String[] args) throws Exception {
  String s=new String(HostBaselineEquivalenceTest.originalBytes("GlassSurfaces.java"), java.nio.charset.StandardCharsets.UTF_8);
  String viewport=s.substring(s.indexOf("private View mainContentViewport("),s.indexOf("private void appendVisiblePages("));
  check(viewport.contains("View root=bar.getRootView()"),"source follows current window");
  check(!viewport.contains("scanRoot!=null")&&!viewport.contains("!content.isShown()"),"no stale root or window visibility rejection");
  check(viewport.contains("child.getVisibility() != View.VISIBLE")&&viewport.contains("child != root"),"subtree visibility excludes only window root");
  check(viewport.contains("content.getRootView()!=root")&&viewport.contains("content.getWidth()<=0||content.getHeight()<=0"),"source identity and dimensions retained");
  String draw=s.substring(s.indexOf("private boolean drawBlurred("),s.indexOf("private Boolean loggedDark"));
  check(draw.indexOf("!canvas.isHardwareAccelerated()")<draw.indexOf("node = spareNode"),"software never records/draws RenderNode");
  check(s.contains("if (!blurred && liquidStyle()) a = dark ? 20 : 14;")&&s.contains("else if (!blurred) a = Math.min(255, a + 90)"),"liquid mist bypasses legacy opaque tint");
  check(s.contains("if (!canvas.isHardwareAccelerated()) return sampleFailure"),"retained hardware path guarded");
  for(String field:new String[]{"reason="," hw="," dsvVis="," barVis="," nodeHasList="," sourceHasList="," rootMatch="," scanRootCurrent="})check(s.contains(field),"diagnostic "+field);
  for(String reason:new String[]{"software-canvas","retained-unavailable","api-below-31","dsv-zero-size","dsv-missing-current-root","dsv-root-mismatch","dsv-subtree-hidden","dsv-recursion-guard","record-or-draw-"})check(s.contains(reason),"failure branch "+reason);
  check(s.contains("failureReasons.add(key)"),"each reason logged once");
  check(!s.contains("Bitmap.createBitmap")&&!s.contains("PixelCopy"),"fallback stores no cross-tab pixels");
  System.out.println("LiquidTransition568Test passed (source guards; device video required)");
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
