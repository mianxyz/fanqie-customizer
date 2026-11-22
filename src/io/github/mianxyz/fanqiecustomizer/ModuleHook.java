package io.github.mianxyz.fanqiecustomizer;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

public final class ModuleHook extends XposedModule {
    private static final String BAR="com.dragon.read.widget.BottomTabBarLayout";
    private final Map<String,Boolean> flags=new HashMap<>();
    /** 整型参数（滑块）。与 flags 同批读取，缺省用 Rules.intDefault。 */
    private final Map<String,Integer> ints=new HashMap<>();
    private final Set<String> logged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Class<?>,String> classRules=new HashMap<>();
    private final Map<View,Integer> originalVisibility=new WeakHashMap<>();
    private boolean installed;
    private String processName;
    private final BookmallBusinessConfig businessConfig=new BookmallBusinessConfig();
    private FeatureHooks features;
    private Lifecycle lifecycle;
    private BookTools books;
    private ReaderLiveLaunchHooks liveLaunches;
    private BottomNavigationHooks bottomNavigation;
    private final Map<View,String> forcedHidden=Collections.synchronizedMap(new WeakHashMap<>());
    private final Set<View> mineRoots=Collections.newSetFromMap(Collections.synchronizedMap(new WeakHashMap<View,Boolean>()));

    @Override public void onModuleLoaded(ModuleLoadedParam param) {
        processName=param.getProcessName();
    }

    @Override public void onPackageReady(PackageReadyParam param) {
        // 只要求「包名是宿主」，进程名不再强制相等。
        // 主进程的 processName 在不同框架/版本下可能是 "com.dragon.read"
        // 或带后缀，强制相等会让入口彻底不匹配、模块完全不生效。
        // 子进程是否处理由各 Hook 自行按需判断。
        if(!Rules.isTargetPackage(param.getPackageName()))return;
        if(!param.isFirstPackage())return;
        try {
            Method attach=Application.class.getDeclaredMethod("attach",Context.class);
            hook(attach).intercept(chain -> {
                Object result=chain.proceed();
                if(!installed) {
                    installed=true;
                    final Application app=(Application)chain.getThisObject();
                    final Context ctx=(Context)chain.getArg(0);
                    // 不能在 attach 里装 Hook：这是宿主启动主线程。
                    // 也不宜「立刻开后台线程」——42 处 Class.forName 会和
                    // 宿主自身的类加载抢 DEX 索引锁，主线程照样被拖慢。
                    // 改为等第一个 Activity 首帧之后再装：此时启动已完成，
                    // 安装既不阻塞冷启动，也不与首屏渲染争资源。
                    // 开屏/状态栏这两项必须在启动早期就位，但成本极低：
                    // 只 Class.forName 一个宿主类 + 一个系统类，不扫方法表。
                    // 因此放在主线程同步装，其余 40 处类查找走延迟安装。
                    // 回到 5.0.5 已验证稳定的做法：在 attach 之后同步安装。
                    // libxposed API 102 允许在拦截回调里安装其它类的 Hook，
                    // 之前「重入会被拒绝」的判断是错的——那几轮新功能全部失效
                    // 正是被延迟安装拖到了视图构造之后。
                    //
                    // 启动耗时由 loadSettings 保证：远程偏好命中即返回（毫秒级），
                    // 免 Root 后备路径绝不阻塞主线程。
                    logMessage("Attach reached; installing hooks");
                    try { installAll(app,ctx); }
                    catch(Throwable error){logMessage("Install FAILED: "+error.getClass().getName()+": "+error.getMessage());}
                }
                return result;
            });
        }catch(Throwable error){logMessage("Application hook failed: "+PrivacyRules.errorType(error));}
    }

