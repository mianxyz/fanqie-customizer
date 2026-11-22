package io.github.mianxyz.fanqiecustomizer;
public final class BookmallBusinessRulesTest {
 public static void main(String[] args){
  if(!BookmallBusinessRules.collapseChannels(1))throw new AssertionError("one channel collapses");
  if(BookmallBusinessRules.collapseChannels(2))throw new AssertionError("two channels remain switchable");
  if(BookmallBusinessRules.collapseChannels(0))throw new AssertionError("unbound empty tabs are not collapsed");
  if(BookmallBusinessRules.collapseChannels(11))throw new AssertionError("full tabs stay visible");
  if(!BookmallBusinessConfig.manages("refresh_confirm")||!BookmallBusinessConfig.manages("hide_search_bar")||!BookmallBusinessConfig.manages("channel_novel")||BookmallBusinessConfig.manages("glass_top_bar"))throw new AssertionError("business configuration scope");
  System.out.println("BookmallBusinessRulesTest passed (channel collapse and business config scope)");
 }
}