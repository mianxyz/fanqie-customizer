package io.github.mianxyz.fanqiecustomizer;

import android.app.*;
import android.content.*;
import android.content.pm.PackageInfo;
import android.os.*;
import android.util.Log;
import android.view.*;
import android.widget.*;
import android.graphics.Color;
import java.util.*;
import java.lang.reflect.Method;
import io.github.libxposed.api.XposedModule;

public final class ModuleHook extends XposedModule {
    private static final String BAR="com.dragon.read.widget.BottomTabBarLayout";
    private final Map<String,Boolean> flags=new HashMap<>();
    private final Set<String> logged=java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Map<Class<?>,String> classRules=new HashMap<>();
    private final Map<View,Integer> originalVisibility=new WeakHashMap<>();
    private boolean installed;
    private String processName;
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
        if(!Rules.TARGET.equals(param.getPackageName())||!Rules.TARGET.equals(processName)||!param.isFirstPackage())return;
        try {
            Method attach=Application.class.getDeclaredMethod("attach",Context.class);
            hook(attach).intercept(chain -> {
                Object result=chain.proceed();
                if(!installed) {
                    installed=true;
                    try { initialize((Application)chain.getThisObject(),(Context)chain.getArg(0)); }
                    catch(Throwable error){logMessage("Initialization failed: "+PrivacyRules.errorType(error));}
                }
                return result;
            });
        }catch(Throwable error){logMessage("Application hook failed: "+PrivacyRules.errorType(error));}
    }

    private void initialize(Application app,Context context) throws Throwable {
        PackageInfo info=context.getPackageManager().getPackageInfo(Rules.TARGET,0);
        if(info.getLongVersionCode()!=73932L||!"7.3.9.32".equals(info.versionName)) {
            logMessage("Unsupported version; no adaptation installed.");return;
        }
        Map<String,Boolean> remoteFlags=new HashMap<>();
        try {
            SharedPreferences prefs=getRemotePreferences(Rules.PREFS);
            if(prefs!=null)for(Map.Entry<String,?> entry:prefs.getAll().entrySet())
                if(PrivacyRules.settingKey(entry.getKey())&&entry.getValue() instanceof Boolean)
                    remoteFlags.put(entry.getKey(),(Boolean)entry.getValue());
        }catch(Throwable error){logMessage("Remote preferences unavailable: "+PrivacyRules.errorType(error));}
        if(!remoteFlags.isEmpty())flags.putAll(remoteFlags);
        else {
            Map<String,Boolean> fileFlags=EmbeddedPrefs.read();
            if(fileFlags!=null){flags.putAll(fileFlags);logMessage("Settings loaded from shared file (embedded framework fallback).");}
        }
        requestSettings(context);
        lifecycle=new Lifecycle();app.registerActivityLifecycleCallbacks(lifecycle);
        books=new BookTools(this,new BookTools.Host(){public boolean on(String key){return ModuleHook.this.on(key);}public void log(String message){logOnce(message);}},context.getClassLoader());
        if(!on("enabled")){logMessage("5.0.5 loaded with customizations disabled; in-app book tools entry follows its own switch.");return;}
        books.install();
        NativeUiHooks.Host host=new NativeUiHooks.Host(){
            public boolean on(String key){return ModuleHook.this.on(key);}
            public void log(String text){logOnce(text);}
            public void forceHide(View view,String reason){forcedHidden.put(view,reason);if(Looper.myLooper()==Looper.getMainLooper())hide(view,reason);else new Handler(Looper.getMainLooper()).post(()->hide(view,reason));}
            public void dataChanged(){new Handler(Looper.getMainLooper()).post(()->{for(Controller c:lifecycle.controllers.values())c.schedule();});}
            public void mineRoot(View root){mineRoots.add(root);logOnce("Native mine root registered: "+root.getClass().getName());}
            public void pageShown(String name){logOnce("Visible fragment: "+name);for(Controller c:lifecycle.controllers.values()){c.diagnosed=false;c.resumedAt=SystemClock.uptimeMillis();c.schedule();}}
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
        liveLaunches=new ReaderLiveLaunchHooks(this,host,context.getClassLoader());liveLaunches.install();
        features=new FeatureHooks(this,host,context.getClassLoader());
        features.install();
        NativeUiHooks nativeUi=new NativeUiHooks(this,host,context.getClassLoader());
        nativeUi.countSink=features::recordCount;nativeUi.install();
        if(on("local_vip"))installVip(context.getClassLoader());
        logMessage("5.0.5 modern API 102 loaded for 7.3.9.32.");
    }

    /** Optional fallback: ask the settings app for the latest switches. No-op on LSPosed. */
    private void requestSettings(Context context) {
        try {
            Intent request=new Intent(SettingsResponder.ACTION);
            request.setPackage(Rules.PACKAGE);
            request.setClassName(Rules.PACKAGE, Rules.PACKAGE+".SettingsResponder");
            context.sendOrderedBroadcast(request,null,new BroadcastReceiver(){
                @Override public void onReceive(Context c,Intent i){
                    Bundle extras=getResultExtras(true);
                    if(extras==null)return;
                    Map<String,Boolean> received=new HashMap<>();
                    for(String key:extras.keySet())if(PrivacyRules.settingKey(key))received.put(key,extras.getBoolean(key));
                    if(received.isEmpty())return;
                    flags.clear();flags.putAll(received);
                    EmbeddedPrefs.writeHost(received);
                    logMessage("Settings received from settings app: "+received.size());
                }
            },null,Activity.RESULT_OK,null,null);
        }catch(Throwable error){logMessage("Settings request skipped: "+PrivacyRules.errorType(error));}
    }

    private boolean on(String key){return flags.containsKey(key)?Boolean.TRUE.equals(flags.get(key)):Rules.defaultOn(key);}
    private void logMessage(String message){log(Log.INFO,"FanqieCustomizer",message);}
    private void logOnce(String message){if(on("debug")&&logged.add(message))logMessage(message);}

    private void installVip(ClassLoader loader) {
        try {
            Class<?> cls=Class.forName("com.dragon.read.component.biz.impl.NsVipImpl",false,loader);
            for(String name:new String[]{"isAnyVip","isVip","isSpecificVipOrHigher"}) {
                for(Method method:cls.getDeclaredMethods()) {
                    if(method.getName().equals(name)&&method.getReturnType()==boolean.class) {
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
            rule=Rules.viewRule(c.getName());if(rule!=null)break;
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
            String cls=view.getClass().getName();
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
    private String resourceEntry(View view){try{return view.getResources().getResourceEntryName(view.getId());}catch(Throwable ignored){return null;}}
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
            queued=false;if(!active)return;last=SystemClock.uptimeMillis();
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
