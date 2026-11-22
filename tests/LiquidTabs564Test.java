package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.Files;
import java.nio.file.Path;

/** Continuous material, compatibility, immediate press and release-commit contracts. */
public final class LiquidTabs564Test {
    public static void main(String[] args) throws Exception {
        close(LiquidGlassRenderer.Rules.activity(Float.NaN), 0f, "NaN activity");
        close(LiquidGlassRenderer.Rules.activity(Float.POSITIVE_INFINITY), 0f, "infinite activity");
        close(LiquidGlassRenderer.Rules.activity(-1), 0f, "activity lower bound");
        close(LiquidGlassRenderer.Rules.activity(2), 1f, "activity upper bound");
        close(LiquidGlassRenderer.Rules.direction(Float.NaN), 0f, "NaN direction");
        close(LiquidGlassRenderer.Rules.direction(-2), -1f, "left direction bound");
        close(LiquidGlassRenderer.Rules.direction(2), 1f, "right direction bound");
        var rest=LiquidGlassRenderer.Rules.params(200,56,28,1,0,55,false,true,0f,0f);
        var mid=LiquidGlassRenderer.Rules.params(200,56,28,1,0,55,false,true,.5f,.5f);
        var active=LiquidGlassRenderer.Rules.params(200,56,28,1,0,55,false,true,1f,1f);
        check(!rest.equals(mid)&&!mid.equals(active), "continuous material invalidates effect cache");
        close(mid.activity(),.5f,"fractional material retained");
        close(mid.direction(),.5f,"fractional direction retained");
        var legacy=LiquidGlassRenderer.Rules.params(200,56,28,1,0,55,false,false,false);
        check(!legacy.pill()&&!legacy.dragging(),"legacy panel mode preserved");
        LiquidGlassRenderer.Spring activity=new LiquidGlassRenderer.Spring(0f);
        activity.target=1f; activity.step(1f/60f,300f,.85f);
        check(activity.value>0f&&activity.value<1f,"press transitions continuously on first frame");
        for(int i=0;i<120;i++)activity.step(1f/120f,300f,.85f);
        check(activity.value>.99f,"long hold reaches active lens");
        activity.target=0f;
        for(int i=0;i<240;i++)activity.step(1f/120f,300f,.85f);
        check(activity.settled(),"release returns to quiet pill");
        String src=new String(HostBaselineEquivalenceTest.originalBytes("GlassSurfaces.java"), java.nio.charset.StandardCharsets.UTF_8);
        String renderer=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/LiquidGlassRenderer.java"));
        int down=src.indexOf("if(action==0){state.downX");
        int move=src.indexOf("if(action==2){float dx",down);
        check(src.substring(down,move).contains("if(liquidStyle())animatePill(v,slotAt(v,e.getX()),false)"),"DOWN targets slot before MOVE");
        String release=src.substring(src.indexOf("if(liquidStyle()&&(action==1||action==3))"),move);
        check(release.contains("slotAt(v,e.getX())")&&release.contains("clickTab(v,to)"),"short and long press commit final slot");
        check(release.contains("cancel.setAction(3)")&&release.contains("if(!wasDrag)"),"host child cancelled once before explicit click");
        check(!release.contains("getEventTime")&&!release.contains("getDownTime"),"long hold not gated by tap timeout");
        check(release.contains("+400L"),"pending target lock retained");
        check(!src.substring(down,move).contains("if(enabled&&liquidStyle())"),"press target independent of swipe setting");
        check(src.contains("(!liquidStyle()&&!glassFlag(\"glass_bottom_bar_swipe\"))"),"liquid pill visible with swipe disabled");
        check(src.contains("state.sx.target=state.sy.target=1.16f"),"1.16 press scale retained");
        check(src.contains("dark,true,state.activity.value,state.direction.value"),"continuous material reaches renderer");
        check(src.contains("View src = mainContentViewport(bar)")&&src.contains("-dx - src.getScrollX(), -dy - src.getScrollY()"),"original viewport transform retained");
        check(src.contains("!backdrop.sampleReady")&&src.contains("rc.drawRenderNode(backdrop.sourceNode)"),"fresh raw source retained");
        String agsl=LiquidGlassRenderer.SOURCE;
        check(agsl.contains("mix(fogColor, liquidColor, activity)")&&agsl.contains("fogRgb * fogAlpha"),"quiet pill is premultiplied fixed fog fading into sampled lens");
        check(agsl.contains("mix(1.0, activity, pill)"),"panel material stays fully liquid independently of activity");
        check(agsl.contains("* materialActivity")&&agsl.contains("direction * pill * activity"),"depth and directional highlight follow activity");
        check(renderer.contains("boolean pill, boolean dragging)")&&renderer.contains("boolean pill, float activity, float direction)"),"old API and continuous overload coexist");
        System.out.println("LiquidTabs564Test passed");
    }
    static void close(float actual,float expected,String message){check(Float.isFinite(actual)&&Math.abs(actual-expected)<.0001f,message);}
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
