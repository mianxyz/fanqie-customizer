package io.github.mianxyz.fanqiecustomizer;

/** Regressions for IDs, search interception and the native fold toggle. */
public final class BookRulesTest {
    public static void main(String[] args){
        String large="7393299999999999999";
        check(large.equals(BookRules.bookId("  "+large+"\n")),"19-digit ID must retain every digit");
        check("000123".equals(BookRules.bookId("000123")),"do not rewrite an ID");
        for(String invalid:new String[]{null,""," ","0","000000","-123","1.23","1e19","１２３","书名123","https://example.com/123","123 456","1234567890123456789012345"})
            check(BookRules.bookId(invalid)==null,"invalid ID rejected: "+invalid);
        for(String query:new String[]{"三体","2026","123456789012345","我的订单","番茄7393299999999999999"})
            check(BookRules.searchId(query)==null,"ordinary search must proceed: "+query);
        check("1234567890123456".equals(BookRules.searchId("1234567890123456")),"16-digit search ID");
        check(large.equals(BookRules.searchId(large)),"large search ID");
        check("123456789012345678901234".equals(BookRules.searchId("123456789012345678901234")),"24-digit upper bound");
        check(BookRules.needsUnfold(true,true,0,"展开全部"),"collapsed bookshelf unfolds");
        check(!BookRules.needsUnfold(true,true,180,"展开全部"),"expanded toggle must not collapse again");
        check(!BookRules.needsUnfold(false,true,0,"展开全部"),"hidden header must not click");
        check(!BookRules.needsUnfold(true,false,0,"展开全部"),"disabled header must not click");
        check(!BookRules.needsUnfold(true,true,0,"已全部展开"),"fully expanded header unchanged");
        check(!BookRules.needsUnfold(true,true,0,"展开全部浏览历史"),"shared history header must not click");
        check(!BookRules.needsUnfold(true,true,Float.NaN,"展开全部"),"unknown rotation must not click");
        check(!BookRules.needsUnfold(true,true,0,null),"missing label must not click");
        check(BookRules.isSettingsPage("com.dragon.read.component.biz.impl.mine.settings.KmpRootSettingsActivity"),"native settings entry");
        check(!BookRules.isSettingsPage("other.app.SettingsActivity"),"unrelated settings page unchanged");
        check(BookRules.isBookPage("com.dragon.read.reader.detail.BookDetailActivity"),"details badge");
        check(BookRules.isReaderPage("com.dragon.read.reader.ui.ReaderActivity"),"reader badge");
        check(!BookRules.isBookPage("com.dragon.read.pages.main.MainFragmentActivity"),"no book badge on main page");
        check(Rules.defaultOn("in_app_entry"),"book number tools entry is available by default");
        for(String key:new String[]{"enabled","auto_expand_shelf","show_book_id","book_id_search","force_open_removed","hide_welfare_float"})
            check(!Rules.defaultOn(key),"new modifications remain opt-in: "+key);
        check("hide_welfare_float".equals(Rules.viewRule("com.bytedance.ug.sdk.kmp.novel.pendant.w")),"real pendant Compose host covered");
        check(Rules.viewRule("androidx.compose.ui.platform.ComposeView")==null,"ordinary Compose UI preserved");
        check(Rules.viewRule("com.dragon.read.component.biz.impl.bookmall.widge.z")==null,"inline ranking card preserved");
        System.out.println("BookRulesTest passed");
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
