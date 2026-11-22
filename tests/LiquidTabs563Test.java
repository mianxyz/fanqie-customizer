package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.Files;
import java.nio.file.Path;

/** Production spring numerical tests plus viewport/display-list invariants. */
public final class LiquidTabs563Test {
    public static void main(String[] args) throws Exception {
        LiquidGlassRenderer.Spring x=new LiquidGlassRenderer.Spring(1f);
        x.target=1.16f;float peak=1;
        for(int i=0;i<120;i++){x.step(1f/120f,250f,.6f);peak=Math.max(peak,x.value);check(Float.isFinite(x.value),"finite press");}
        check(peak>1.16f&&peak<1.20f,"press spring restrained overshoot");
        check(x.value>1.15f,"press reaches enlarged state");
        x.target=1f;float minimum=2;
        for(int i=0;i<240;i++){x.step(1f/120f,250f,.6f);minimum=Math.min(minimum,x.value);}
        check(minimum<1f&&minimum>.97f,"release low damping overshoot");
        check(x.settled(),"release converges");
        LiquidGlassRenderer.Spring a=new LiquidGlassRenderer.Spring(0),b=new LiquidGlassRenderer.Spring(0);
        a.target=b.target=400;
        for(int i=0;i<60;i++)a.step(1f/60f,300f,.5f);
        for(int i=0;i<120;i++)b.step(1f/120f,300f,.5f);
        check(Math.abs(a.value-b.value)<.01f,"frame-rate independent position");
        a.target=-100;a.velocity=1200;
        for(int i=0;i<240;i++)a.step(1f/120f,300f,.5f);
        check(Math.abs(a.value+100)<.01f,"interrupted reverse converges with velocity");
        float previous=a.value;a.step(Float.NaN,300,.5f);a.step(-1,300,.5f);
        check(a.value==previous,"invalid elapsed time ignored");
        String src=new String(HostBaselineEquivalenceTest.originalBytes("GlassSurfaces.java"), java.nio.charset.StandardCharsets.UTF_8);
        check(!src.contains("activeBlurSource"),"no competing fragment source");
        check(src.contains("-dx - src.getScrollX(), -dy - src.getScrollY()"),"pager viewport scroll compensated");
        check(src.contains("View src = mainContentViewport(bar)"),"per-frame main content selection");
        check(src.contains("exact(root,\"dsv\",\"android.widget.FrameLayout\")"),"runtime main Fragment container identity");
        check(src.contains("if(ancestor==content)return null"),"source never contains bottom bar");
        check(src.contains("if (!recorded) sourceNode.discardDisplayList()"),"failed backdrop discarded");
        check(src.contains("!backdrop.sampleReady"),"pill only consumes fresh sample");
        check(src.contains("rc.drawRenderNode(backdrop.sourceNode)"),"pill samples raw shared viewport once");
        check(src.contains("state.velocityTracker.computeCurrentVelocity"),"live motion velocity");
        check(src.contains("+400L"),"click target lock");
        check(!src.contains("PixelCopy"),"no screenshot pipeline");
        System.out.println("LiquidTabs563Test passed (spring peak="+peak+", release min="+minimum+")");
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}