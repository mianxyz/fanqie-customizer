package io.github.mianxyz.fanqiecustomizer;
import java.lang.reflect.Modifier;
public final class Splash615Test {
 static void check(boolean b){if(!b)throw new AssertionError("splash entry boundary");}
 public static void main(String[] args){
  String h=SplashAdHooks.HELPER,s="io.reactivex.Single";String[] p={"java.lang.Boolean"};int pub=Modifier.PUBLIC|Modifier.FINAL;
  check(SplashAdHooks.isAdAcquisition(h,"a",s,p,pub));
  for(String n:new String[]{"g","getSplashAdView","hasHitAttribution","u7","xa","onRefresh"})check(!SplashAdHooks.isAdAcquisition(h,n,s,p,pub));
  check(!SplashAdHooks.isAdAcquisition("com.dragon.read.pages.splash.AttributionManager","a",s,p,pub));
  check(!SplashAdHooks.isAdAcquisition(h,"a","boolean",p,pub));
  for(String[] ps:new String[][]{{},{"boolean"},{"java.lang.Boolean","java.lang.Boolean"},{"java.lang.Object"}})check(!SplashAdHooks.isAdAcquisition(h,"a",s,ps,pub));
  for(int flags:new int[]{0,Modifier.PRIVATE,Modifier.PUBLIC|Modifier.STATIC,Modifier.PUBLIC|Modifier.NATIVE})check(!SplashAdHooks.isAdAcquisition(h,"a",s,p,flags));
  System.out.println("Splash615Test passed (outer entry only; attribution, callbacks, internal/native/static/overloads excluded)");
 }
}
