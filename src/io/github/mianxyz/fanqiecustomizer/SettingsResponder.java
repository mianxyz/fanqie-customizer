package io.github.mianxyz.fanqiecustomizer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

/** Answers the hooked host's ordered broadcast with the current switches. */
public final class SettingsResponder extends BroadcastReceiver {
    public static final String ACTION = "io.github.mianxyz.fanqiecustomizer.REQUEST_SETTINGS";

    @Override public void onReceive(Context context, Intent intent) {
        if (!ACTION.equals(intent.getAction())) return;
        SharedPreferences prefs = context.getSharedPreferences(Rules.PREFS, Context.MODE_PRIVATE);
        Bundle reply = new Bundle();
        for (String key : Rules.allSettingKeys())
            if (PrivacyRules.settingKey(key))
                reply.putBoolean(key, prefs.getBoolean(key, Rules.defaultOn(key)));
        setResultExtras(reply);
    }
}