    /**
     * 等宿主首屏渲染完成后再安装 Hook。
     *
     * 判据用第一个 Activity 的 onResume（首帧已在此时提交给合成器），
     * 再留一小段缓冲，避开 onResume 里密集的业务初始化。
     * 注册只挂一次，安装只跑一次。
     */
    /** 启动早期就必须就位、且加载成本极低的 Hook。 */
    /**
     * 在 attach 之后同步安装全部 Hook。
     *
     * 结构回到 5.0.5 的已验证形态：配置先就位，然后把各组 Hook 装上。
     * 新增功能与老功能走同一条路径，不再有「延迟安装」这个分支。
     */
    private void installAll(Application app,Context ctx) {
        long start=SystemClock.uptimeMillis();
        loadSettings(ctx);
        logMessage("Settings ready: bool="+flags.size()+" int="+ints.size()+" enabled="+on("enabled"));
        if(!on("enabled")){
            logMessage("Customizations disabled by configuration (enabled=false); hot hooks=0");
            logMessage("All hooks installed in "+(SystemClock.uptimeMillis()-start)+"ms");
            return;
        }
        try {
            installRest(app,ctx);
        } catch (Throwable error) {
            logMessage("Install FAILED: "+error.getClass().getName()+": "+error.getMessage());
        }
        logMessage("All hooks installed in "+(SystemClock.uptimeMillis()-start)+"ms");
    }

    /**
     * 单个 Hook 组的安装隔离：失败只记日志，不影响其它功能。
     *
     * 同时记录每组耗时。宿主启动被拖慢时，这行日志能直接指出是哪一组，
     * 不必再靠猜——之前排查启动慢就是因为没有任何计时信息。
     */
    private void installQuietly(String name, Runnable action) {
        long start=SystemClock.uptimeMillis();
        try {
            action.run();
        } catch (Throwable error) {
            // 把异常类型和消息都打出来：只打类型会丢掉「方法不存在」这类关键信息。
            logMessage("Hook group '"+name+"' FAILED: "+error.getClass().getName()+": "+error.getMessage());
        }
        long cost=SystemClock.uptimeMillis()-start;
        logMessage("Hook group '"+name+"' done in "+cost+"ms");
    }

