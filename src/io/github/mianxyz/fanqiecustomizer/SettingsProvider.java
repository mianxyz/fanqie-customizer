package io.github.mianxyz.fanqiecustomizer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

/**
 * 免 Root 内嵌框架（LSPatch / FPA）使用的同步配置通道。
 *
 * 为什么需要它：真实 LSPosed 环境里，设置页通过 XposedService 把开关写进
 * 远程偏好，Hook 侧用 getRemotePreferences() 读取；内嵌框架没有
 * XposedService，Hook 侧拿到的是空表。有序广播要等 initialize() 之后才回来，
 * 首启那次开关已经决定了装哪些 Hook，只能下次生效；provider 的 call() 在
 * initialize() 继续之前同步返回，所以首启就能拿到当前开关。
 *
 * 只在远程偏好不可用时被查询，LSPosed 安装永远不会调用它。
 */
public final class SettingsProvider extends ContentProvider {
    public static final String AUTHORITY = Rules.PACKAGE + ".settings";
    public static final String METHOD = "settings";

    @Override public boolean onCreate() { return true; }

    @Override public Bundle call(String method, String arg, Bundle extras) {
        if ("rank_image_revision".equals(method)){Bundle b=new Bundle();b.putString("revision",RankImageStore.revision(getContext()));return b;}
        if (!METHOD.equals(method)) return null;
        Context context = getContext();
        if (context == null) return null;
        // 内嵌框架下这里与宿共同一个进程，读到的是番茄自己的 prefs。
        // dump() 只导出确实存过的键，因此它会是空表而不是一份假配置。
        return SettingsKeys.dump(
            context.getSharedPreferences(Rules.PREFS, Context.MODE_PRIVATE));
    }

    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] args, String sortOrder) { return null; }
    @Override public android.os.ParcelFileDescriptor openFile(Uri uri,String mode)throws java.io.FileNotFoundException {
        java.util.List<String> parts=uri.getPathSegments();if(!"r".equals(mode)||parts.size()!=2||!"rank".equals(parts.get(0))||!RankImageStore.validRevision(parts.get(1))||"default".equals(parts.get(1)))throw new java.io.FileNotFoundException("Unknown image");
        return android.os.ParcelFileDescriptor.open(new java.io.File(getContext().getFilesDir(),RankImageStore.fileName(parts.get(1))),android.os.ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}