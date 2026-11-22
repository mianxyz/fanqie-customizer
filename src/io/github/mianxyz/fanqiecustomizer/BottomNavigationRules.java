package io.github.mianxyz.fanqiecustomizer;

import java.util.*;

/** Preserve native tab identities and indices; calculate only a visibility mask. */
final class BottomNavigationRules {
    static final String BAR="com.dragon.read.widget.BottomTabBarLayout";
    private BottomNavigationRules() {}
    static boolean visibleSlot(boolean main,boolean overlay,int visibility,int width,boolean hidden,boolean measured){return main&&!overlay&&visibility==0&&(!measured||width>0)&&!hidden;}
    static int nearest(float x,int[] indices,float[] centers){int best=-1;float distance=Float.MAX_VALUE;for(int n=0;n<indices.length;n++){float d=Math.abs(x-centers[n]);if(Float.isFinite(d)&&d<distance){distance=d;best=indices[n];}}return best;}
    static int slotEdge(int width,int ordinal,int count){return count<=0?0:(int)((long)Math.max(0,width)*ordinal/count);}
    static boolean[] hidden(boolean enabled,List<String> types,Set<String> keys){
        if(types==null)return new boolean[0];
        boolean[] hidden=new boolean[types.size()];boolean visible=false;
        for(int i=0;i<hidden.length;i++){
            String key=Rules.tabKey(types.get(i));
            hidden[i]=enabled&&key!=null&&keys!=null&&keys.contains(key);
            visible|=!hidden[i];
        }
        if(!visible&&hidden.length>0)hidden[0]=false;
        return hidden;
    }
    static boolean animation(boolean enabled,Set<String> keys,boolean original){
        if(enabled&&keys!=null)for(String key:Rules.TAB_KEYS)if(keys.contains(key))return false;
        return original;
    }
}
