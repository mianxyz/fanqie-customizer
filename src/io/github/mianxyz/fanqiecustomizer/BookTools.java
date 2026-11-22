package io.github.mianxyz.fanqiecustomizer;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.*;

/** Native navigation and small, optional UI additions. No chapter/reward responses are fabricated. */
final class BookTools {
    interface Host { boolean on(String key); void log(String text); }
    private final XposedModule module;
    private final Host host;
    private final ClassLoader loader;
    private final Map<View,Long> unfoldAttempts=new WeakHashMap<>();
    private final Set<View> pendingUnfold=Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Activity,View> settingEntries=new WeakHashMap<>();
    private final Map<Activity,View> bookBadges=new WeakHashMap<>();
    private final Map<Activity,String> badgeKeys=new WeakHashMap<>();
    BookTools(XposedModule module,Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}

    private final Set<View> unfolded=Collections.newSetFromMap(new WeakHashMap<>());
    void install(){
        if(host.on("auto_expand_shelf")){
            String name="com.dragon.read.component.biz.impl.bookshelf.feed.BSFeedTitleHeaderView";
            try{
                Class<?> type=HostSymbols.type(name,loader);
                for(Method m:type.getDeclaredMethods())if((HostSymbols.matches(m,"a")&&Arrays.equals(m.getParameterTypes(),new Class<?>[]{boolean.class,boolean.class}))||(HostSymbols.matches(m,"b")&&m.getParameterCount()==0)){
                    module.hook(m).intercept(chain->{Object result=chain.proceed();View header=(View)chain.getThisObject();header.post(()->unfold(header));return result;});
                }
                module.hook(type.getDeclaredConstructor(Context.class,android.util.AttributeSet.class)).intercept(chain->{Object result=chain.proceed();View header=(View)chain.getThisObject();header.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){public void onViewAttachedToWindow(View v){v.post(()->unfold(v));}public void onViewDetachedFromWindow(View v){}});return result;});
            }catch(Throwable error){host.log("Bookshelf event hook failed: "+error.getClass().getSimpleName());}
        }
        if(host.on("force_open_removed")){
            install("com.dragon.read.reader.utils.h","b",method->
                method.getParameterCount()==3&&method.getParameterTypes()[1]==Bundle.class,
                (receiver,args)->{
                    if(args[1] instanceof Bundle){Bundle copy=new Bundle((Bundle)args[1]);copy.putBoolean("key_check_book_status",false);args[1]=copy;host.log("Reader entry: local removed-book check skipped");}
                    return false;
                });
        }
        if(host.on("book_id_search")){
            install("com.dragon.read.component.biz.impl.FanqieSearchActivity","A2",method->method.getParameterCount()==1&&method.getReturnType()==void.class,
                (receiver,args)->{
                    if(!(receiver instanceof Activity)||args[0]==null)return false;
                    Object raw=field(args[0],"a");String id=raw instanceof String?BookRules.searchId((String)raw):null;
                    if(id==null)return false;
                    if(open((Activity)receiver,id,host.on("force_open_removed"))){host.log("Numeric book search redirected through native navigation");return true;}
                    return false;
                });
        }
    }
    interface Predicate { boolean matches(Method method); }
    interface Before { boolean apply(Object receiver,Object[] args)throws Throwable; }
    private void install(String name,String method,Predicate filter,Before action){
        int count=0;
        try{
            for(Method m:HostSymbols.type(name,loader).getDeclaredMethods())if(HostSymbols.matches(m,method)&&filter.matches(m)){
                module.hook(m).intercept(chain->{Object[] args=chain.getArgs().toArray();try{if(action.apply(chain.getThisObject(),args))return null;}catch(Throwable error){host.log("Book adaptation error: "+name+"."+method+" "+error.getClass().getSimpleName());}return chain.proceed(args);});count++;
            }
        }catch(Throwable error){host.log("Book hook unavailable: "+name+"."+method+" "+error.getClass().getSimpleName());}
        host.log("Book hooks registered: "+name+"."+method+" = "+count);
    }

    void decorate(Activity activity){
        if(host.on("in_app_entry")&&BookRules.isSettingsPage(HostSymbols.canonical(activity.getClass().getName())))settingsEntry(activity);
        if(host.on("enabled")&&BookRules.isBookPage(HostSymbols.canonical(activity.getClass().getName()))&&(host.on("show_book_id")||host.on("force_open_removed")))bookBadge(activity);
    }
    void release(Activity activity){settingEntries.remove(activity);bookBadges.remove(activity);badgeKeys.remove(activity);}
    private void settingsEntry(Activity activity){
        if(settingEntries.containsKey(activity))return;
        FrameLayout content=content(activity);if(content==null)return;
        Button button=button(activity,"书籍编号工具");
        button.setOnClickListener(v->idDialog(activity));
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(activity,52),Gravity.BOTTOM);
        params.leftMargin=params.rightMargin=dp(activity,16);params.bottomMargin=bottomInset(activity)+dp(activity,12);
        // Reserve space so the new entry does not cover the app's last settings row.
        for(int i=0;i<content.getChildCount();i++){View child=content.getChildAt(i);child.setPadding(child.getPaddingLeft(),child.getPaddingTop(),child.getPaddingRight(),child.getPaddingBottom()+bottomInset(activity)+dp(activity,76));}
        content.addView(button,params);settingEntries.put(activity,button);host.log("In-app book tools entry added");
    }
    private void idDialog(Activity activity){
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(dp(activity,22),dp(activity,8),dp(activity,22),0);
        EditText input=new EditText(activity);input.setHint("输入书籍内部编号");input.setSingleLine();input.setInputType(InputType.TYPE_CLASS_NUMBER);layout.addView(input);
        TextView note=new TextView(activity);note.setText("可打开详情，或使用原生阅读入口尝试阅读。下架后是否还能读，取决于服务器与已有内容。");note.setTextSize(13);note.setPadding(0,dp(activity,12),0,0);layout.addView(note);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("书籍编号直达").setView(layout).setNegativeButton("取消",null).setNeutralButton("打开详情",null).setPositiveButton("尝试阅读",null).create();
        dialog.setOnShowListener(ignored->{
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->{String id=BookRules.bookId(input.getText().toString());if(id==null){input.setError("请输入有效的纯数字编号");return;}if(open(activity,id,false))dialog.dismiss();});
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String id=BookRules.bookId(input.getText().toString());if(id==null){input.setError("请输入有效的纯数字编号");return;}if(open(activity,id,true))dialog.dismiss();});
        });dialog.show();
    }
    boolean open(Activity activity,String raw,boolean reader){
        String id=BookRules.bookId(raw);if(id==null||activity.isFinishing()||activity.isDestroyed())return false;
        try{
            if(reader){
                Class<?> cls=HostSymbols.type("com.dragon.read.reader.utils.ReaderBundleBuilder",loader);
                Constructor<?> ctor=cls.getDeclaredConstructor(Context.class,String.class);ctor.setAccessible(true);Object builder=ctor.newInstance(activity,id);
                Method check=cls.getDeclaredMethod(HostSymbols.methodName(cls,"setCheckBookStatus"),boolean.class);check.setAccessible(true);check.invoke(builder,!(host.on("enabled")&&host.on("force_open_removed")));
                Method start=cls.getDeclaredMethod(HostSymbols.methodName(cls,"openReader"));start.setAccessible(true);start.invoke(builder);
            }else{
                // 用宿主自己的包名，Play 版与国内版是两份独立安装。
                Intent intent=new Intent().setClassName(activity.getPackageName(),HostSymbols.name("com.dragon.read.reader.detail.BookDetailActivity"));intent.putExtra("bookId",id);intent.putExtra("enter_from_tag","fanqie_customizer");activity.startActivity(intent);
            }
            return true;
        }catch(Throwable error){host.log("Book navigation failed: "+error.getClass().getSimpleName());Toast.makeText(activity,"此入口暂时无法打开，请查看命中日志。",Toast.LENGTH_LONG).show();return false;}
    }
    private void bookBadge(Activity activity){
        String id=currentId(activity);if(id==null)return;
        boolean reader=BookRules.isReaderPage(HostSymbols.canonical(activity.getClass().getName()));boolean showId=host.on("show_book_id"),force=!reader&&host.on("force_open_removed");
        if(!showId&&!force)return;
        String key=id+":"+showId+":"+force;if(key.equals(badgeKeys.get(activity)))return;
        FrameLayout parent=content(activity);if(parent==null)return;
        View previous=bookBadges.get(activity);if(previous!=null&&previous.getParent() instanceof ViewGroup)((ViewGroup)previous.getParent()).removeView(previous);
        LinearLayout strip=new LinearLayout(activity);strip.setOrientation(LinearLayout.HORIZONTAL);strip.setGravity(Gravity.CENTER_VERTICAL);strip.setPadding(dp(activity,12),dp(activity,4),dp(activity,8),dp(activity,4));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xeefff4ec);bg.setCornerRadius(dp(activity,18));strip.setBackground(bg);strip.setElevation(dp(activity,2));
        if(showId){TextView label=new TextView(activity);label.setText("书籍编号 · "+id);label.setTextSize(11);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);label.setMaxWidth(Math.max(dp(activity,60),activity.getResources().getDisplayMetrics().widthPixels-dp(activity,force?140:40)));label.setTextColor(Color.rgb(105,57,37));label.setPadding(0,dp(activity,6),dp(activity,8),dp(activity,6));label.setOnClickListener(v->copy(activity,id));label.setOnLongClickListener(v->{copy(activity,id);return true;});strip.addView(label);}
        if(force){TextView action=new TextView(activity);action.setText("尝试阅读  ›");action.setTextColor(Color.rgb(221,76,32));action.setTextSize(13);action.setPadding(dp(activity,8),dp(activity,8),dp(activity,8),dp(activity,8));action.setOnClickListener(v->open(activity,id,true));strip.addView(action);}
        FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,ViewGroup.LayoutParams.WRAP_CONTENT,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);params.bottomMargin=bottomInset(activity)+dp(activity,reader?56:76);
        parent.addView(strip,params);bookBadges.put(activity,strip);badgeKeys.put(activity,key);
    }
    private static void copy(Activity activity,String id){ClipboardManager clipboard=(ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("书籍编号",id));Toast.makeText(activity,"书籍编号已复制",Toast.LENGTH_SHORT).show();}}
    private String currentId(Activity activity){
        try{String id=BookRules.bookId(activity.getIntent().getStringExtra("bookId"));if(id!=null)return id;}catch(Throwable ignored){}
        if(BookRules.isReaderPage(HostSymbols.canonical(activity.getClass().getName())))try{
            Object value=activity.getClass().getMethod(HostSymbols.publicMethodName(activity.getClass(),"getBookId")).invoke(activity);
            return value instanceof String?BookRules.bookId((String)value):null;
        }catch(Throwable ignored){}
        return null;
    }

    void unfold(View header){
        if(!host.on("enabled")||!host.on("auto_expand_shelf")||!header.isShown()||unfolded.contains(header)||pendingUnfold.contains(header))return;
        if(ScrollWorkGate.busy(header)){pendingUnfold.add(header);header.postDelayed(()->{pendingUnfold.remove(header);unfold(header);},200);return;}
        try{
            TextView text=(TextView)field(header,"d");View arrow=(View)field(header,"e");
            if(!BookRules.needsUnfold(text.isShown()&&arrow.isShown(),header.isEnabled(),arrow.getRotation(),text.getText().toString()))return;
            Long last=unfoldAttempts.get(header);if(last!=null&&SystemClock.uptimeMillis()-last<3000)return;
            pendingUnfold.add(header);long readyAt=SystemClock.uptimeMillis();header.post(()->{
                pendingUnfold.remove(header);
                try{
                    TextView label=(TextView)field(header,"d");View icon=(View)field(header,"e");
                    if(!BookRules.needsUnfold(header.isShown()&&label.isShown()&&icon.isShown(),header.isEnabled(),icon.getRotation(),label.getText().toString()))return;
                    // m44.w binds the model's unfold callback to this header itself.
                    View target=header.isClickable()?header:null;
                    if(target==null||!target.isEnabled()){host.log("Bookshelf unfold control has no ready click listener");return;}
                    unfoldAttempts.put(header,SystemClock.uptimeMillis());boolean clicked=target.performClick();if(clicked)unfolded.add(header);host.log("Bookshelf native unfold click: "+clicked+" readyLatencyMs="+(SystemClock.uptimeMillis()-readyAt));
                }catch(Throwable error){host.log("Bookshelf unfold failed: "+error.getClass().getSimpleName());}
            });
        }catch(Throwable error){host.log("Bookshelf header adaptation failed: "+error.getClass().getSimpleName());}
    }
    private static Object field(Object object,String name)throws Throwable {Field f=object.getClass().getField(HostSymbols.publicFieldName(object.getClass(),name));f.setAccessible(true);return f.get(object);}
    private static FrameLayout content(Activity activity){View view=activity.findViewById(android.R.id.content);return view instanceof FrameLayout?(FrameLayout)view:null;}
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static int bottomInset(Activity activity){WindowInsets insets=activity.getWindow().getDecorView().getRootWindowInsets();if(Build.VERSION.SDK_INT>=30&&insets!=null)return insets.getInsets(WindowInsets.Type.systemBars()).bottom;return dp(activity,24);}
    private static Button button(Context context,String title){Button button=new Button(context);button.setText(title);button.setAllCaps(false);button.setTextSize(15);button.setTextColor(Color.WHITE);GradientDrawable background=new GradientDrawable();background.setColor(Color.rgb(236,86,45));background.setCornerRadius(dp(context,16));button.setBackground(background);button.setElevation(dp(context,2));return button;}
}
