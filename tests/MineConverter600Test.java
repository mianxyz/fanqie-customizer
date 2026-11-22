package io.github.mianxyz.fanqiecustomizer;
public final class MineConverter600Test {
 public static void main(String[] args)throws Exception{
  for(long code:new long[]{74132,74332,74532}){
   HostAdaptation570Test.fresh();HostSymbols.initialize("com.dragon.read",code,MineConverter600Test.class.getClassLoader(),s->{});
   String owner=code==74132?"ud4.h":code==74332?"ye4.h":"wf4.h";
   String ctx=code==74132?"qy6.c":code==74332?"s17.c":"i57.c";
   if(!HostSymbols.name("sd4.h").equals(owner)||!HostSymbols.canonical(ctx).equals("zx6.c"))throw new AssertionError("converter type normalization "+code);
   if(!HostSymbols.methodName("sd4.h","b").equals(code==74132?"b":"a"))throw new AssertionError("converter alias "+code);
  }
  HostSymbols.resetForTest();System.out.println("MineConverter600Test passed (three runtime profile type and method normalization paths)");
 }
}
