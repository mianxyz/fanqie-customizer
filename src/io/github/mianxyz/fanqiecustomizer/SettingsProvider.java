package io.github.mianxyz.fanqiecustomizer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * Synchronous settings channel for embedded frameworks.
 *
 * An ordered broadcast comes back after initialize() has already decided which
 * hooks to install, so the switches would only take effect on the next start.
 * A provider call returns before initialize() continues, which lets the host
 * use the current switches on the very first start. It is only queried when
 * remote preferences are unavailable; LSPosed installs never call it.
 */
public final class SettingsProvider extends ContentProvider {
    public static final String AUTHORITY = Rules.PACKAGE + ".settings";
    public static final String METHOD = "settings";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD.equals(method)) return null;
        Context context = getContext();
        if (context == null) return null;
        SharedPreferences prefs = context.getSharedPreferences(Rules.PREFS, Context.MODE_PRIVATE);
        Bundle reply = new Bundle();
        for (String key : Rules.allSettingKeys())
            if (PrivacyRules.settingKey(key))
                reply.putBoolean(key, prefs.getBoolean(key, Rules.defaultOn(key)));
        return reply;
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] args, String sortOrder) { return null; }
    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
