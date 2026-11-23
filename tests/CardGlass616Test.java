package io.github.mianxyz.fanqiecustomizer;
import java.nio.file.*;
public final class CardGlass616Test {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  var r=new CardGlassHooks.CaptureRejection();Object b=new Object(),d=new Object(),next=new Object();
  check(!r.skip(b,1,d,0),"initial sample");r.reject(b,1,d,1000);
  for(int i=0;i<181;i++)check(r.skip(b,1,d,1000+i*4),"same bitmap generation rejected");
  check(r.skip(b,1,next,9000),"wrapper change does not change bitmap pixels");
  check(!r.skip(b,2,d,1001),"generation immediately retries");
  check(!r.skip(next,1,d,1001),"new bitmap immediately retries");
  check(!r.skip(null,0,d,1001),"bitmap to drawable retries");
  r.reject(null,0,d,2000);
  check(r.skip(null,0,d,2000)&&r.skip(null,0,d,2179),"mutable wrapper cooling");
  check(!r.skip(null,0,d,2180)&&!r.skip(null,0,d,2181),"180ms expiry retries");
  check(!r.skip(null,0,next,2001),"new drawable immediately retries");
  check(!r.skip(b,1,d,2001),"bitmap arriving inside cooldown retries");
  check(!r.skip(null,0,null,2001)&&!r.skip(null,0,d,1999),"missing drawable and clock rollback retry");
  r.reject(null,0,d,2200);check(r.skip(null,0,d,2379),"renewed cooldown");
  r.clear();check(!r.skip(null,0,d,2201),"successful capture clears rejection");
  var other=new CardGlassHooks.CaptureRejection();check(!other.skip(b,1,d,2201),"cards isolated");
  String s=Files.readString(Path.of("src/io/github/mianxyz/fanqiecustomizer/CardGlassHooks.java"));
  check(s.indexOf("card.rejection.skip(actual,generation,drawable,now)")<s.indexOf("extracting.set(true);"),"gate before allocation and scan");
  int reject=s.indexOf("if(placeholderPixels(pixels))");int end=s.indexOf("return;}",reject);
  String branch=s.substring(reject,end);check(branch.contains("card.rejection.reject(actual,generation,drawable,now)")&&branch.contains("source.recycle()")&&!branch.contains("card.source=")&&!branch.contains("sourceCache.put"),"placeholder retains existing source and cache");
  check(s.indexOf("card.rejection.clear();",end)<s.indexOf("sourceCache.put(key,new CachedSource(source,average,name))"),"acceptance clears only after placeholder check");
  System.out.println("CardGlass616Test passed (identity/generation, 180ms boundary, arrival, per-card and source/cache protection)");
 }
}
