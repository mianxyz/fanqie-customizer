package io.github.mianxyz.fanqiecustomizer;
public final class BarInteractionRulesTest {
 public static void main(String[] args){
  if(BarInteractionRules.suspend(true,true,1,true,false))throw new AssertionError("normal");
  if(!BarInteractionRules.suspend(true,true,1,true,true))throw new AssertionError("edit child remains visible");
  if(!BarInteractionRules.suspend(false,true,1,true,false)||!BarInteractionRules.suspend(true,false,1,true,false)||!BarInteractionRules.suspend(true,true,.05f,true,false)||!BarInteractionRules.suspend(true,true,1,false,false))throw new AssertionError("host hidden/disabled/transparent/offscreen");
  if(BarInteractionRules.suspend(true,true,.051f,true,false))throw new AssertionError("alpha threshold");
  System.out.println("BarInteractionRulesTest passed");
 }
}
