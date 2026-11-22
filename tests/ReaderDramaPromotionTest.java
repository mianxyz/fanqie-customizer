package io.github.mianxyz.fanqiecustomizer;

/** Precision and switch restoration contracts for reader-specific series promotion. */
public final class ReaderDramaPromotionTest {
    public static void main(String[] args){
        String prefix="com.dragon.read.component.shortvideo.impl.reader.depend.";
        for(String factory:new String[]{prefix+"ShortSeriesParagraphInsertDependProvider",prefix+"ShortSeriesReadGenreCardDependProvider"}){
            check(ReaderDramaPromotionHooks.promotionFactory(factory),"confirmed factory");
            check(ReaderDramaPromotionHooks.blockRequest(true,true,ReaderDramaPromotionHooks.promotionFactory(factory)),"promotion on");
            check(!ReaderDramaPromotionHooks.blockRequest(true,false,ReaderDramaPromotionHooks.promotionFactory(factory)),"runtime off restores request");
        }
        for(String other:new String[]{null,"",prefix+"OtherDependProvider",prefix+"ShortSeriesParagraphInsertDependProviderExtra",
            "com.dragon.read.component.shortvideo.api.model.ShortSeriesLaunchArgs","com.dragon.read.video.ui.VideoActivity"}){
            check(!ReaderDramaPromotionHooks.promotionFactory(other),"unrelated factory and active player retained");
            check(!ReaderDramaPromotionHooks.blockRequest(true,true,ReaderDramaPromotionHooks.promotionFactory(other)),"no generic series block");
        }
        String source="com.dragon.read.GenreCardRequestSource";
        check(ReaderDramaPromotionHooks.requestSignature("e","boolean","java.lang.String",source,"boolean"),"exact request gate");
        check(!ReaderDramaPromotionHooks.requestSignature("e","void","java.lang.String",source,"boolean"),"void excluded");
        check(!ReaderDramaPromotionHooks.requestSignature("f","boolean","java.lang.String",source,"boolean"),"wrong method excluded");
        check(!ReaderDramaPromotionHooks.requestSignature("e","boolean","java.lang.String","java.lang.Object","boolean"),"wrong argument excluded");
        check(!ReaderDramaPromotionHooks.requestSignature("e","boolean","java.lang.String",source),"wrong arity excluded");
        check(!ReaderDramaPromotionHooks.requestSignature("e","boolean",(String[])null),"unknown signature excluded");
        check(ReaderDramaPromotionHooks.blockPendant(true,true,true),"reader single-ad eligibility suppressed");
        check(!ReaderDramaPromotionHooks.blockPendant(true,false,true),"runtime off restores pendant");
        check(!ReaderDramaPromotionHooks.blockPendant(true,true,false),"outside reader active drama retained");
        check(!ReaderDramaPromotionHooks.blockPendant(true,false,false),"disabled non-reader retained");
        check(!ReaderDramaPromotionHooks.blockRequest(false,true,true),"master off restores request");
        check(!ReaderDramaPromotionHooks.blockPendant(false,true,true),"master off restores pendant");
        check(ReaderDramaPromotionHooks.preLayoutSignature("cv3.b","com.dragon.read.rpc.model.CellViewData","com.dragon.reader.lib.ReaderClient"),"cached model pre-layout boundary");
        check(!ReaderDramaPromotionHooks.preLayoutSignature("void","com.dragon.read.rpc.model.CellViewData","com.dragon.reader.lib.ReaderClient"),"void rendering callback excluded");
        check(!ReaderDramaPromotionHooks.preLayoutSignature("cv3.b","java.lang.Object","com.dragon.reader.lib.ReaderClient"),"unrelated models retained");
        check(ReaderDramaPromotionHooks.preLayoutSignature("cv3.a","com.dragon.read.rpc.model.CellViewData"),"page model before pagination");
        System.out.println("ReaderDramaPromotionTest passed");
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
}
