package io.github.mianxyz.fanqiecustomizer;
import java.util.concurrent.*;
import java.nio.file.*;
public final class AsyncLogTest {
 public static void main(String[] args)throws Exception{
  AsyncLog.Dispatcher d=new AsyncLog.Dispatcher(256);CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1),done=new CountDownLatch(256);
  try{if(!d.worker.isDaemon())throw new AssertionError("daemon");d.offer(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});if(!entered.await(2,TimeUnit.SECONDS))throw new AssertionError("consumer");
   for(int i=0;i<256;i++)if(!d.offer(done::countDown))throw new AssertionError("capacity "+i);
   FutureTask<Boolean> probe=new FutureTask<>(()->{for(int i=0;i<10000;i++)if(d.offer(()->{}))return false;return true;});Thread producer=new Thread(probe);producer.setDaemon(true);producer.start();if(!probe.get(2,TimeUnit.SECONDS)||d.pending()!=256||d.dropped.get()!=10000)throw new AssertionError("bounded drop");
   if(d.offer(null))throw new AssertionError("null task");AsyncLog.log(null,4,null,null);AsyncLog.log(null,4,null,"");
   release.countDown();if(!done.await(2,TimeUnit.SECONDS))throw new AssertionError("drain");CountDownLatch survived=new CountDownLatch(1);d.offer(()->{throw new RuntimeException("fixture");});d.offer(survived::countDown);if(!survived.await(2,TimeUnit.SECONDS))throw new AssertionError("sink failure recovery");
   Path source=Path.of("src/io/github/mianxyz/fanqiecustomizer");if(Files.exists(source.resolve("StartupPerf.java")))throw new AssertionError("temporary observer removed");
   try(var files=Files.list(source)){for(Path file:files.filter(p->p.toString().endsWith(".java")).toList()){String text=Files.readString(file);if(text.contains("StartupPerf")||text.contains("PERF612"))throw new AssertionError("temporary observer reference: "+file);}}
  }finally{release.countDown();d.worker.interrupt();d.worker.join(2000);}
  System.out.println("AsyncLogTest passed (256 bound, 10000 full-queue drops without consumer progress, null tolerance, daemon, sink exception recovery)");
 }
}
