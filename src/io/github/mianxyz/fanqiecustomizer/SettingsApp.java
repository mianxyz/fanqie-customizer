package io.github.mianxyz.fanqiecustomizer;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;
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
                    main.post(() -> { service=bound; sync(); });
                }
                @Override public void onServiceDied(XposedService dead) {
                    main.post(() -> { if(service==dead){service=null;synced=false;status="框架连接已断开。重新打开设置页后重试。";notifyListener();} });
                }
            });
        } catch(Throwable error) {
            // Embedded frameworks do not ship XposedServiceHelper; the file fallback below still works.
        }
        main.post(this::sync);
    }

    public SharedPreferences local() { return getSharedPreferences(Rules.PREFS, MODE_PRIVATE); }
    public String status() { return status; }
    public boolean synced(){return synced;}
    public void setListener(Runnable listener) { this.listener=listener; }
    public void clearListener(Runnable expected){if(listener==expected)listener=null;}
    private void notifyListener() { if(listener!=null)listener.run(); }

    public boolean sync() {
        EmbeddedPrefs.write(this, (key, fallback) -> local().getBoolean(key, fallback));
        if(service==null){synced=false;status="设置已保存到本机（含共享配置后备通道），等待框架连接后同步。";notifyListener();return false;}
        try {
            SharedPreferences.Editor editor=service.getRemotePreferences(Rules.PREFS).edit().clear();
            for(Map.Entry<String,?> entry:local().getAll().entrySet())
                if(PrivacyRules.settingKey(entry.getKey())&&entry.getValue() instanceof Boolean)editor.putBoolean(entry.getKey(),(Boolean)entry.getValue());
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
