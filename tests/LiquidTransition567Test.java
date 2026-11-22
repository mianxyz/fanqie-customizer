package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
/** Source guards; real RenderNode continuity is verified separately by device video. */
public final class LiquidTransition567Test {
 public static void main(String[] args) throws Exception {
  String s=new String(HostBaselineEquivalenceTest.originalBytes("GlassSurfaces.java"), java.nio.charset.StandardCharsets.UTF_8);
  String draw=s.substring(s.indexOf("private boolean drawBlurred("),s.indexOf("private Boolean loggedDark"));
  check(draw.indexOf("mainContentViewport(bar)")<draw.indexOf("node = spareNode"),"validate source before touching committed nodes");
  check(!draw.substring(0,draw.indexOf("View src =")).contains("discardDisplayList"),"no unconditional frame discard");
  check(draw.contains("spareNode = committedNode")&&draw.contains("node = committedNode; sourceNode = committedSource"),"commit and rollback use separate recording nodes");
  check(draw.contains("src.draw(rc)")&&draw.contains("liquid.apply(node"),"visible draw records viewport and reapplies optics synchronously");
  check(s.contains("sampleTab != selectedTab(bar)")&&s.contains("sampleRoot != bar.getRootView()"),"retained frame isolated by tab and window");
  check(s.contains("sampleWidth != w || sampleHeight != h")&&s.contains("sampleLiquid != liquidStyle()"),"size and style isolate retained frame");
  check(s.contains("!node.hasDisplayList()")&&s.contains("!sourceNode.hasDisplayList()"),"reclaimed lists never used");
  check(s.contains("if (visibility == View.VISIBLE) { transitionDrawLogged=false; invalidate(); }"),"return first frame redraw requested");
  check(!s.substring(s.indexOf("@Override protected void onDraw(Canvas canvas)", s.indexOf("private final class GlassBackdropView")), s.indexOf("private void drawGlass(")).contains("0xCCFFFFFF"),"no opaque-white exception fallback");
  check(s.contains("onActivityPaused(android.app.Activity a) { }")&&s.contains("onActivityStopped(android.app.Activity a) { }"),"pause stop retain nodes");
  check(s.contains("if (snapshot.equals(acceptedGlassConfig)) return;"),"same config never resets optics");
  check(s.contains("instanceof GlassBackdropView)group.getChildAt(i).invalidate()"),"predraw invalidates current frame");
  System.out.println("LiquidTransition567Test passed (source guards; device video required)");
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
