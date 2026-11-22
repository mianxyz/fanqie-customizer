package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.Files;
import java.nio.file.Path;

/** Source lifecycle guards only; not an Android runtime or transition-frame test. */
public final class GlobalGlassLifecycle567Test {
    public static void main(String[] args) throws Exception {
        String source=new String(HostBaselineEquivalenceTest.originalBytes("GlobalGlassSurfaces.java"), java.nio.charset.StandardCharsets.UTF_8);
        String pause=block(source,"public void onActivityPaused(");
        check(pause.contains("resumed.remove(a)")&&pause.contains("if(resumed.isEmpty())"),"pause tracks resumed activities");
        check(pause.contains("main.removeCallbacks(tick)")&&pause.contains("ticking=false"),"last pause stops scheduler");
        check(!pause.contains("restore")&&!pause.contains(".clear("),"pause preserves transition resources");
        String resume=block(source,"public void onActivityResumed(");
        check(resume.contains("resumed.put(a,Boolean.TRUE)")&&resume.contains("startTick()"),"resume restarts scheduler");
        String destroy=block(source,"public void onActivityDestroyed(");
        check(destroy.contains("restoreRoot(a.getWindow().getDecorView())"),"destroy targets its decor root");
        check(!destroy.contains("restoreRanks(null)")&&!destroy.contains("restoreTopBackdrops(true)"),"destroy avoids global restore");

        String top=block(source,"private void restoreTopBackdrops(");
        check(top.contains("view==null||force||!panels()||"),"explicit disable and forced cleanup bypass resumed guard");
        check(top.contains("isResumedRoot(view.getRootView())&&(!view.isAttachedToWindow()||!visibleStoreOwner(view))"),"secondary resumed root does not restore paused main backdrop");
        String active=block(source,"private boolean isResumedRoot(");
        check(active.contains("resumed.keySet()")&&active.contains("!a.isDestroyed()")&&active.contains("a.getWindow().getDecorView()==root"),"active ownership is exact decor identity");

        String cleanup=block(source,"private void restoreRoot(");
        check(cleanup.contains("if(root==null)return;"),"null root is not global cleanup");
        check(cleanup.contains("topBackdrops.keySet()")&&cleanup.contains("TopPageBackground bg=topBackdrops.remove(v)"),"destroy removes top backdrop ownership");
        check(cleanup.contains("v.getBackground()==bg")&&cleanup.contains("setPreservingPadding(v,bg.original)"),"destroy restores only still-owned backdrop");
        check(cleanup.contains("restoreRanks(root)"),"destroy restores its ranks");
        check(cleanup.contains("surfaces.keySet()")&&cleanup.contains("if(v!=null&&v.getRootView()==root)restore(v)"),"destroy restores only its surfaces");
        check(cleanup.contains("nativeTopLayers.keySet()")&&cleanup.contains("if(v!=null&&v.getRootView()==root){nativeTopLayers.remove(v);v.invalidate();}"),"destroy drops only its native top layers");
        check(count(cleanup,"v.getRootView()==root")==3,"all three map cleanup loops are root-scoped");
        check(!cleanup.contains(".clear()")&&!cleanup.contains("restoreRanks(null)"),"destroy preserves other roots");
        String ranks=block(source,"private void restoreRanks(");
        check(ranks.contains("root==null||v.getRootView()==root")&&ranks.contains("NativeCoverGlass.restore(v)")&&ranks.contains("rankPanels.remove(v)"),"rank cleanup honors selected root and explicit all-root disable");

        String tick=block(source,"private final Runnable tick=");
        check(tick.contains("if(resumed.isEmpty()){ticking=false;return;}"),"empty tick stops without restore");
        check(tick.contains("restoreTopBackdrops(false)"),"tick uses guarded backdrop cleanup");
        check(tick.contains("if(!panels()){restoreRanks(null);for(View v:new ArrayList<>(surfaces.keySet()))restore(v);}"),"disabled panels still restore all ranks and surfaces");
        check(!tick.contains("restoreRoot(")&&!tick.contains("restoreTopBackdrops(true)"),"secondary activity tick never force-restores another root");
        check(tick.contains("if(!v.isAttachedToWindow())restore(v)"),"actual detached surface still releases resources");
        check(source.contains("onViewDetachedFromWindow(View v){restore(v);}"),"detach listener release is retained");
        System.out.println("GlobalGlassLifecycle567Test passed (source lifecycle boundaries only)");
    }

    private static String block(String source,String signature) {
        int start=source.indexOf(signature);
        check(start>=0,"missing source boundary: "+signature);
        int open=source.indexOf('{',start),depth=0;
        boolean quoted=false,escaped=false;char quote=0;
        for(int i=open;i<source.length();i++) {
            char c=source.charAt(i);
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c==quote)quoted=false;continue;}
            if(c=='"'||c=='\''){quoted=true;quote=c;continue;}
            if(c=='{')depth++;else if(c=='}'&&--depth==0)return source.substring(start,i+1);
        }
        throw new AssertionError("unclosed source boundary: "+signature);
    }
    private static int count(String source,String part){int n=0,at=0;while((at=source.indexOf(part,at))>=0){n++;at+=part.length();}return n;}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
