package io.github.mianxyz.fanqiecustomizer;

import java.util.List;

/** Exercises production identity/selection rules without an Android runtime. */
public final class CardGlassSourceRulesTest {
    public static void main(String[] args) throws Exception {
        CardGlassHooks.SourceRules.Identity identity=new CardGlassHooks.SourceRules.Identity();
        check(!identity.update(null,null),"unresolved model has no change evidence");
        check(!identity.update(" cover-A ",null),"first known URL initializes without clearing ready pixels");
        check(!identity.update("cover-A",null),"equal URL survives node/model reallocation");
        check(!identity.update("",null),"loading blank preserves identity");
        check(!identity.update(null,"night-A"),"late night URL enriches identity");
        check(!identity.update("cover-A","night-A"),"same pair survives rebinding");
        check(identity.update("cover-B",null),"resolved day URL changing clears old book");
        check(identity.night==null,"new book does not inherit old night identity");
        check(!identity.update(null,null),"placeholder after book change keeps new identity");
        check(!identity.update(null,"night-B"),"first night URL for new book initializes");
        check(identity.update(null,"night-C"),"night-only book URL change detected");
        Node badge=new Node(new Layout(16,16),new Config("badge-url",null,"badge-resource"));
        Node cover=new Node(new Layout(-1,-2),new Config(null,null,"cover-resource"));
        check(CardGlassHooks.selectCover(List.of(badge,cover))==cover,"resource badge before cover excluded");
        check(CardGlassHooks.selectCover(List.of(cover,badge))==cover,"badge reorder keeps cover");
        check(CardGlassHooks.selectCover(List.of(badge))==null,"badge-only tree has no cover");
        check(CardGlassHooks.selectCover(List.of(cover,new Node(new Layout(-1,-2),new Config("other-url",null,null))))==null,"ambiguous full-cover nodes never guessed");
        check(CardGlassHooks.selectCover(List.of(new Node(new Layout(-1,-2),new Config("cover-url",null,null))))!=null,"network cover uses same layout rule");
        Node mixed=new Node(new Layout(Float.valueOf(-1f),Double.valueOf(-2d)),new Config(null,null,null));
        check(CardGlassHooks.selectCover(List.of(mixed))==mixed,"Float width and Double height accepted");
        Node reverseMixed=new Node(new Layout(Double.valueOf(-1d),Float.valueOf(-2f)),new Config(null,null,null));
        check(CardGlassHooks.selectCover(List.of(reverseMixed))==reverseMixed,"Double width and Float height accepted");
        check(!CardGlassHooks.SourceRules.numberEquals(null,-1),"missing layout excluded");
        check(!CardGlassHooks.SourceRules.numberEquals("-1",-1),"non-number layout excluded");
        check(!CardGlassHooks.SourceRules.numberEquals(Float.NaN,-1),"NaN layout excluded");
        String source=new String(HostBaselineEquivalenceTest.originalBytes("CardGlassHooks.java"), java.nio.charset.StandardCharsets.UTF_8);
        check(source.split("clearSource\\(",-1).length-1==2,"one clear caller plus declaration: node/exception/placeholder paths never clear");
        check(source.contains("if(c.coverIdentity.update(resolved[0],resolved[1]))clearSource(c,\"cover-url-changed\")"),"only confirmed URL change owns clear");
        check(source.contains("finally{content(scope);}"),"foreground text still drawn after independent glass layer");
        check(!source.contains("coverDimensions")&&!source.contains("hasCoverPixels"),"intrinsic dimensions and sampled colors never exclude scoped cover input");
        check(source.contains("5.5f*density")&&source.contains("width*.25f"),"quarter-size blur retains effective 22dp radius");
        System.out.println("CardGlassSourceRulesTest passed (production identity, observed DSL selection and clear boundaries)");
    }
    static final class Node { final Integer b=1; final Layout d; final Config f; Node(Layout d,Config f){this.d=d;this.f=f;} }
    static final class Layout { final Number a,b; Layout(Number a,Number b){this.a=a;this.b=b;} }
    static final class Config { final Object d,e; final String g; Config(Object d,Object e,String g){this.d=d;this.e=e;this.g=g;} }
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
