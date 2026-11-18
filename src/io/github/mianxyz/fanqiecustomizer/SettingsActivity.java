package io.github.mianxyz.fanqiecustomizer;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native offline settings, grouped into pages, with system light/dark colors. */
public final class SettingsActivity extends Activity {
    private static final String[] PAGES={"主页","书架","阅读","我的","平板","书籍","更多"};
    private static final int ACCENT=Color.rgb(239,91,51);
    private SharedPreferences prefs;
    private SettingsApp app;
    private LinearLayout pageBody,currentCard;
    private TextView status;
    private Switch master,launcherIcon;
    private ScrollView scroll;
    private final Map<String,Switch> controls=new LinkedHashMap<>();
    private final Map<String,TextView> tabs=new LinkedHashMap<>();
    private boolean updating,dark;
    private int background,surface,ink,muted,line;
    private String selected="主页";
    private final Runnable syncListener=this::status;

    @Override public void onCreate(Bundle state){
        dark=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
        setTheme(dark?android.R.style.Theme_Material_NoActionBar:android.R.style.Theme_Material_Light_NoActionBar);
        super.onCreate(state);app=(SettingsApp)getApplication();prefs=app.local();
        background=Color.parseColor(dark?"#151716":"#F5F6F3");surface=Color.parseColor(dark?"#242725":"#FFFFFF");ink=Color.parseColor(dark?"#F2F3EE":"#252C27");muted=Color.parseColor(dark?"#AFB7AE":"#69746A");line=Color.parseColor(dark?"#3A403B":"#EDF0E9");
        if(state!=null&&Arrays.asList(PAGES).contains(state.getString("page")))selected=state.getString("page");
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(background);root.setPadding(dp(18),dp(18),dp(18),dp(8));setContentView(root);
        if(Build.VERSION.SDK_INT>=29)root.setForceDarkAllowed(false);
        root.setOnApplyWindowInsetsListener((view,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());root.setPadding(dp(18),bars.top+dp(12),dp(18),bars.bottom+dp(8));}return insets;});root.requestApplyInsets();
        if(Build.VERSION.SDK_INT>=30&&getWindow().getInsetsController()!=null){int bits=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;getWindow().getInsetsController().setSystemBarsAppearance(dark?0:bits,bits);}
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(20),dp(18),dp(20),dp(18));
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(238,93,55),Color.rgb(243,131,72)});gradient.setCornerRadius(dp(22));hero.setBackground(gradient);root.addView(hero);
        hero.addView(label("番茄界面定制",25,Color.WHITE,true));TextView version=label("7.3.9.32   ·   v5.0.5",12,0xffffeee4,false);version.setPadding(0,dp(6),0,0);hero.addView(version);
        status=label("等待框架连接",12,ink,false);status.setPadding(dp(12),dp(8),dp(12),dp(8));LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(-2,-2);statusParams.topMargin=dp(10);root.addView(status,statusParams);
        status.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("配置同步状态").setMessage(app.status()).setPositiveButton("重新同步",(d,w)->app.sync()).setNegativeButton("关闭",null).show());
        LinearLayout masterCard=new LinearLayout(this);masterCard.setOrientation(LinearLayout.HORIZONTAL);masterCard.setGravity(Gravity.CENTER_VERTICAL);masterCard.setPadding(dp(15),dp(8),dp(12),dp(8));masterCard.setBackground(rounded(surface,16));
        LinearLayout masterText=new LinearLayout(this);masterText.setOrientation(LinearLayout.VERTICAL);masterText.addView(label("启用界面定制",16,ink,true));masterText.addView(label("修改后请强行停止并重新打开番茄",11,muted,false));masterCard.addView(masterText,new LinearLayout.LayoutParams(0,-2,1));
        master=makeSwitch("enabled");master.setContentDescription("启用界面定制");masterCard.addView(master);masterCard.setOnClickListener(v->master.toggle());LinearLayout.LayoutParams masterParams=new LinearLayout.LayoutParams(-1,-2);masterParams.topMargin=dp(10);root.addView(masterCard,masterParams);
        HorizontalScrollView navigation=new HorizontalScrollView(this);navigation.setHorizontalScrollBarEnabled(false);LinearLayout chips=new LinearLayout(this);navigation.addView(chips);
        for(String page:PAGES){TextView chip=label(page,14,muted,true);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(15),dp(10),dp(15),dp(10));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-2,-2);cp.rightMargin=dp(6);chips.addView(chip,cp);chip.setOnClickListener(v->{selected=page;render();});tabs.put(page,chip);}
        LinearLayout.LayoutParams navParams=new LinearLayout.LayoutParams(-1,-2);navParams.topMargin=dp(14);navParams.bottomMargin=dp(6);root.addView(navigation,navParams);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);pageBody=new LinearLayout(this);pageBody.setOrientation(LinearLayout.VERTICAL);pageBody.setPadding(0,dp(5),0,dp(18));scroll.addView(pageBody);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        app.setListener(syncListener);status();render();
    }
    private void render(){
        controls.clear();controls.put("enabled",master);launcherIcon=null;pageBody.removeAllViews();
        for(Map.Entry<String,TextView> tab:tabs.entrySet()){boolean active=tab.getKey().equals(selected);tab.getValue().setTextColor(active?Color.WHITE:muted);tab.getValue().setBackground(rounded(active?ACCENT:surface,14));}
        if("主页".equals(selected))home();else if("书架".equals(selected))shelf();else if("阅读".equals(selected))reading();else if("我的".equals(selected))mine();else if("平板".equals(selected))tablet();else if("书籍".equals(selected))books();else more();scroll.post(()->scroll.scrollTo(0,0));
    }
    private void home(){
        card("底部导航","打开开关即隐藏入口，至少保留一个。");for(int i=0;i<Rules.TAB_KEYS.length;i++)toggle(Rules.TAB_KEYS[i],"隐藏"+Rules.TAB_LABELS[i],null);
        card("默认打开的底部导航","新建主页面时生效；返回已有页面保留当前位置。自动会避开隐藏入口；指定入口被隐藏或不存在时回退到可见入口。");
        String preferred=StartupTabRules.preferred(prefs.getAll()),title="自动（避开隐藏入口）";
        for(int i=0;i<StartupTabRules.TYPES.length;i++)if(StartupTabRules.TYPES[i].equals(preferred))title=Rules.TAB_LABELS[i];
        action("默认打开："+title,this::chooseStartupTab);
        card("书城频道","关闭开关时按应用原配置显示；不会生成未下发的频道。");for(int i=0;i<Rules.CHANNEL_KEYS.length;i++)toggle(Rules.CHANNEL_KEYS[i],"隐藏"+Rules.CHANNEL_LABELS[i],null);
        card("悬浮入口",null);toggle("hide_welfare_float","隐藏福利与任务浮图","覆盖已识别的原生挂件和新版绘制宿主。");toggle("hide_post_float","隐藏发帖快捷入口","书城和我的页面右下角的发帖按钮。");toggle("hide_drama_resume","隐藏短剧继续播放浮窗","隐藏打开应用后首页自动出现的短剧续播挂件。默认关闭，保留手动开启的后台小窗播放。");
    }
    private void chooseStartupTab(){
        String[] labels=new String[Rules.TAB_LABELS.length+1];labels[0]="自动（避开隐藏入口）";
        System.arraycopy(Rules.TAB_LABELS,0,labels,1,Rules.TAB_LABELS.length);
        String preferred=StartupTabRules.preferred(prefs.getAll());int selectedIndex=0;
        for(int i=0;i<StartupTabRules.TYPES.length;i++)if(StartupTabRules.TYPES[i].equals(preferred))selectedIndex=i+1;
        new AlertDialog.Builder(this).setTitle("默认打开的底部导航").setSingleChoiceItems(labels,selectedIndex,(dialog,which)->{
            if(which>0&&prefs.getBoolean(Rules.TAB_KEYS[which-1],false)){Toast.makeText(this,"该入口已隐藏，请先取消隐藏再设为默认。",Toast.LENGTH_LONG).show();return;}
            SharedPreferences.Editor editor=prefs.edit();for(String key:StartupTabRules.KEYS)editor.remove(key);
            if(which>0)editor.putBoolean(StartupTabRules.KEYS[which-1],true);
            if(!editor.commit()){Toast.makeText(this,"保存失败，请重试",Toast.LENGTH_LONG).show();return;}
            app.sync();dialog.dismiss();render();
        }).setNegativeButton("取消",null).show();
    }
    private void shelf(){
        card("书架展示",null);toggle("auto_expand_shelf","自动展开全部","进入书架后执行原生展开动作。已展开的区域不会反复收起。");
        card("书架金币",null);toggle("hide_coin","隐藏金币控件","隐藏已识别的金币栏、金币盒提示与奖励控件。");note("自动展开适用于当前版本的原生折叠书架区域；其他布局可能需要继续适配。");
    }
    private void reading(){
        card("阅读界面",null);toggle("hide_reader_video_entry","隐藏章末免广告入口","隐藏章末“看视频免广告”按钮。");toggle("hide_reader_audio","隐藏快捷听书按钮",null);
        card("广告与游戏",null);toggle("hide_banner","隐藏原生横幅广告","同时取消已适配阅读页的广告占位，防止正文被顶起。");toggle("hide_reader_ads","隐藏阅读广告组件","拦截已适配的阅读整页、插屏、广告弹窗及直播推广。");toggle("hide_reader_live_popup","屏蔽阅读页直播弹窗","需同时开启隐藏阅读广告组件。也会阻止阅读页主动打开的内置直播播放器。");toggle("hide_short_drama_ad","关闭短剧插页广告","关闭阅读过程中插入的短剧广告与章节间短剧推广。独立于“隐藏阅读广告组件”，重启番茄后生效。");toggle("hide_games","隐藏章末小游戏",null);note("广告隐藏覆盖已识别的组件，部分动态页面可能需要继续适配。");
    }
    private void mine(){
        card("收益与游戏",null);toggle("hide_income","隐藏收益记录与收益卡片","可能同时隐藏余额显示区域。");toggle("hide_frequent_games","隐藏常玩游戏","包括侧栏游戏区域和已识别的收益区游戏入口。");
        card("功能入口",null);for(int i=0;i<Rules.MINE_KEYS.length;i++)toggle(Rules.MINE_KEYS[i],"隐藏"+Rules.MINE_LABELS[i],null);
    }
    private void tablet(){
        card("平板专属入口","开关开启即隐藏对应的“我的”菜单项，默认全部关闭。仅在番茄最小布局宽度达到 600dp 的大屏布局生效，手机布局保留原样。");
        for(int i=0;i<TabletRules.KEYS.length;i++)toggle(TabletRules.KEYS[i],"隐藏"+TabletRules.LABELS[i],null);
        note("隐藏只调整菜单展示，不会删除笔记、钱包或其他账号数据。修改后请同步配置，强行停止并重新打开番茄。");
    }
    private void books(){
        card("书籍编号",null);toggle("show_book_id","显示书籍内部编号","在详情页和阅读页显示。点击编号即可复制。");toggle("book_id_search","编号搜索直达","在番茄搜索框提交 16 位及以上纯数字时打开详情；同时开启下架书籍选项时尝试阅读。短编号可使用工具入口。");
        card("下架书籍",null);toggle("force_open_removed","尝试打开下架书籍","跳过阅读入口的本地下架检查。详情页会增加“尝试阅读”按钮。");note("服务器已删除或不再提供的章节无法恢复。开关关闭时保留应用原来的书籍状态检查。");
        card("编号直达工具","番茄 → 我的 → 设置 → 书籍编号工具，可输入编号打开详情或尝试阅读。");action("打开番茄",()->{Intent intent=getPackageManager().getLaunchIntentForPackage(Rules.TARGET);if(intent!=null)startActivity(intent);else Toast.makeText(this,"未找到番茄免费小说",Toast.LENGTH_SHORT).show();});
        card("阅读计数",null);toggle("raw_read_counts","显示接口原始阅读计数","在书籍详情页显示原始计数；这些字段不能证明实时真实人数。");
    }
    private void more(){
        launcherIconSetting();
        card("书籍工具",null);toggle("in_app_entry","在番茄设置中显示书籍编号工具","仅提供编号直达，不包含模块设置。模块配置请从桌面打开。");
        card("应用提示",null);toggle("hide_update_popup","关闭自动更新弹窗","默认关闭。开启后隐藏已适配的普通、灰度和强制升级提示；仍可手动检查更新。");
        card("日志与配置",null);toggle("debug","输出命中日志","日志位于 LSPosed → 日志 → 模块日志，搜索 FanqieCustomizer。");toggle("trace_ab","记录实验配置名称","仅记录已核对的配置名称，不记录未知名称或配置值。请同时开启命中日志。");
        action("查看已发现配置",()->new AlertDialog.Builder(this).setTitle("当前版本的配置线索").setMessage("阅读听书入口：reader_hiden_audio_btn_v561\n书城发帖入口：new_post_bookstore_entrance_config_v717\n书架折叠：bookshelf_unfold_config_v685\n阅读入口状态检查：key_check_book_status\n\n名称来自客户端，不代表服务器已开启相应功能。").setPositiveButton("关闭",null).show());
        card("实验选项",null);toggle("local_vip","本地 VIP 判断 Hook","仅改变本地判断，不会开通服务端会员或恢复不可用内容。");note("免广告送爱发电礼物尚未加入。模块不伪造礼物到账或广告完成结果。");
        card("恢复与帮助",null);action("日志采集步骤",()->new AlertDialog.Builder(this).setTitle("采集命中日志").setMessage(Rules.LOG_GUIDE).setPositiveButton("知道了",null).show());
        action("关闭全部定制",()->new AlertDialog.Builder(this).setTitle("关闭全部定制？").setMessage("将关闭番茄定制选项和书籍编号工具入口，不改变桌面图标设置。重新打开番茄后生效。").setPositiveButton("关闭全部",(d,w)->{if(prefs.edit().clear().putBoolean("in_app_entry",false).putBoolean("hide_reader_live_popup",false).commit()){updating=true;master.setChecked(false);updating=false;app.sync();render();}else Toast.makeText(this,"保存失败，请重试",Toast.LENGTH_LONG).show();}).setNegativeButton("取消",null).show());
        note("适配番茄免费小说 7.3.9.32。模块未添加数据上传功能；完整 LSPosed 日志仍可能含个人信息，分享前请检查。");
    }
    private void launcherIconSetting(){
        card("桌面图标","隐藏后可从 LSPosed → 模块 → 番茄界面定制 → 设置进入本页，关闭开关恢复图标。此项立即生效，不影响模块功能。");
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(11),0,dp(11));
        row.addView(label("隐藏桌面图标",15,ink,false),new LinearLayout.LayoutParams(0,-2,1));
        launcherIcon=new Switch(this);launcherIcon.setSaveEnabled(false);launcherIcon.setPadding(dp(8),0,0,0);launcherIcon.setContentDescription("隐藏桌面图标");
        int[][] states={new int[]{android.R.attr.state_checked},new int[]{}};launcherIcon.setThumbTintList(new ColorStateList(states,new int[]{ACCENT,dark?0xffaab2a9:0xffe0e5dc}));launcherIcon.setTrackTintList(new ColorStateList(states,new int[]{dark?0xff784331:0xffffc7b6,dark?0xff505950:0xffd0d8cd}));
        row.addView(launcherIcon);currentCard.addView(row);refreshLauncherIcon();
        launcherIcon.setOnCheckedChangeListener((button,checked)->{
            if(updating)return;
            try{if(!LauncherVisibility.setHidden(this,checked))Toast.makeText(this,"系统未确认桌面图标更改，请重试。",Toast.LENGTH_LONG).show();}
            catch(RuntimeException error){Toast.makeText(this,"图标设置失败："+PrivacyRules.errorType(error),Toast.LENGTH_LONG).show();}
            refreshLauncherIcon();
        });
        row.setOnClickListener(v->{if(launcherIcon.isEnabled())launcherIcon.toggle();});
    }
    private void refreshLauncherIcon(){
        if(launcherIcon==null)return;
        updating=true;
        try{launcherIcon.setChecked(LauncherVisibility.isHidden(this));launcherIcon.setEnabled(true);}
        catch(RuntimeException error){launcherIcon.setEnabled(false);Toast.makeText(this,"无法读取桌面图标状态："+PrivacyRules.errorType(error),Toast.LENGTH_LONG).show();}
        finally{updating=false;}
    }
    private void card(String title,String description){
        currentCard=new LinearLayout(this);currentCard.setOrientation(LinearLayout.VERTICAL);currentCard.setPadding(dp(16),dp(15),dp(16),dp(8));currentCard.setBackground(rounded(surface,18));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=dp(12);pageBody.addView(currentCard,params);
        currentCard.addView(label(title,18,ink,true));if(description!=null){TextView text=label(description,12,muted,false);text.setPadding(0,dp(7),0,dp(8));currentCard.addView(text);}
    }
    private void toggle(String key,String title,String description){
        View divider=new View(this);divider.setBackgroundColor(line);LinearLayout.LayoutParams div=new LinearLayout.LayoutParams(-1,dp(1));div.topMargin=dp(8);currentCard.addView(divider,div);
        LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(11),0,dp(11));LinearLayout text=new LinearLayout(this);text.setOrientation(LinearLayout.VERTICAL);text.addView(label(title,15,ink,false));
        if(description!=null){TextView detail=label(description,11,muted,false);detail.setPadding(0,dp(5),dp(8),0);text.addView(detail);}row.addView(text,new LinearLayout.LayoutParams(0,-2,1));Switch control=makeSwitch(key);control.setContentDescription(title);row.addView(control);currentCard.addView(row);row.setOnClickListener(v->control.toggle());
    }
    private Switch makeSwitch(String key){
        Switch control=new Switch(this);control.setSaveEnabled(false);control.setPadding(dp(8),0,0,0);control.setChecked(prefs.getBoolean(key,Rules.defaultOn(key)));controls.put(key,control);
        int[][] states={new int[]{android.R.attr.state_checked},new int[]{}};control.setThumbTintList(new ColorStateList(states,new int[]{ACCENT,dark?0xffaab2a9:0xffe0e5dc}));control.setTrackTintList(new ColorStateList(states,new int[]{dark?0xff784331:0xffffc7b6,dark?0xff505950:0xffd0d8cd}));
        control.setOnCheckedChangeListener((button,checked)->{
            if(updating)return;Map<String,Object> next=new HashMap<>(prefs.getAll());next.put(key,checked);String problem=Rules.allTabsHidden(next)?"至少保留一个底部入口":Rules.allChannelsHidden(next)?"至少保留一个书城频道":null;
            if(problem!=null){updating=true;control.setChecked(!checked);updating=false;Toast.makeText(this,problem,Toast.LENGTH_SHORT).show();return;}
            if(!prefs.edit().putBoolean(key,checked).commit()){updating=true;control.setChecked(!checked);updating=false;Toast.makeText(this,"保存失败，请重试",Toast.LENGTH_LONG).show();return;}app.sync();
        });return control;
    }
    private void action(String title,Runnable callback){Button button=new Button(this);button.setAllCaps(false);button.setText(title);button.setTextSize(14);button.setTextColor(ACCENT);button.setBackground(rounded(dark?0xff3b302a:0xfffff1e8,12));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,dp(46));params.topMargin=dp(10);params.bottomMargin=dp(6);currentCard.addView(button,params);button.setOnClickListener(v->callback.run());}
    private void note(String message){TextView text=label(message,12,muted,false);text.setPadding(dp(6),0,dp(6),dp(15));pageBody.addView(text);}
    private TextView label(String message,int size,int color,boolean bold){TextView text=new TextView(this);text.setText(message);text.setTextSize(size);text.setTextColor(color);text.setIncludeFontPadding(false);text.setLineSpacing(dp(3),1);if(bold)text.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return text;}
    private GradientDrawable rounded(int color,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(radius));return drawable;}
    private void status(){if(status==null)return;status.setText(app.synced()?"● 配置已同步 · 点击查看":"● 等待同步 · 点击查看");status.setTextColor(app.synced()?(dark?0xff9ad4aa:0xff2a7648):(dark?0xffffcd91:0xff95622f));status.setBackground(rounded(app.synced()?(dark?0xff293d2d:0xffe7f2e7):(dark?0xff423426:0xfff8eddc),12));}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    @Override public void onResume(){super.onResume();if(app!=null)app.sync();refreshLauncherIcon();}
    @Override public void onSaveInstanceState(Bundle state){state.putString("page",selected);super.onSaveInstanceState(state);}
    @Override public void onDestroy(){if(app!=null)app.clearListener(syncListener);super.onDestroy();}
}
