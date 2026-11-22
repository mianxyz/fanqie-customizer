package io.github.mianxyz.fanqiecustomizer;

import java.nio.file.Files;
import java.nio.file.Path;

/** Regression boundaries for 5.3.58; rendering remains a device acceptance check. */
public final class CardGlass558Test {
    public static void main(String[] args) throws Exception {
        check(CardGlassHooks.SourceRules.dark(0xff202020),"dark cover raises readable mist floor");
        check(!CardGlassHooks.SourceRules.dark(0xffdddddd),"light cover retains low mist");
        String source=new String(HostBaselineEquivalenceTest.originalBytes("CardGlassHooks.java"), java.nio.charset.StandardCharsets.UTF_8);
        check(source.contains("Bitmap.Config.HARDWARE?actual.copy(Bitmap.Config.ARGB_8888,false):actual"),"hardware source has software-safe copy");
        check(source.contains("card.painter.get()!=painter||card.observedBitmap.get()!=actual||card.observedGeneration!=generation"),"same painter new bitmap and pixel generation observed");
        check(source.contains("card placeholder id=")&&source.contains("if(placeholder(source))"),"white and transparent placeholders retry without source cache");
        check(source.contains("0x52FFFFFF,bottom,Shader.TileMode.CLAMP")&&source.contains("0x78FFFFFF:0x72FFFFFF"),"day mist gradient and dark-cover floor");
        check(!source.contains("0xB8FFFFFF")&&source.contains("0x99FFFFFF"),"opaque day wash removed and separator retained");
        check(source.contains("card.rootBgSkipped=true;content(chain.getArg(0))"),"root background skips paint only and preserves children");
        for(String field:new String[]{"card diag id="," painter="," src="," bottomAvgARGB="," rootBgSkipped="," hw="," textBox="})check(source.contains(field),"diagnostic field "+field);
        check(source.contains("diagnosticCards<3"),"diagnostic card count bounded");
        check(source.contains("5.5f*density")&&source.contains("width*.25f")&&source.contains("finally{content(scope);}"),"22dp blur confined under clear foreground");
        check(source.contains("DrawablePainter.j(f0.h)")&&source.contains("painterCards.put(painter,new PainterBinding(scoped,activeKey(scoped)))")&&source.contains("Objects.equals(bound.key,activeKey(card))"),"async concrete painter draw is identity-bound");
        check(source.contains("if(iw<=0)iw=bounds.width()")&&source.contains("if(ih<=0)ih=bounds.height()"),"draw-sized wrappers do not require intrinsic dimensions");
        check(source.contains("drawableBitmap(current,visited,depth+1)")&&source.contains("depth>8"),"wrapper extraction has cycle and depth bounds");
        check(source.contains("(p>>>24)>96"),"low-alpha shared placeholder is excluded");
        check(source.contains("tagBackgrounds(result,rootCard)"),"root background tags returned modifier including reused elements");
        System.out.println("CardGlass558Test passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
