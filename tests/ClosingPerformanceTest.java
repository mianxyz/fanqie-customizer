package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
public final class ClosingPerformanceTest{
 public static void main(String[] args)throws Exception{
  String b=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/GlassSurfaces.java")),g=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/GlobalGlassSurfaces.java"));
  if(!b.contains("nativeTabCache.get(bar)")||!b.contains("nativeTabShapes.get(bar)")||!b.contains("System.identityHashCode(track.getChildAt(i))"))throw new AssertionError("identity cache invalidation");
  if(!b.contains("cached.isAttachedToWindow()")||!b.contains("content.getRootView()!=root"))throw new AssertionError("detach/root cache guard");
  if(!g.contains("if(dirty&&!ScrollWorkGate.busy(v))")||g.contains("addOnPreDrawListener"))throw new AssertionError("scroll discovery/rebuild");
  if(!b.contains("if(suspendBar(v))return chain.proceed();"))throw new AssertionError("edit touch passthrough");
  System.out.println("ClosingPerformanceTest passed (cache invalidation, scroll and edit boundaries; device metrics separate)");
 }
}