    private void installRest(Application app,Context context) throws Throwable {
        // 版本检查必须容错：任一环节抛错都会让整个模块静默失效。
        // 包名优先用入口已判定的宿主包名，避免 attach 阶段 context 尚未定型。
        String hostPackage=null;
        long versionCode=0L;
        try {
            String fromContext=context==null?null:context.getPackageName();
            hostPackage=Rules.isTargetPackage(fromContext)?fromContext:hostPackage;
            if(hostPackage==null)for(String candidate:Rules.TARGET_PACKAGES){
                try{context.getPackageManager().getPackageInfo(candidate,0);hostPackage=candidate;break;}catch(Throwable ignored){}
            }
            if(hostPackage==null){logMessage("Host package not resolved; using "+Rules.TARGET);hostPackage=Rules.TARGET;}
            PackageInfo info=context.getPackageManager().getPackageInfo(hostPackage,0);
            versionCode=info.getLongVersionCode();
            String versionName=info.versionName;
            if(!Rules.supported(versionCode,versionName)) {
                // 只警告不阻断：宿主小版本号变动不该让整模块失效。
                // 各 Hook 自身按类名/方法名查找，缺什么会在对应分组里报 FAILED。
                logMessage("Untested version "+versionName+" ("+versionCode+"); installing anyway");
            }
        } catch (Throwable error) {
            // 取不到版本信息时不阻断：按最宽松处理继续安装，由各 Hook 自行判断。
            logMessage("Version probe failed ("+PrivacyRules.errorType(error)+"); continuing with "+hostPackage);
        }
        if(!(Rules.TARGET.equals(hostPackage)&&versionCode==73932L))HostSymbolData.registerCurrent(hostPackage,versionCode);
        HostSymbols.initialize(hostPackage,versionCode,context.getClassLoader(),this::logMessage);
        // 配置已由 installAll 读好，这里不再重复读取。
        final Lifecycle lifecycleRef=new Lifecycle();
        lifecycle=lifecycleRef;
        // 生命周期回调必须在主线程注册：后台线程注册会错过已经创建的 Activity。
        // lifecycleRef 是 final，保证主线程一定能看到这个已构造好的对象。
        new Handler(Looper.getMainLooper()).post(()->{
            try{app.registerActivityLifecycleCallbacks(lifecycleRef);}
            catch(Throwable error){logMessage("Lifecycle register failed: "+PrivacyRules.errorType(error));}
        });
        books=new BookTools(this,new BookTools.Host(){public boolean on(String key){return ModuleHook.this.on(key);}public void log(String message){logOnce(message);}},context.getClassLoader());
        // 主开关语义：只有「明确读到 enabled=false」才停用。
        // 完全读不到配置时按启用处理——否则一次同步失败就会让模块
        // 表现为「装了但完全不生效」，而这个状态用户无法自行判断。
        if(!flags.isEmpty()&&!on("enabled")){
            logMessage("Customizations disabled by configuration (enabled=false).");
            return;
        }
        if(flags.isEmpty())logMessage("No settings were loaded; installing hooks with defaults.");
        logMessage("Feature keys: barFloat="+on("glass_bottom_bar_floating")
            +" barDock="+on("glass_bottom_bar_docked")
            +" top="+on("glass_top_bar")
            +" search="+on("hide_search_bar")
            +" refresh="+on("refresh_confirm")
            +" listen="+on("unlimited_listen")+" autoread="+on("unlimited_autoread"));
        logMessage("Bar params: alpha="+intValue("glass_bar_alpha")
            +" blur="+intValue("glass_bar_blur")
            +" corner="+intValue("glass_bar_corner")
            +" margin="+intValue("glass_bar_margin")
            +" bottom="+intValue("glass_bar_bottom"));
        ScrollWorkGate.install(this);
        ScrollWorkGate.observeRecycler(this,context.getClassLoader());
        books.install();
        NativeUiHooks.Host host=new NativeUiHooks.Host(){
            public boolean on(String key){return ModuleHook.this.on(key);}
            public int intValue(String key){return ModuleHook.this.intValue(key);}
            public void log(String text){logOnce(text);}
            public void forceHide(View view,String reason){forcedHidden.put(view,reason);if(Looper.myLooper()==Looper.getMainLooper())hide(view,reason);else new Handler(Looper.getMainLooper()).post(()->hide(view,reason));}
            public void dataChanged(){new Handler(Looper.getMainLooper()).post(()->{for(Controller c:lifecycle.controllers.values())c.schedule();});}
            public void mineRoot(View root){mineRoots.add(root);logOnce("Native mine root registered: "+root.getClass().getName());}
            public void pageShown(String name){
                logOnce("Visible fragment: "+name);
                GlobalGlassSurfaces.pageChanged();
                for(Controller c:lifecycle.controllers.values()){c.diagnosed=false;c.resumedAt=SystemClock.uptimeMillis();c.schedule();}
                // 书城页可见时补扫一次：这是日志里唯一稳定触发的入口
                // （"Visible fragment: ...NewBookMallFragment"），
                // 作为 SuperSwipeRefreshLayout / AppBarLayout 挂载完成后的双保险。
                if(name!=null&&name.contains("NewBookMallFragment")&&glassSurfaces!=null){
                    Activity current=lifecycle.resumedActivity;
                    if(current!=null)glassSurfaces.scan(current);
                }
            }
            public boolean readerVisible(){Activity current=lifecycle.resumedActivity;return current!=null&&BookRules.isReaderPage(current.getClass().getName());}
            public boolean tabletLayout(){Activity current=lifecycle.resumedActivity;Context owner=current==null?context:current;return TabletRules.layout(owner.getResources().getConfiguration().smallestScreenWidthDp);}
        };
        List<String> enabled=new ArrayList<>();for(String key:flags.keySet())if(on(key))enabled.add(key);Collections.sort(enabled);logOnce("Enabled settings: "+enabled);
        new StartupTabHooks(this,host,context.getClassLoader()).install();
        bottomNavigation=new BottomNavigationHooks(this,host,context.getClassLoader());bottomNavigation.install();
        new UpdatePopupHooks(this,host,context.getClassLoader()).install();
        new DramaResumeHooks(this,host,context.getClassLoader()).install();
        new PostShortcutHooks(this,host,context.getClassLoader()).install();
        new ReaderBannerHooks(this,host,context.getClassLoader()).install();
        new ReaderAdHooks(this,host,context.getClassLoader()).install();
        installQuietly("reader-drama-promotion",()->new ReaderDramaPromotionHooks(this,host,context.getClassLoader()).install());
        // ── 新增功能：全部在这里同步安装，与老功能同一条路径 ──
        // 每条都是独立隔离，一处失败不影响其余。
        installQuietly("splash", () -> {
            if(on("hide_splash_ad"))new SplashAdHooks(this,host,context.getClassLoader()).install();
        });
        installQuietly("statusbar", () -> {
            if(on("hide_status_bar"))new ImmersiveHooks(this,host,context.getClassLoader()).install();
        });
        installQuietly("glass", () -> {
            if(on("glass_bottom_bar_floating")||on("glass_bottom_bar_docked")||on("glass_top_bar")){
                // 直接传入 attach 回调里现成的 Application。
                // 之前 GlassSurfaces 自己反射 ActivityThread.mInitialApplication，
                // 那个字段此刻还是 null，导致底栏入口从未注册（实机日志：
                // "Glass lifecycle FAILED: no Application"，hooks 从 3 掉到 2）。
                glassSurfaces=new GlassSurfaces(this,host,context.getClassLoader(),app);
                glassSurfaces.install();
            }
        });
        FeatureHooks.Host businessHost=new FeatureHooks.Host(){
            public boolean on(String key){return businessOn(key);}
            public void log(String text){logMessage(text);}
            public void forceHide(View view,String reason){host.forceHide(view,reason);}
            public void dataChanged(){host.dataChanged();}
        };
        installQuietly("global-glass",()->new GlobalGlassSurfaces(this,businessHost,context.getClassLoader()).install(app));
        businessConfig.install(this,app,this::logMessage);
        installQuietly("bookmall-business",()->new BookmallBusinessViews(businessHost).install(app));
        installQuietly("refresh",()->new RefreshConfirm(this,businessHost,context.getClassLoader()).install());
        installQuietly("privilege", () -> {
            if(on("unlimited_listen")||on("unlimited_autoread")||on("hide_reader_ads"))
                new PrivilegeHooks(this,host,context.getClassLoader()).install();
        });
        installQuietly("widget", () -> {
            if(on("hide_widget_prompt"))
                new WidgetPromptHooks(this,host,context.getClassLoader()).install();
        });

        liveLaunches=new ReaderLiveLaunchHooks(this,host,context.getClassLoader());liveLaunches.install();
        // Mine and sidebar filters share the live configuration adapter below.
        NativeUiHooks.Host nativeHost=new NativeUiHooks.Host(){
            public boolean on(String key){return BookmallBusinessConfig.manages(key)?businessOn(key):host.on(key);}
            public void log(String text){host.log(text);}
            public void forceHide(View v,String reason){host.forceHide(v,reason);}
            public void dataChanged(){host.dataChanged();}
            public boolean tabletLayout(){return host.tabletLayout();}
            public void mineRoot(View root){host.mineRoot(root);}
            public void pageShown(String name){host.pageShown(name);}
        };
        features=new FeatureHooks(this,nativeHost,context.getClassLoader());features.install();
        NativeUiHooks nativeUi=new NativeUiHooks(this,nativeHost,context.getClassLoader());
        nativeUi.countSink=features::recordCount;nativeUi.install();
        if(on("local_vip"))installVip(context.getClassLoader());
        logMessage("Pulp loaded for "+hostPackage+" code="+versionCode);
    }

