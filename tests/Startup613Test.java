package io.github.mianxyz.fanqiecustomizer;
import java.util.Random;
public final class Startup613Test {
 public static void main(String[] args){Random random=new Random(613);for(int trial=0;trial<1000;trial++){int w=1+random.nextInt(160),h=1+random.nextInt(240);int[] pixels=new int[w*h];for(int i=0;i<pixels.length;i++)pixels[i]=random.nextInt();if(trial%3==0)java.util.Arrays.fill(pixels,trial%2==0?0xffffffff:0);long a=0,r=0,g=0,b=0,n=0;boolean placeholder=true;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=pixels[y*w+x];if((p>>>24)>96&&(((p>>>16)&255)<248||((p>>>8)&255)<248||(p&255)<248))placeholder=false;if(y>=(int)(h*.65f)){a+=p>>>24;r+=(p>>>16)&255;g+=(p>>>8)&255;b+=p&255;n++;}}int avg=n==0?0:((int)(a/n)<<24)|((int)(r/n)<<16)|((int)(g/n)<<8)|(int)(b/n);if(avg!=CardGlassHooks.bottomAveragePixels(pixels,w,h)||placeholder!=CardGlassHooks.placeholderPixels(pixels))throw new AssertionError("pixel equivalence "+trial);}
 System.out.println("Startup613Test passed (1000 randomized bottom-average and placeholder equivalence cases)");}
}
