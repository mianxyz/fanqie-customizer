package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
public final class Performance600Test {
 static String source(String n)throws Exception{return Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer",n));}
 static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 public static void main(String[] args)throws Exception{
 String m=source("ModuleHook.java"),n=source("NativeUiHooks.java"),g=source("GlobalGlassSurfaces.java"),b=source("BookTools.java");
 int start=m.indexOf("private void installAll");String entry=m.substring(start,m.indexOf("private void installQuietly",start));
 String q=String.valueOf((char)34);
 check(entry.indexOf("if(!on("+q+"enabled"+q+"))")<entry.indexOf("installRest(app,ctx)"),"disabled exits before business registration");
 check(n.contains("if(host.on("+q+"hide_search_bar"+q+"))new DramaSearchVisibility"),"search-off has no search base hooks");
 check(!g.contains("View.class.getDeclaredMethod("+q+"draw"+q),"no global View.draw interception");
 String tick=g.substring(g.indexOf("private final Runnable tick="),g.indexOf("private void update(Activity"));
 check(!tick.contains("update(a)")&&!tick.contains("descendants("),"timer never discovers full tree");
 check(g.contains("s.dirty&&!ScrollWorkGate.busy(v)"),"only visible dirty idle surfaces refresh");
 check(b.contains("module.hook(m)")&&b.contains("unfolded.add(header)")&&b.contains("long readyAt=SystemClock.uptimeMillis();header.post(()->{"),"event-triggered immediate one-shot unfold");
 check(n.contains("hook("+q+"sd4.h"+q+","+q+"b"+q)&&n.contains("args[0]=filterCard(args[0],0)"),"list conversion filters copied card");
 String maps=source("HostSymbolData.java");check(maps.contains("c.put("+q+"sd4.h"+q+","+q+"ye4.h"+q+")")&&maps.contains("c.put("+q+"sd4.h"+q+","+q+"wf4.h"+q+")"),"inlined domestic converters mapped");
 System.out.println("Performance600Test passed (current-source structural checks; device checks separate)");
 }
}
