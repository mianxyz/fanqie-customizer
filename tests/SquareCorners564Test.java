package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source-level boundary regression; GPU coverage and parent outlines require device evidence. */
public final class SquareCorners564Test {
 public static void main(String[] args)throws Exception {
  String src=new String(HostBaselineEquivalenceTest.originalBytes("SquareGlassHooks.java"), java.nio.charset.StandardCharsets.UTF_8);
  String owner=src.substring(src.indexOf("hook(\"owner\""),src.indexOf("hook(\"background\""));
  String paint=src.substring(src.indexOf(" void paint("),src.indexOf(" // One antialiased boundary"));
  String mask=src.substring(src.indexOf(" void maskCorners("),src.indexOf(" // Read-only diagnostics"));
  check(!src.contains(".clipPath("),"no duplicate hard path intersections");
  check(owner.contains("t.images.size()==3&&enabled()&&canvas.isHardwareAccelerated()"),"mask only for ready enabled hardware tile");
  check(owner.contains("canvas.saveLayer(0,0,v.getWidth(),v.getHeight(),null):canvas.save()"),"OFF/readiness fallback uses original canvas");
  check(owner.contains("finally{canvas.restoreToCount(saved)")&&owner.contains("t.cornersActive=false"),"balanced layer and state on exceptions");
  check(src.contains("if(t.images.size()!=3)return c.proceed();if(!t.cornersActive)return c.proceed()"),"newly collected sources wait for rounded frame");
  check(mask.contains("Path.FillType.EVEN_ODD")&&mask.contains("BlendMode.CLEAR")&&src.contains("new Paint(Paint.ANTI_ALIAS_FLAG)"),"antialiased inverse cutout");
  check(mask.contains("float radius=8*density")&&mask.contains("new RectF(0,0,w,h)"),"same symmetric 8dp outer geometry");
  check(mask.contains("t.maskDensity!=density")&&mask.contains("t.maskW!=w")&&mask.contains("t.maskH!=h"),"mask rebuilt after density/geometry changes");
  check(!owner.contains("setRenderEffect")&&!mask.contains("setRenderEffect"),"foreground never blurred");
  check(paint.contains("5.5f*density,5.5f*density")&&paint.contains("w*.25f")&&paint.contains("h*.25f"),"three-image 22dp atmosphere retained");
  check(paint.contains("setStrokeWidth(.75f*density)")&&paint.contains(".375f*density"),"existing inset rim retained");
  for(String color:new String[]{"0xff252830","0xfff2eee6","0xB020222A","0x82FFFFFF","0xB0252830","0xB2FFFFFF"})check(paint.contains(color),"palette retained "+color);
  check(src.contains("bg.getOutline(outline)")&&src.contains("v.getClipToOutline()")&&src.contains("activeOutline=unobserved"),"read-only diagnostic distinguishes drawable and actual outline");
  for(String mutation:new String[]{"setClipToOutline(","setOutlineProvider(","setBackground(","setLayerType("})check(!src.contains(mutation),"host properties unchanged "+mutation);
  System.out.println("SquareCorners564Test passed (source invariants; device visual pending)");
 }
 static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
