package io.github.mianxyz.fanqiecustomizer;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
import java.util.HashMap;
import java.util.Map;

/** Private local settings mirrored into the framework's remote preferences. */
public final class SettingsApp extends Application {
    private XposedService service;
    private Runnable listener;
    private String status = "等待框架连接。请在 LSPosed 中启用本模块，再重新打开设置页。";
    private boolean synced;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onCreate() {
        super.onCreate();
        try {
            XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
                @Override public void onServiceBind(XposedService bound) {
                    main.post(() -> { service=bound; sync(); new Thread(()->{try{publishRankImage();}catch(java.io.IOException e){android.util.Log.w("Pulp","Rank image publish",e);}},"Pulp-rank-publish").start(); });
                }
                @Override public void onServiceDied(XposedService dead) {
                    main.post(() -> { if(service==dead){service=null;synced=false;status="框架连接已断开。重新打开设置页后重试。";notifyListener();} });
                }
            });
        } catch(Throwable ignored) {
            // 内嵌框架没有 XposedServiceHelper；后备通道仍然可用，不在这里中断启动。
        }
        // 首启即写出共享配置，让内嵌框架第一次启动就拿到开关。
        main.post(this::sync);
    }

    /** 当前本地布尔开关快照，后备通道与 sync() 共用。 */
    private Map<String,Boolean> localFlags() {
        Map<String,Boolean> values=new HashMap<>();
        for(Map.Entry<String,?> entry:local().getAll().entrySet())
            if(PrivacyRules.settingKey(entry.getKey())&&entry.getValue() instanceof Boolean)
                values.put(entry.getKey(),(Boolean)entry.getValue());
        return values;
    }

    /** 当前本地整型参数快照（滑块）。越界值按 Rules 的区间钳制。 */
    private Map<String,Integer> localInts() {
        Map<String,Integer> values=new HashMap<>();
        for(Map.Entry<String,?> entry:local().getAll().entrySet()){
            String key=entry.getKey();
            if(!Rules.isIntKey(key))continue;
            Object v=entry.getValue();
            if(v instanceof Integer)values.put(key,SettingsKeys.clamp(key,(Integer)v));
        }
        for(String key:Rules.INT_KEYS)if(!values.containsKey(key))values.put(key,Rules.intDefault(key));
        return values;
    }

    /**
     * 设置页是否运行在宿主进程内部（NPatch 本地模式 / LSPatch / FPA 内嵌）。
     *
     * 判据是本进程的主包名与模块包名是否一致：独立安装时二者相同，
     * 内嵌进番茄时主包名是 com.dragon.read。
     */
    private boolean runningInsideHost() {
        try {
            return !getPackageName().equals(Rules.PACKAGE);
        } catch (Throwable ignored) {
            return false;
        }
    }

    public SharedPreferences local() { return getSharedPreferences(Rules.PREFS, MODE_PRIVATE); }
    public String status() { return status; }
    public boolean synced(){return synced;}
    public void setListener(Runnable listener) { this.listener=listener; }
    public void clearListener(Runnable expected){if(listener==expected)listener=null;}
    private void notifyListener() { if(listener!=null)listener.run(); }

    public synchronized void publishRankImage() throws java.io.IOException {
        if(service==null)return;
        String revision=RankImageStore.revision(this);
        java.util.Set<String> existing=new java.util.HashSet<>(java.util.Arrays.asList(service.listRemoteFiles()));
        if(!revision.equals("default")&&!existing.contains(RankImageStore.fileName(revision))){String name=RankImageStore.fileName(revision);try(java.io.InputStream in=new java.io.FileInputStream(new java.io.File(getFilesDir(),name));java.io.OutputStream out=new android.os.ParcelFileDescriptor.AutoCloseOutputStream(service.openRemoteFile(name))){in.transferTo(out);}}
        String marker="rank-current-"+revision;
        try(java.io.OutputStream out=new android.os.ParcelFileDescriptor.AutoCloseOutputStream(service.openRemoteFile(marker))){out.write(1);}
        for(String name:service.listRemoteFiles())if((name.startsWith("rank-current-")&&!name.equals(marker))||(name.startsWith(RankImageStore.PREFIX)&&!name.equals(RankImageStore.REVISION)&&(revision.equals("default")||!name.equals(RankImageStore.fileName(revision)))))service.deleteRemoteFile(name);
    }
    public boolean sync() {
        // 先落地共享文件：LSPosed 主通道存在时它只是副产品，
        // 内嵌框架下它就是 Host 唯一能拿到的配置来源。
        //
        // 但内嵌框架里设置页与番茄同进程，此时写出去的正是宿主私有目录，
        // 会覆盖 Hook 侧刚读到的那份，只剩主开关的旧值——所以跳过。
        if(!runningInsideHost())EmbeddedPrefs.write(this,localFlags(),localInts());
        if(service==null){synced=false;status="设置已保存到本机，等待框架连接后同步。";notifyListener();return false;}
        try {
            SharedPreferences.Editor editor=service.getRemotePreferences(Rules.PREFS).edit().clear();
            for(Map.Entry<String,Boolean> entry:localFlags().entrySet())
                editor.putBoolean(entry.getKey(),entry.getValue());
            // 滑块参数与布尔开关同批提交：分开提交会让宿主读到半套配置。
            for(Map.Entry<String,Integer> entry:localInts().entrySet())
                editor.putInt(entry.getKey(),entry.getValue());
            if(!editor.commit())throw new IllegalStateException("框架未确认保存");
            status="配置已同步到 LSPosed。修改后请强行停止并重新打开番茄。";
            synced=true;
            notifyListener(); return true;
        } catch(Throwable error) {
            synced=false;
            status="同步失败："+error.getClass().getSimpleName()+"。请重新打开设置页重试。";
            notifyListener();return false;
        }
    }
}
