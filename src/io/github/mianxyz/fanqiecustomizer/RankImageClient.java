package io.github.mianxyz.fanqiecustomizer;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import java.io.*;
import io.github.libxposed.api.XposedModule;
/** One asynchronous revision check per resume; image bytes only when content changes. */
final class RankImageClient {
 static volatile Bitmap bitmap;static volatile String revision="default";private static boolean loading;
 static void refresh(XposedModule module,Application app){synchronized(RankImageClient.class){if(loading)return;loading=true;}new Thread(()->{try{
  String next;boolean remote=true;
  // listRemoteFiles is metadata-only: unchanged images incur no image file reads.
  String[] names;try{names=module.listRemoteFiles();}catch(Throwable e){names=null;}
  next=null;if(names!=null)for(String n:names)if(n.startsWith("rank-current-")){String r=n.substring(13);if(RankImageStore.validRevision(r))next=r;}
  if(next==null){remote=false;Bundle b=app.getContentResolver().call(android.net.Uri.parse("content://"+SettingsProvider.AUTHORITY),"rank_image_revision",null,null);next=b==null?"default":b.getString("revision","default");}
  if(!RankImageStore.validRevision(next)||next.equals(revision))return;
  Bitmap decoded=null;if(!next.equals("default")){ParcelFileDescriptor fd=remote?module.openRemoteFile(RankImageStore.fileName(next)):app.getContentResolver().openFileDescriptor(android.net.Uri.parse("content://"+SettingsProvider.AUTHORITY+"/rank/"+next),"r");if(fd==null)throw new IOException("No image descriptor");try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){byte[] bytes=in.readNBytes(RankImageStore.MAX_BYTES+1);if(bytes.length>RankImageStore.MAX_BYTES)throw new IOException("Image size");java.security.MessageDigest digest=java.security.MessageDigest.getInstance("SHA-256");StringBuilder hash=new StringBuilder();for(byte b:digest.digest(bytes))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));if(!next.equals(hash.toString()))throw new IOException("Image hash mismatch");BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(o.outWidth<=0||o.outHeight<=0||Math.max(o.outWidth,o.outHeight)>RankImageStore.MAX_EDGE)throw new IOException("Image dimensions");o.inJustDecodeBounds=false;decoded=BitmapFactory.decodeByteArray(bytes,0,bytes.length,o);if(decoded==null)throw new IOException("Image decode");}}
  final Bitmap image=decoded;final String accepted=next;new Handler(Looper.getMainLooper()).post(()->{bitmap=image;revision=accepted;NativeCoverGlass.imageChanged();module.log(android.util.Log.INFO,"FanqieCustomizer","Rank image updated revision="+accepted);});
 }catch(Throwable e){module.log(android.util.Log.WARN,"FanqieCustomizer","Rank image read: "+e.getClass().getSimpleName());}finally{synchronized(RankImageClient.class){loading=false;}}},"Pulp-rank-image").start();}
}
