package io.github.mianxyz.fanqiecustomizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

/**
 * provider 不可用时的第二顺位后备：用有序广播回答宿主进程的开关查询。
 * 与 {@link SettingsProvider} 导出同一份键值，行为保持一致。
 */
public final class SettingsResponder extends BroadcastReceiver {
    public static final String ACTION = Rules.PACKAGE + ".REQUEST_SETTINGS";

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !ACTION.equals(intent.getAction())) return;
        // 同 SettingsProvider：只回真实存过的键，空表表示这里没有配置。
        setResultExtras(SettingsKeys.dump(
            context.getSharedPreferences(Rules.PREFS, Context.MODE_PRIVATE)));
    }
}