    /**
     * 读取开关：LSPosed 远程偏好是主通道，只有在它拿不到任何一项时
     * 才依次尝试 provider / 有序广播 / 共享文件。
     *
     * 为什么必须先问远程偏好：改成「总是先读后备」会让 Root 环境在
     * 后备文件陈旧时覆盖框架里的最新配置，等于静默回退用户设置。
     * 后备路径每一步都独立 try/catch，任何异常都不阻止 Hook 继续安装。
     */
    private void loadSettings(Context context) {
        Map<String,Boolean> remote=new HashMap<>();
        try {
            SharedPreferences prefs=getRemotePreferences(Rules.PREFS);
            if(prefs!=null)for(Map.Entry<String,?> entry:prefs.getAll().entrySet()){
                String key=entry.getKey();
                if(!PrivacyRules.settingKey(key))continue;
                Object v=entry.getValue();
                if(v instanceof Boolean)remote.put(key,(Boolean)v);
                else if(v instanceof Integer&&Rules.isIntKey(key))
                    ints.put(key,SettingsKeys.clamp(key,(Integer)v));
            }
        }catch(Throwable error){logMessage("Remote preferences unavailable: "+PrivacyRules.errorType(error));}
        if(!remote.isEmpty()){
            // 迁移必须在 putAll 之前：旧键要先换成新键，否则升级后底栏玻璃会消失。
            SettingsKeys.migrate(remote);
            flags.putAll(remote);
            return;
        }
        // 只有包含主开关 enabled 的结果才算「真的拿到了用户配置」。
        // 内嵌框架下 provider 与宿主同进程，读的是番茄自己的空 prefs；
        // 若把「非空」当成功，就会用默认值覆盖、并跳过文件后备。
        String source=null;
        Map<String,Boolean> fallback=new HashMap<>();
        // 每条后备路径都同时收集整型参数：只拿布尔会让滑块永远是默认值。
        ints.clear();
        Bundle fromProvider=callProvider(context);
        SettingsKeys.collect(fromProvider,fallback);
        SettingsKeys.collectInts(fromProvider,ints);
        if(SettingsKeys.hasUserConfig(fallback))source="provider";
        else{
            fallback.clear();
            ints.clear();
            Bundle fromBroadcast=requestByBroadcast(context);
            SettingsKeys.collect(fromBroadcast,fallback);
            SettingsKeys.collectInts(fromBroadcast,ints);
            if(SettingsKeys.hasUserConfig(fallback))source="broadcast";
        }
        if(source==null){
            fallback.clear();
            ints.clear();
            Map<String,Boolean> file=EmbeddedPrefs.read(ints);
            if(file!=null)fallback.putAll(file);
            if(SettingsKeys.hasUserConfig(fallback))source="shared file";
        }
        if(source==null){
            logMessage("No settings source available; using defaults.");
            return;
        }
        SettingsKeys.migrate(fallback);
        flags.putAll(fallback);
        // 把有效配置写回宿主可见位置（内嵌框架下 provider 与宿主同进程时无副作用）。
        EmbeddedPrefs.writeHost(fallback,ints);
        logMessage("Settings loaded from "+source+" fallback: "+fallback.size()+" + "+ints.size()+" params");
    }

