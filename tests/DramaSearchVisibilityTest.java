package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.Files;
import java.nio.file.Path;

/** Structural regression guards; the Android first-frame/restore checks remain device acceptance. */
public final class DramaSearchVisibilityTest {
    public static void main(String[] args) throws Exception {
        Path src=Path.of("src/io/github/mianxyz/fanqiecustomizer");
        String code=new String(HostBaselineEquivalenceTest.originalBytes("DramaSearchVisibility.java"), java.nio.charset.StandardCharsets.UTF_8);
        String install=new String(HostBaselineEquivalenceTest.originalBytes("NativeUiHooks.java"), java.nio.charset.StandardCharsets.UTF_8);
        check(install.contains("new DramaSearchVisibility(module,host).install();"),"search hooks installed even when initial toggle is off");
        check(code.contains("host.on(\"enabled\")&&host.on(\"hide_search_bar\")"),"both live flags gate hiding");
        check(code.contains("\"gat\".equals(id(view))")&&code.contains("\"ixs\".equals(id(parent))")&&code.contains("androidx.appcompat.widget.StretchLeftLayout"),"short-drama identity must retain ID, class and ancestor guards");
        check(code.contains("COMMON.equals(type.getName())"),"CommonSearchBar subclasses remain supported");
        check(code.contains("hook(View.class,\"setVisibility\",true)")&&code.contains("hook(ViewGroup.class,\"addView\",false)")&&code.contains("dispatchAttachedToWindow"),"synchronous visibility, creation and attach boundaries");
        check(code.contains("hidden.put(view,requested);args[0]=View.GONE;"),"host visibility writes saved before suppression");
        check(code.contains("hidden.put(view,view.getVisibility())")&&code.contains("hidden.remove(view);write(view,original)"),"first state saved and restored without forcing VISIBLE");
        check(code.contains("known.add(view)")&&code.contains("new ArrayList<>(known)"),"initially disabled targets revisited for live enable");
        check(code.contains("writing.set(true)")&&code.contains("finally {writing.remove();}"),"module writes do not replace host restore state");
        check(code.contains("addOnPreDrawListener")&&code.contains("syncDramaChildren(container,0)"),"root pre-draw covers late binding and hidden target restore");
        check(!code.contains("postDelayed(")&&!code.contains("setLayoutParams(")&&!code.contains(".height = 0"),"no delayed hiding or irreversible height mutation");
        check(!code.contains("PixelCopy"),"search visibility does not introduce capture");
        System.out.println("DramaSearchVisibilityTest passed (source boundary guards only; device behavior unverified)");
    }
    private static void check(boolean pass,String message){if(!pass)throw new AssertionError(message);}
}
