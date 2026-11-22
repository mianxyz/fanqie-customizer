package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
public final class BarPress600Test {
 public static void main(String[] args)throws Exception {
  int[] inputs={0,50,100,-1,999};float[] targets={1.015f,1.028f,1.042f,1.015f,1.042f};
  for(int i=0;i<inputs.length;i++){
   int bounce=inputs[i];near(LiquidGlassRenderer.Rules.barPressScale(bounce),targets[i]);
   float d=LiquidGlassRenderer.Rules.barDamping(bounce);ok(d>.30f&&d<.92f);
   LiquidGlassRenderer.Spring spring=new LiquidGlassRenderer.Spring(1f);spring.target=targets[i];
   for(int n=0;n<600;n++){LiquidGlassRenderer.Rules.stepBarScale(spring,1f/120f,bounce);bounded(spring);}
   near(spring.value,targets[i]);spring.target=1f;
   for(int n=0;n<1200&&!spring.settled();n++){LiquidGlassRenderer.Rules.stepBarScale(spring,1f/120f,bounce);bounded(spring);}
   ok(spring.settled());near(spring.value,1f);near(spring.velocity,0f);
   spring.value=1.04f;spring.velocity=4f;LiquidGlassRenderer.Rules.resetBarScale(spring);near(spring.value,1);near(spring.target,1);near(spring.velocity,0);
  }
  for(float value:new float[]{Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-999f,999f}){float scale=LiquidGlassRenderer.Rules.barScale(value);ok(Float.isFinite(scale)&&scale>=.97f&&scale<=1.06f);}
  String s=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/GlassSurfaces.java"));
  ok(s.contains("resetLiquidPress(bar,state,\"paused\")"));ok(s.contains("else resetLiquidPress(bar,pills.get(bar),\"focus-lost\")"));
  ok(s.contains("if(action==3){resetLiquidPress(bar,state,\"cancel\");return;}"));ok(s.contains("if(action==3)resetLiquidPress(v,state,\"cancel\")"));
  ok(s.contains("bar.removeCallbacks(state.frameCallback)"));ok(s.contains("bar.setScaleX(1f);bar.setScaleY(1f)"));
  ok(s.contains("bar.setPivotX(bar.getWidth()*.5f);bar.setPivotY(bar.getHeight()*.5f)"));
  int a=s.indexOf("    private void queueLiquidFrame"),b=s.indexOf("    private final java.util.Map<ViewGroup,boolean[]>",a);String frame=s.substring(a,b);
  ok(!frame.contains("requestLayout")&&!frame.contains("getChildAt")&&!frame.contains("updateLiquidClipping"));
  ok(frame.contains("!state.barScale.settled()"));ok(s.contains("if(liquidStyle())liquidTouch(v,state,e)"));
  System.out.println("BarPress600Test passed (5 bounce targets/damping/convergence; finite clamp; pause/cancel/focus reset and removable frame guards)");
 }
 static void bounded(LiquidGlassRenderer.Spring s){ok(Float.isFinite(s.value)&&Float.isFinite(s.velocity)&&s.value>=.97f&&s.value<=1.06f);}
 static void near(float a,float b){ok(Math.abs(a-b)<.00001f);}static void ok(boolean value){if(!value)throw new AssertionError();}
}