    private Bundle callProvider(Context context) {
        try {
            return context.getContentResolver().call(
                    Uri.parse("content://"+SettingsProvider.AUTHORITY),
                    SettingsProvider.METHOD,null,null);
        }catch(Throwable error){
            logMessage("Settings provider unavailable: "+PrivacyRules.errorType(error));
            return null;
        }
    }

    private Bundle requestByBroadcast(Context context) {
        HandlerThread thread=null;
        try {
            thread=new HandlerThread("FanqieSettings");thread.start();
            Handler scheduler=new Handler(thread.getLooper());
            final CountDownLatch done=new CountDownLatch(1);
            final AtomicReference<Bundle> answer=new AtomicReference<>();
            BroadcastReceiver receiver=new BroadcastReceiver(){
                @Override public void onReceive(Context c,Intent i){answer.set(getResultExtras(true));done.countDown();}
            };
            Intent request=new Intent(SettingsResponder.ACTION);
            request.setPackage(Rules.PACKAGE);
            request.setClassName(Rules.PACKAGE,SettingsResponder.class.getName());
            request.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES|Intent.FLAG_RECEIVER_FOREGROUND);
            context.sendOrderedBroadcast(request,null,receiver,scheduler,Activity.RESULT_OK,null,null);
            try{done.await(1200,TimeUnit.MILLISECONDS);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}
            return answer.get();
        }catch(Throwable error){
            logMessage("Settings broadcast skipped: "+PrivacyRules.errorType(error));
            return null;
        }finally{if(thread!=null)thread.quitSafely();}
    }

    private boolean businessOn(String key){Boolean live=businessConfig.value(key);return live!=null?live:on(key);}
    private boolean on(String key){return flags.containsKey(key)?Boolean.TRUE.equals(flags.get(key)):Rules.defaultOn(key);}

    /** 读整型参数（滑块）。没同步到就用 Rules 里的默认值。 */
    private int intValue(String key){
        Integer v=ints.get(key);
        return v!=null?v:Rules.intDefault(key);
    }
    private void logMessage(String message){log(Log.INFO,"FanqieCustomizer",message);}
    private void logOnce(String message){if(on("debug")&&logged.add(message))logMessage(message);}

    private void installVip(ClassLoader loader) {
        try {
            Class<?> cls=HostSymbols.type("com.dragon.read.component.biz.impl.NsVipImpl",loader);
            for(String name:new String[]{"isAnyVip","isVip","isSpecificVipOrHigher"}) {
                for(Method method:cls.getDeclaredMethods()) {
                    if(HostSymbols.matches(method,name)&&method.getReturnType()==boolean.class) {
                        hook(method).intercept(chain -> {logOnce("Local VIP: "+method.getName());return true;});
                    }
                }
            }
        }catch(Throwable error){logMessage("VIP hooks unavailable: "+PrivacyRules.errorType(error));}
    }

    private String ruleFor(Class<?> cls) {
        if(classRules.containsKey(cls))return classRules.get(cls);
        String rule=null;
        for(Class<?> c=cls;c!=null&&View.class.isAssignableFrom(c);c=c.getSuperclass()) {
            rule=Rules.viewRule(HostSymbols.canonical(c.getName()));if(rule!=null)break;
        }
        classRules.put(cls,rule);return rule;
    }

    private void hide(View view,String reason) {
        if(view.getVisibility()!=View.GONE){if(!originalVisibility.containsKey(view))originalVisibility.put(view,view.getVisibility());view.setVisibility(View.GONE);logOnce("Hidden "+reason+": "+view.getClass().getName());}
    }

    private void applyBar(View bar) {
        if(bottomNavigation!=null)bottomNavigation.apply(bar);
    }

    private String contextualRule(View view,Activity activity){
        if(forcedHidden.containsKey(view))return forcedHidden.get(view);
        if("com.dragon.read.pages.main.MainFragmentActivity".equals(activity.getClass().getName())&&on("hide_post_float")){
            String cls=HostSymbols.canonical(view.getClass().getName());
            if(cls.equals("dd5.o")||cls.equals("com.dragon.read.editor.CollapsingPublishLayout")||cls.equals("com.dragon.read.social.ui.PublishButton")||cls.equals("com.dragon.community.common.ui.contentpublish.PublishButton"))return "hide_post_float";
            if(view instanceof TextView&&"发帖子".contentEquals(((TextView)view).getText())){
                int[] pos=new int[2];view.getLocationOnScreen(pos);
                if(pos[0]>view.getResources().getDisplayMetrics().widthPixels/2&&pos[1]>view.getResources().getDisplayMetrics().heightPixels/2){
                    View owner=view;
                    for(int i=0;i<3&&owner.getParent() instanceof View;i++){
                        if(owner.isClickable())break;View parent=(View)owner.getParent();
                        if(parent.getWidth()>view.getResources().getDisplayMetrics().widthPixels/2||parent.getHeight()>view.getResources().getDisplayMetrics().heightPixels/3)break;owner=parent;
                    }
                    if(owner.isClickable()){forcedHidden.put(owner,"post shortcut");hide(owner,"post shortcut");}
                    return "hide_post_float";
                }
            }
        }
        if(on("hide_income")&&inMine(view)){
            int id=view.getId();String entry=id==View.NO_ID?null:resourceEntry(view);
            // The native My page also has a separate dynamic coin/income entry outside POLARIS.
            if("c4t".equals(entry))return "hide_income";
        }
        return null;
    }
    private String resourceEntry(View view){try{return HostSymbols.canonicalResource(view.getResources().getResourceEntryName(view.getId()));}catch(Throwable ignored){return null;}}
    private boolean inMine(View view){for(View current=view;current!=null;){if(mineRoots.contains(current))return true;current=current.getParent() instanceof View?(View)current.getParent():null;}return false;}

    private final class Controller implements ViewTreeObserver.OnGlobalLayoutListener,Runnable {
        final Activity activity;final View root; final Handler handler=new Handler(Looper.getMainLooper());
        TextView countBadge;
        boolean active; boolean queued; long last;long resumedAt;boolean diagnosed;
        Controller(Activity activity){this.activity=activity;this.root=activity.getWindow().getDecorView();}
        void start(){if(active)return;active=true;diagnosed=false;resumedAt=SystemClock.uptimeMillis();root.getViewTreeObserver().addOnGlobalLayoutListener(this);schedule();}
        void stop(){active=false;handler.removeCallbacks(this);queued=false;if(root.getViewTreeObserver().isAlive())root.getViewTreeObserver().removeOnGlobalLayoutListener(this);}
        @Override public void onGlobalLayout(){schedule();}
        void schedule(){if(!active||queued)return;queued=true;handler.postDelayed(this,Math.max(100,600-(SystemClock.uptimeMillis()-last)));}
        @Override public void run(){
            queued=false;if(!active)return;if(ScrollWorkGate.busy(root)){schedule();return;}last=SystemClock.uptimeMillis();
            try {
                if(books!=null)books.decorate(activity);
                if(!on("enabled"))return;
                updateCounts();
                if(on("debug")&&!diagnosed&&SystemClock.uptimeMillis()-resumedAt>=6000){diagnosed=true;diagnoseFloatingViews();}
                ArrayDeque<View> queue=new ArrayDeque<>();queue.add(root);int visited=0;
                while(!queue.isEmpty()&&visited++<5000){
                    View v=queue.removeFirst();String rule=ruleFor(v.getClass());
                    if(rule!=null&&on(rule)){hide(v,rule);continue;}
                    String contextual=contextualRule(v,activity);if(contextual!=null){hide(v,contextual);continue;}
                    if(BAR.equals(v.getClass().getName()))applyBar(v);
                    // Search visibility is owned by synchronous, reversible search controllers.
                    if(books!=null&&"com.dragon.read.component.biz.impl.bookshelf.feed.BSFeedTitleHeaderView".equals(v.getClass().getName()))books.unfold(v);
                    if(v instanceof ViewGroup&&v.getVisibility()!=View.GONE){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)queue.addLast(g.getChildAt(i));}
                }
            }catch(Throwable e){logOnce("UI adaptation failed: "+PrivacyRules.errorType(e));}
            if(active&&(on("debug")||on("hide_welfare_float")||on("hide_post_float")||on("auto_expand_shelf"))&&SystemClock.uptimeMillis()-resumedAt<15000)schedule();
        }
        void diagnoseFloatingViews(){
            logOnce("Visible activity: "+activity.getClass().getName());
            if(!"com.dragon.read.pages.main.MainFragmentActivity".equals(activity.getClass().getName()))return;
            ArrayDeque<View> pending=new ArrayDeque<>();pending.add(root);int seen=0,reported=0;
            int screenWidth=root.getResources().getDisplayMetrics().widthPixels,screenHeight=root.getResources().getDisplayMetrics().heightPixels;
            float density=root.getResources().getDisplayMetrics().density;
            while(!pending.isEmpty()&&seen++<5000&&reported<80){View view=pending.removeFirst();
                if(view.getVisibility()!=View.VISIBLE||!view.isShown())continue;
                int[] xy=new int[2];view.getLocationOnScreen(xy);
                boolean image=view instanceof ImageView||view.getClass().getName().contains("Drawee");
                boolean edge=xy[0]<screenWidth/3||xy[0]+view.getWidth()>screenWidth*2/3;
                if((view.isClickable()||image)&&view.getWidth()>0&&view.getHeight()>0&&view.getWidth()<density*240&&view.getHeight()<density*240&&edge){
                    Object parent=view.getParent();String parentName=parent==null?"none":parent.getClass().getName();
                    logOnce("Float candidate: "+view.getClass().getName()+" id="+resourceEntry(view)+" parent="+parentName+" size="+view.getWidth()+"x"+view.getHeight());reported++;
                }
                if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)pending.add(group.getChildAt(i));}
            }
        }
        void updateCounts(){
            if(!on("raw_read_counts")||features==null||!"com.dragon.read.reader.detail.BookDetailActivity".equals(activity.getClass().getName()))return;
            String id=activity.getIntent().getStringExtra("bookId");if(id==null)return;
            String known=features.countFor(id);
            String message=known==null?"接口原始阅读数据\nread_count: 未提供\nread_count_all: 未提供\n等待该书接口返回；不代表阅读人数为零":known;
            View content=activity.findViewById(android.R.id.content);if(!(content instanceof FrameLayout))return;
            if(countBadge==null){
                countBadge=new TextView(activity);countBadge.setTextSize(11);countBadge.setTextColor(Color.WHITE);countBadge.setBackgroundColor(0xcc263238);
                int pad=Math.round(8*activity.getResources().getDisplayMetrics().density);countBadge.setPadding(pad,pad,pad,pad);
                FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.TOP|Gravity.END);
                params.topMargin=pad*8;params.rightMargin=pad;((FrameLayout)content).addView(countBadge,params);
            }
            countBadge.setOnClickListener(v->new android.app.AlertDialog.Builder(activity).setMessage(message).setPositiveButton("关闭",null).show());
            String shortText=message.split("\n")[1]+"\n"+message.split("\n")[2]+"\n接口数据 · 点此查看口径";
            if(!shortText.contentEquals(countBadge.getText()))countBadge.setText(shortText);
        }
    }

    /** 玻璃模块实例，供书城 Fragment 可见时补扫一次。 */
    private GlassSurfaces glassSurfaces;

    private final class Lifecycle implements Application.ActivityLifecycleCallbacks {
        final Map<Activity,Controller> controllers=new IdentityHashMap<>();
        volatile Activity resumedActivity;
        String lastForegroundClass;
        long readerPausedAt=-1;
        @Override public void onActivityResumed(Activity a){resumedActivity=a;lastForegroundClass=a.getClass().getName();readerPausedAt=-1;if(a.isFinishing())return;Controller c=controllers.get(a);if(c==null){c=new Controller(a);controllers.put(a,c);}c.start();}
        @Override public void onActivityPaused(Activity a){if(resumedActivity==a){if(BookRules.isReaderPage(a.getClass().getName()))readerPausedAt=SystemClock.uptimeMillis();resumedActivity=null;}Controller c=controllers.get(a);if(c!=null)c.stop();}
        @Override public void onActivityDestroyed(Activity a){if(resumedActivity==a)resumedActivity=null;Controller c=controllers.remove(a);if(c!=null)c.stop();if(books!=null)books.release(a);}
        @Override public void onActivityCreated(Activity a,Bundle b){if(liveLaunches!=null)liveLaunches.created(a,lastForegroundClass,readerPausedAt<0?-1:SystemClock.uptimeMillis()-readerPausedAt);}
        @Override public void onActivityStarted(Activity a){}
        @Override public void onActivityStopped(Activity a){}
        @Override public void onActivitySaveInstanceState(Activity a,Bundle b){}
    }
}
