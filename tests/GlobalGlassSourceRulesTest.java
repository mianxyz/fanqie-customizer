package io.github.mianxyz.fanqiecustomizer;
public final class GlobalGlassSourceRulesTest {
 public static void main(String[] args){
  check(GlobalGlassSurfaces.SourceRules.before(0,0,0,1),"earlier same-Z sibling");
  check(!GlobalGlassSurfaces.SourceRules.before(0,0,2,1),"later same-Z excluded");
  check(GlobalGlassSurfaces.SourceRules.before(-1,0,3,1),"lower Z is behind regardless index");
  check(!GlobalGlassSurfaces.SourceRules.before(1,0,0,1),"higher Z excluded regardless index");
  check(!GlobalGlassSurfaces.SourceRules.before(Float.NaN,0,0,1),"unknown Z excluded");
  check(GlobalGlassSurfaces.SourceRules.visible(true,1,2000,1384),"visible backing");
  check(!GlobalGlassSurfaces.SourceRules.visible(false,1,2000,1384),"hidden retained tab excluded");
  check(!GlobalGlassSurfaces.SourceRules.visible(true,0,2000,1384),"collapsed transparent header excluded");
  check(!GlobalGlassSurfaces.SourceRules.visible(true,1,0,1384),"empty background excluded");
  check(GlobalGlassSurfaces.SourceRules.pure("bookstore","com.dragon.read.component.biz.impl.bookmall.widge.BookstoreHeaderBgView",""),"header class accepted independent of ViewGroup");
  check(GlobalGlassSurfaces.SourceRules.pure("mine","com.facebook.drawee.view.SimpleDraweeView","g5r"),"mine exact backing");
  check(!GlobalGlassSurfaces.SourceRules.pure("mine","com.facebook.drawee.view.SimpleDraweeView","ajy"),"book cover excluded");
  check(GlobalGlassSurfaces.SourceRules.pure("shelf","com.dragon.read.component.biz.impl.bookshelf.recommend.BsRecommendBgView","h3q"),"shelf FrameLayout background accepted");
  check(!GlobalGlassSurfaces.SourceRules.pure("bookstore","android.widget.FrameLayout","kn0"),"content container excluded");
  System.out.println("GlobalGlassSourceRulesTest passed (production source ordering, visibility, exact backing rules)");
 }
 static void check(boolean b,String s){if(!b)throw new AssertionError(s);}
}
