package io.github.mianxyz.fanqiecustomizer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import io.github.libxposed.api.XposedModule;
final class AsyncLog {
 static final class Dispatcher {
  private final ArrayBlockingQueue<Runnable> queue;
  final AtomicLong dropped=new AtomicLong(); final Thread worker;
  Dispatcher(int capacity){queue=new ArrayBlockingQueue<>(capacity);worker=new Thread(()->{while(!Thread.currentThread().isInterrupted())try{Runnable task=queue.take();try{task.run();}catch(Throwable ignored){}}catch(InterruptedException e){Thread.currentThread().interrupt();}},"Pulp-async-log");worker.setDaemon(true);worker.start();}
  boolean offer(Runnable task){if(task==null)return false;boolean accepted=queue.offer(task);if(!accepted)dropped.incrementAndGet();return accepted;}
  int pending(){return queue.size();}
 }
 private static final Dispatcher dispatcher=new Dispatcher(256);
 static void log(XposedModule module,int priority,String tag,String message){if(module==null||message==null||message.isEmpty())return;dispatcher.offer(()->module.log(priority,tag==null?"FanqieCustomizer":tag,message));}
 static long dropped(){return dispatcher.dropped.get();}
}
