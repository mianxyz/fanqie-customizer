package io.github.mianxyz.fanqiecustomizer;
import android.app.*;
import android.content.*;
import android.view.*;
import java.lang.reflect.Method;
import java.util.*;
import io.github.libxposed.api.XposedModule;
/** Gesture-only confirmation. Programmatic refresh calls always pass through. */
final class RefreshConfirm {
    private static final String HOST="com.dragon.read.widget.refresh.SuperSwipeRefreshLayout";
    private final XposedModule module;private final FeatureHooks.Host host;private final ClassLoader loader;
    private final Set<Object> dialogs=Collections.newSetFromMap(new WeakHashMap<Object,Boolean>());
    RefreshConfirm(XposedModule module,FeatureHooks.Host host,ClassLoader loader){this.module=module;this.host=host;this.loader=loader;}
    void install(){int hits=0;try{Class<?> cls=Class.forName(HOST,false,loader);
        Method finish=cls.getDeclaredMethod("d"); finish.setAccessible(true);
        Method reset=cls.getDeclaredMethod("b",String.class);reset.setAccessible(true);
        for(Method method:cls.getDeclaredMethods()){
            Class<?>[] p=method.getParameterTypes();if(!method.getName().equals("i")||p.length!=4||p[0]!=boolean.class||p[1]!=String.class||p[2]!=int.class)continue;
            method.setAccessible(true);
            module.hook(method).intercept(chain->{
                Object self=chain.getThisObject();
                if(!host.on("enabled")||!host.on("refresh_confirm")||!Boolean.TRUE.equals(chain.getArgs().get(0))||!fromGesture()||!(self instanceof View)||!isBookmall((View)self))return chain.proceed();
                View view=(View)self;Activity activity=activity(view.getContext());
                if(activity==null||activity.isFinishing()||activity.isDestroyed())return chain.proceed();
                Object[] args=chain.getArgs().toArray();
                long resetAt=android.os.SystemClock.uptimeMillis();
                // b(String) is the host's existing finishSpinner below-threshold return path.
                try{reset.invoke(self,(Object)null);}catch(Throwable error){host.log("Refresh dialog reset FAILED: "+error.getClass().getSimpleName());return chain.proceed();}
                if(dialogs.add(self))view.post(()->{
                    if(activity.isFinishing()||activity.isDestroyed()){dialogs.remove(self);return;}
                    try{
                        Dialog dialog=createDialog(activity,()->{host.log("Refresh dialog: cancelled");},()->view.postDelayed(()->{if(!view.isAttachedToWindow()||activity.isFinishing())return;try{method.invoke(self,args);host.log("Refresh dialog: confirmed");}catch(Throwable error){host.log("Refresh dialog replay FAILED: "+error.getClass().getSimpleName());}},Math.max(0,450-(android.os.SystemClock.uptimeMillis()-resetAt))));
                        dialog.setOnDismissListener(d->dialogs.remove(self));dialog.show();
                        dialog.getWindow().setLayout(Math.min(dp(activity,320),activity.getResources().getDisplayMetrics().widthPixels-dp(activity,40)),ViewGroup.LayoutParams.WRAP_CONTENT);
                        host.log("Refresh dialog: shown");
                    }catch(Throwable error){dialogs.remove(self);host.log("Refresh dialog FAILED: "+error.getClass().getSimpleName());}
                });
                return null;
            });hits++;
        }
    }catch(Throwable error){host.log("Refresh dialog hooks FAILED: "+error.getClass().getSimpleName());}
    host.log("Refresh dialog hooks: "+hits+" gesture=d->i reset=b(String)");}
    private int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
    private android.graphics.drawable.GradientDrawable shape(int color,int stroke,Context c,int radius){android.graphics.drawable.GradientDrawable g=new android.graphics.drawable.GradientDrawable();g.setColor(color);g.setCornerRadius(dp(c,radius));if(stroke!=0)g.setStroke(dp(c,1),stroke);return g;}
    private Dialog createDialog(Activity a,Runnable cancel,Runnable confirm){
        boolean night=(a.getResources().getConfiguration().uiMode&48)==32;
        try{night=Boolean.TRUE.equals(Class.forName("com.dragon.read.base.skin.SkinManager",false,loader).getMethod("isNightMode").invoke(null));}catch(Throwable ignored){}
        Dialog d=new Dialog(a);d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        android.widget.LinearLayout card=new android.widget.LinearLayout(a);card.setOrientation(1);card.setPadding(dp(a,24),dp(a,24),dp(a,24),dp(a,24));card.setBackground(shape(night?0xF51E2026:0xFCFFFFFF,night?0x22FFFFFF:0x14000000,a,24));card.setElevation(dp(a,6));
        android.widget.TextView title=new android.widget.TextView(a);title.setText("确认刷新书城？");title.setTextSize(17);title.setTypeface(android.graphics.Typeface.DEFAULT,1);title.setTextColor(night?0xFFF5F6FA:0xFF202228);card.addView(title);
        android.widget.TextView sub=new android.widget.TextView(a);sub.setText("刷新后将重新加载推荐流内容");sub.setTextSize(13.5f);sub.setTextColor(night?0xFFADB2BF:0xFF747983);sub.setPadding(0,dp(a,10),0,dp(a,24));card.addView(sub);
        android.widget.LinearLayout row=new android.widget.LinearLayout(a);card.addView(row);
        String[] names={"取消刷新","确定刷新"};for(int j=0;j<2;j++){final int index=j;android.widget.TextView button=new android.widget.TextView(a);button.setText(names[j]);button.setTextSize(14);button.setTypeface(android.graphics.Typeface.DEFAULT,1);button.setGravity(android.view.Gravity.CENTER);button.setTextColor(j==1?0xFFFFFFFF:night?0xFFE8EBF2:0xFF454A55);
            int bg=j==1?0xFFFF5F38:night?0xFF2C303A:0xFFF1F2F5;button.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x22FFFFFF),shape(bg,0,a,14),shape(0xFFFFFFFF,0,a,14)));android.widget.LinearLayout.LayoutParams lp=new android.widget.LinearLayout.LayoutParams(0,dp(a,44),1);if(j==0)lp.rightMargin=dp(a,12);row.addView(button,lp);button.setOnClickListener(v->{d.dismiss();if(index==0)cancel.run();else confirm.run();});}
        d.setContentView(card);Window w=d.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams lp=w.getAttributes();lp.dimAmount=0.35f;w.setAttributes(lp);}
        GlobalGlassSurfaces.decorateDialog(a,card);
        return d;
    }
    private boolean fromGesture(){for(StackTraceElement e:Thread.currentThread().getStackTrace())if(HOST.equals(e.getClassName())&&"d".equals(e.getMethodName()))return true;return false;}
    private boolean isBookmall(View v){for(View p=v;p!=null;p=p.getParent() instanceof View?(View)p.getParent():null)if(p.getClass().getName().equals("com.dragon.read.component.biz.impl.bookmall.widge.BookMallCoordinatorLayout"))return true;return false;}
    private Activity activity(Context c){for(int i=0;c!=null&&i<12;i++){if(c instanceof Activity)return (Activity)c;if(!(c instanceof ContextWrapper))break;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}return null;}
}
