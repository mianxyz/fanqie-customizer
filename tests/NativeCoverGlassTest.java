package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.*;
/** Fixed-source production rules; device rendering is verified by the integrating agent. */
public final class NativeCoverGlassTest {
    public static void main(String[] args)throws Exception{
        check(NativeCoverGlass.textureSize(2000)==500,"quarter resolution");
        check(NativeCoverGlass.textureSize(0)==1,"zero-size guard");
        check(NativeCoverGlass.BLUR_DP==22f,"fixed 22dp blur");
        for(float density:new float[]{1f,1.5f,2.625f,4f})check(NativeCoverGlass.blurSigma(density)/NativeCoverGlass.SCALE==22f*density,"22dp blur survives texture scaling");
        check(NativeCoverGlass.fixedSource(false)==0xffe8edf2,"fixed day source");
        check(NativeCoverGlass.fixedSource(true)==0xff20232a,"fixed night source");
        check((NativeCoverGlass.fixedSource(false)>>>24)==255&&(NativeCoverGlass.fixedSource(true)>>>24)==255,"opaque base isolates host content");
        check(NativeCoverGlass.mistTop(false)==0x48ffffff&&NativeCoverGlass.mistBottom(false)==0x58f6f8fa,"uniform light mist endpoints");
        check(NativeCoverGlass.mistTop(true)==0x482c3038&&NativeCoverGlass.mistBottom(true)==0x58242830,"uniform dark mist endpoints");
        String s=new String(HostBaselineEquivalenceTest.originalBytes("NativeCoverGlass.java"), java.nio.charset.StandardCharsets.UTF_8);
        // Inspect the actual recording block, not just the presence of a fixed-source helper.
        int start=s.indexOf("RecordingCanvas recording=node.beginRecording(rw,rh);");
        int end=s.indexOf("finally{node.endRecording();}",start);
        check(start>=0&&end>start,"explicit render recording lifecycle");
        String recording=s.substring(start,end);
        check(recording.contains("try{recording.drawColor(fixedSource(night));}"),"real RenderNode input is the fixed theme source");
        check(!recording.contains("drawBitmap")&&!recording.contains("drawPureSource")&&!recording.contains(".draw("),"recording never samples content");
        check(!s.contains("Bitmap")&&!s.contains("snapshot(")&&!s.contains("collectBitmaps")&&!s.contains("coverRegion")&&!s.contains("getDrawable()")&&!s.contains("getLocationOnScreen"),"entire cover sampling and stitching chain removed");
        check(s.contains("else canvas.drawColor(fixedSource(night));"),"software fallback has same fixed source");
        check(s.contains("float sigma=blurSigma(density)")&&s.contains("RenderEffect.createBlurEffect(sigma,sigma,Shader.TileMode.CLAMP)"),"fixed 22dp CLAMP render effect");
        check(s.contains("mistTop(night),mistBottom(night),Shader.TileMode.CLAMP")&&s.contains("new LinearGradient(0,0,0,area.height()"),"full-height uniform mist gradient");
        check(s.contains("node.hasDisplayList()")&&s.contains("renderDensity!=density")&&s.contains("rw!=width||rh!=height"),"render cache refreshes on size and density");
        check(s.contains("if(night!=dark){night=dark;discard();}"),"theme change discards stale source");
        check(s.contains(".75f*density")&&s.contains("RADIUS_PX=21f")&&s.contains("canvas.clipPath(clip)"),"0.75dp rim and native rounded clip");
        check(s.contains("hlf")&&s.contains("AnimatorContainerLayout")&&s.contains("androidx.constraintlayout.widget.ConstraintLayout"),"exact rank framework shape");
        check(!s.contains("PixelCopy")&&!s.contains("view.draw(")&&!s.contains("image.draw("),"no window or View capture");
        check(s.contains("Build.VERSION.SDK_INT<31")&&s.contains("RankLayoutFix.update(candidate,true)")&&s.contains("RankLayoutFix.restore(candidate)"),"platform and frozen layout integration retained");
        check(s.contains("if(!enabled){restore(candidate);return false;}")&&s.contains("preservePadding(candidate,glass.original)"),"OFF restores original background");
        check(s.contains("preservePadding(view,null)")&&s.contains("parent.addView(layer,0,")&&s.contains("glass.ensureBackground()")&&s.contains("view.isPaddingRelative()")&&s.contains("if(l!=view.getPaddingLeft()"),"attach and restore preserve padding");
        check(s.contains("original=replacement")&&s.contains("background-replaced")&&s.contains("glass.safeUpdate(night,")&&s.contains("update-error entry="),"replacement and error recovery retained");
        check(s.contains("removeOnAttachStateChangeListener(glass)")&&s.contains("removeOnPreDrawListener(glass)")&&s.contains("removeView(glass.layer)")&&s.contains("onViewDetachedFromWindow(View v){restore(v);}"),"listeners and underlay removed on restore and detach");
        check(s.contains("neutral(bg)||edgeFade(v,bg)")&&s.contains("neutral(fg)||edgeFade(v,fg)")&&s.contains("restoreLayers();")&&s.contains("getBackground()==null")&&s.contains("getForeground()==null"),"saved edge backgrounds and foregrounds restore without replacing host updates");
        check(s.contains("fading.put(v,v.isHorizontalFadingEdgeEnabled())")&&s.contains("setHorizontalFadingEdgeEnabled(e.getValue())"),"horizontal fade state restored");
        check(NativeCoverGlass.whiteFade(new int[]{0xffffffff,0xffffffff,0x00ffffff}),"white transparent edge recognized");
        check(!NativeCoverGlass.whiteFade(new int[]{0xffffffff,0xffffffff}),"solid white is not an edge gradient");
        check(!NativeCoverGlass.whiteFade(new int[]{0xffff2222,0x00ff2222}),"colored edge retained");
        check(!NativeCoverGlass.whiteFade(null)&&!NativeCoverGlass.whiteFade(new int[0]),"empty edge guard");
        check(NativeCoverGlass.rankEdge("fkq")&&NativeCoverGlass.rankEdge("hd_")&&!NativeCoverGlass.rankEdge("fkq_extra"),"exact rank edge IDs only");
        check(s.contains("bucket.size()>=96")&&s.contains("layerDiagnostics"),"bounded diagnostics");
        System.out.println("NativeCoverGlassTest passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
