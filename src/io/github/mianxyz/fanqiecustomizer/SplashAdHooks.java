package io.github.mianxyz.fanqiecustomizer;

import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.concurrent.CancellationException;

/** Keep attribution truthful. Complete the outer ad acquisition through the host NO_AD consumer.
 * 73932: SplashActivity.t1 -> SplashHelper.a -> z0/y0 -> NO_AD -> presenter.u7.
 * A false hasHitAttribution instead sends established users into first-launch attribution waits.
 * The exact stable entry below is independently checked against all six APKs by the 615 fixture.
 */
final class SplashAdHooks {
    static final String HELPER = "com.dragon.read.pages.splash.SplashHelper";
    private final XposedModule module;
    private final FeatureHooks.Host host;
    private final ClassLoader loader;
    SplashAdHooks(XposedModule module, FeatureHooks.Host host, ClassLoader loader) {
        this.module=module; this.host=host; this.loader=loader;
    }
    static boolean isAdAcquisition(String owner,String name,String result,String[] parameters,int modifiers) {
        return HELPER.equals(owner) && "a".equals(name) && "io.reactivex.Single".equals(result)
            && parameters.length==1 && "java.lang.Boolean".equals(parameters[0])
            && Modifier.isPublic(modifiers) && !Modifier.isStatic(modifiers) && !Modifier.isNative(modifiers);
    }
    void install() {
        int hits=0;
        try {
            Class<?> cls=Class.forName(HELPER,false,loader);
            Class<?> single=Class.forName("io.reactivex.Single",false,loader);
            Object noAd=single.getMethod("error",Throwable.class).invoke(null,new CancellationException("Pulp: no splash ad"));
            for(Method method:cls.getDeclaredMethods()) {
                Class<?>[] types=method.getParameterTypes(); String[] params=new String[types.length];
                for(int i=0;i<types.length;i++)params[i]=types[i].getName();
                if(!isAdAcquisition(cls.getName(),method.getName(),method.getReturnType().getName(),params,method.getModifiers()))continue;
                module.hook(method).intercept(chain->noAd);
                hits++;
            }
        } catch(Throwable error) {host.log("Splash ad hook unavailable: "+PrivacyRules.errorType(error));}
        host.log("Splash ad hooks registered: "+hits+" (outer acquisition, attribution preserved)");
    }
}
