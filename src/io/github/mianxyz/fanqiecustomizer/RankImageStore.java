package io.github.mianxyz.fanqiecustomizer;
import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.util.AtomicFile;
import java.io.*;
import java.security.MessageDigest;
/** Private image files, never a preference key or a persisted gallery URI. */
public final class RankImageStore {
 public static final String REVISION="rank-image-revision", PREFIX="rank-image-";
 public static final int MAX_EDGE=1440,MAX_BYTES=2097152;
 private RankImageStore(){}
 public static boolean validRevision(String s){return s!=null&&(s.equals("default")||s.matches("[0-9a-f]{64}"));}
 public static String fileName(String revision){if(!validRevision(revision)||revision.equals("default"))throw new IllegalArgumentException("image revision");return PREFIX+revision+".jpg";}
 public static String revision(Context c){try{return new String(java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),REVISION).toPath()),java.nio.charset.StandardCharsets.US_ASCII).trim();}catch(IOException e){return "default";}}
 public static boolean custom(Context c){String r=revision(c);return validRevision(r)&&!r.equals("default");}
 public static int sampleSize(int w,int h){int s=1;while(Math.max(w,h)/s>MAX_EDGE*2)s*=2;return s;}
 public static String save(Context c,Uri uri)throws Exception{
  BitmapFactory.Options opts=new BitmapFactory.Options();opts.inJustDecodeBounds=true;
  try(InputStream in=c.getContentResolver().openInputStream(uri)){BitmapFactory.decodeStream(in,null,opts);}
  if(opts.outWidth<=0||opts.outHeight<=0)throw new IOException("Invalid image");
  opts.inSampleSize=sampleSize(opts.outWidth,opts.outHeight);opts.inJustDecodeBounds=false;opts.inPreferredConfig=Bitmap.Config.ARGB_8888;
  Bitmap decoded;try(InputStream in=c.getContentResolver().openInputStream(uri)){decoded=BitmapFactory.decodeStream(in,null,opts);}if(decoded==null)throw new IOException("Image decode failed");
  Bitmap scaled=decoded;try{float scale=Math.min(1f,MAX_EDGE/(float)Math.max(decoded.getWidth(),decoded.getHeight()));if(scale<1)scaled=Bitmap.createScaledBitmap(decoded,Math.max(1,Math.round(decoded.getWidth()*scale)),Math.max(1,Math.round(decoded.getHeight()*scale)),true);
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();if(!scaled.compress(Bitmap.CompressFormat.JPEG,88,bytes))throw new IOException("Image encode failed");byte[] data=bytes.toByteArray();if(data.length>MAX_BYTES)throw new IOException("Compressed image too large");StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(data))hash.append(String.format(java.util.Locale.ROOT,"%02x",b&255));String revision=hash.toString();atomic(new File(c.getFilesDir(),fileName(revision)),data);setRevision(c,revision);return revision;
  }finally{if(scaled!=decoded)scaled.recycle();decoded.recycle();}
 }
 public static void clear(Context c)throws IOException{setRevision(c,"default");}
 private static void setRevision(Context c,String r)throws IOException{atomic(new File(c.getFilesDir(),REVISION),r.getBytes(java.nio.charset.StandardCharsets.US_ASCII));File[] files=c.getFilesDir().listFiles();if(files!=null)for(File f:files)if(f.getName().startsWith(PREFIX)&&!f.getName().equals(REVISION)&&(r.equals("default")||!f.getName().equals(fileName(r))))f.delete();}
 private static void atomic(File f,byte[] bytes)throws IOException{AtomicFile a=new AtomicFile(f);FileOutputStream out=a.startWrite();try{out.write(bytes);a.finishWrite(out);}catch(IOException e){a.failWrite(out);throw e;}}
}
