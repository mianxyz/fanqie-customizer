package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
public final class VisualReuse600Test {
 static void ok(boolean b,String s){if(!b)throw new AssertionError(s);}
 public static void main(String[] args)throws Exception{
 String g=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/GlobalGlassSurfaces.java"));
 String b=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/BookmallBusinessViews.java"));
 ok(g.contains("if(!s.rankPanel)restore(v)")&&g.contains("if(!rankPanel)restore(v)"),"rank detach and tick retain cache");
 ok(g.contains("cached.rankPanel&&cached.alive&&panels()&&!rankCover()")&&g.contains("cached.original=requested;return chain.proceed(new Object[]{cached})"),"exact registered background guard");
 String attach=g.substring(g.indexOf("public void onViewAttachedToWindow(View v){if(rankPanel)"),g.indexOf("public boolean getPadding(Rect out)"));
 ok(attach.contains("ensureRankBackground(v)")&&!attach.contains("postDelayed")&&!attach.contains("queueDiscovery")&&!attach.contains("releaseNode"),"synchronous cached attach");
 ok(g.contains("surfaces.get(v).root.get()==root"),"detached owner destroyed with Activity");
 ok(g.contains("if(!entry.getValue().rankPanel)entry.getValue().dirty=true"),"scroll does not dirty rank source");
 ok(g.contains("rankPanel&&node!=null&&node.hasDisplayList()"),"busy reuses display list");
 ok(b.contains("v.getOverlay().add(fade)")&&b.contains("v.getOverlay().remove(fade)"),"decoration-only reversible overlay");
 ok(b.contains("GlobalGlassSurfaces.storeBackdropColor")&&b.contains("0f,.333333f,.666667f,1f")&&b.contains("0xBF000000,next}"),"smooth terminal page-color gradient");
 ok(Rules.allSettingKeys().length==90,"90 keys frozen");
 System.out.println("VisualReuse600Test passed (cache lifecycle/background guard/top edge structure)");
 }
}
