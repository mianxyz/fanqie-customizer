package io.github.mianxyz.fanqiecustomizer;

/** Pure rules shared by native search, the ID dialog and reader badges. */
public final class BookRules {
    private BookRules(){}
    public static String bookId(String text){
        if(text==null)return null;String value=text.trim();
        if(value.isEmpty()||value.length()>24)return null;
        boolean nonzero=false;
        for(int i=0;i<value.length();i++){char c=value.charAt(i);if(c<'0'||c>'9')return null;nonzero|=c!='0';}
        return nonzero?value:null;
    }
    public static String searchId(String text){String id=bookId(text);return id!=null&&id.length()>=16?id:null;}
    public static boolean needsUnfold(boolean shown,boolean enabled,float rotation,String text){
        return shown&&enabled&&Math.abs(rotation)<0.5f&&"展开全部".equals(text==null?null:text.trim());
    }
    public static boolean isSettingsPage(String name){
        return "com.dragon.read.component.biz.impl.mine.settings.SettingsActivity".equals(name)
            ||"com.dragon.read.component.biz.impl.mine.settings.KmpSettingsActivity".equals(name)
            ||"com.dragon.read.component.biz.impl.mine.settings.KmpRootSettingsActivity".equals(name);
    }
    public static boolean isBookPage(String name){return "com.dragon.read.reader.detail.BookDetailActivity".equals(name)||isReaderPage(name);}
    public static boolean isReaderPage(String name){return "com.dragon.read.reader.ui.ReaderActivity".equals(name);}
}
