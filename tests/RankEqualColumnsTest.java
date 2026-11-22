package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
public final class RankEqualColumnsTest {
 public static void main(String[] args)throws Exception{
  Path src=Path.of("src/io/github/mianxyz/fanqiecustomizer");
  String rank=Files.readString(src.resolve("RankLayoutFix.java")),glass=Files.readString(src.resolve("NativeCoverGlass.java")),global=Files.readString(src.resolve("GlobalGlassSurfaces.java"));
  check(!Rules.defaultOn("rank_equal_columns")&&!Rules.isIntKey("rank_equal_columns")&&PrivacyRules.settingKey("rank_equal_columns"),"independent opt-in boolean");
  check(!glass.contains("RankLayoutFix"),"frost renderer has zero rank geometry calls");
  check(global.contains("host.on(\"enabled\")&&host.on(\"rank_equal_columns\")"),"layout independent of global glass master/cover");
  check(global.contains("if(rankEqual()){rankLayouts.add(panel);RankLayoutFix.update(panel,true);}"),"disabled never installs layout state");
  check(global.contains("if(!rankEqual())restoreRankLayouts(null);")&&global.contains("restoreRankLayouts(root);"),"live off and destruction restore");
  check(rank.indexOf("if(states.containsKey(panel))return;")<rank.indexOf("State state=new State(panel)"),"repeat update constant-time guard");
  check(!rank.contains("OnPreDrawListener")&&!rank.contains("requestLayout("),"no predraw geometry or unconditional layout request");
  check(rank.contains("View.OnLayoutChangeListener")&&rank.contains("OnChildAttachStateChangeListener"),"targeted size and new child events");
  check(rank.contains("if(lp.width!=width){lp.width=width;v.setLayoutParams(lp);}"),"width writes only on mismatch");
  check(rank.contains("if(known)return;")&&rank.contains("if(changed){for"),"unchanged columns skip child tree and geometry traversal");
  check(rank.contains("removeCallbacks(this)")&&rank.contains("removeOnLayoutChangeListener(this)")&&rank.contains("removeOnChildAttachStateChangeListener"),"listener and queued work cleanup");
  check(rank.contains("HostSymbols.publicMethodName(adapter.getClass(),\"getItemCount\")"),"inherited six-profile adapter mapping retained");
  System.out.println("RankEqualColumnsTest passed (opt-in, decoupling, event boundaries, O(1) repeat, guarded writes, cleanup and mapped count; runtime tested separately)");
 }
 static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
