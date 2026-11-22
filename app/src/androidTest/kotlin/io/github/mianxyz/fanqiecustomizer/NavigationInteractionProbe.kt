package io.github.mianxyz.fanqiecustomizer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import io.github.mianxyz.fanqiecustomizer.ui.components.FloatingPillNavBar
import io.github.mianxyz.fanqiecustomizer.ui.components.NavItem
import io.github.mianxyz.fanqiecustomizer.ui.icons.AppIcons
import io.github.mianxyz.fanqiecustomizer.ui.theme.FanqieCustomizerTheme
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Test-only real navigation component: drag/cancel/re-touch plus composition audit markers. */
class NavigationInteractionProbe(private val runner: Instrumentation) {
    private val selected = mutableIntStateOf(1)
    private val motion = mutableStateOf(false)
    private val callbacks = mutableListOf<Pair<Long,Boolean>>()
    private val report = JSONObject()
    private val bar = Rect()
    private var downTime = 0L
    private fun x(slot: Float) = bar.left + bar.width() * (slot + 0.5f) / 4f
    private fun send(action: Int, slot: Float) {
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x(slot), bar.exactCenterY(), 0)
        event.source = InputDevice.SOURCE_TOUCHSCREEN
        try { check(runner.uiAutomation.injectInputEvent(event,true)) } finally { event.recycle() }
    }
    private fun settled(expected: Int? = null) {
        val end = SystemClock.uptimeMillis()+2500
        do {
            var done = false
            runner.runOnMainSync { done = !motion.value && (expected == null || selected.intValue == expected) }
            if (done) return
            SystemClock.sleep(30)
        } while (SystemClock.uptimeMillis()<end)
        error("Navigation did not settle: selected="+selected.intValue+" motion="+motion.value+" expected="+expected)
    }
    private fun tap(slot: Int) {
        downTime=SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN,slot.toFloat())
        SystemClock.sleep(32)
        send(MotionEvent.ACTION_UP,slot.toFloat())
    }
    private fun drag(from: Float,to: Float,cancel: Boolean=false) {
        downTime=SystemClock.uptimeMillis()
        send(MotionEvent.ACTION_DOWN,from)
        for(i in 1..30) {
            SystemClock.sleep(15)
            send(MotionEvent.ACTION_MOVE,from+(to-from)*i/30f)
        }
        send(if(cancel) MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP,to)
    }
    fun run() {
        var activity: SettingsActivity?=null
        var failure: Throwable?=null
        val positioned=CountDownLatch(1)
        try {
            activity=runner.startActivitySync(Intent(runner.targetContext,SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SettingsActivity
            runner.runOnMainSync {
                activity.setContent {
                    FanqieCustomizerTheme(darkTheme=true,glassLevel=GlassLevel.None) {
                        Box(Modifier.fillMaxSize().background(Color(0xff1b1b1b))) {
                            FloatingPillNavBar(
                                listOf(NavItem("仪表盘",AppIcons.Dashboard),NavItem("功能",AppIcons.Tune),
                                    NavItem("主题",AppIcons.Palette),NavItem("关于",AppIcons.Info)),
                                selected.intValue,{ motion.value=true; selected.intValue=it },GlassLevel.None,null,
                                Modifier.align(Alignment.BottomCenter).padding(horizontal=18.dp,vertical=48.dp)
                                    .onGloballyPositioned { c ->
                                        val b=c.boundsInWindow()
                                        bar.set(b.left.toInt(),b.top.toInt(),b.right.toInt(),b.bottom.toInt())
                                        positioned.countDown()
                                    },
                                onMotionChanged={ value ->
                                    motion.value=value
                                    synchronized(callbacks) { callbacks+=System.nanoTime() to value }
                                },
                            )
                        }
                    }
                }
            }
            check(positioned.await(5,TimeUnit.SECONDS)) { "Navigation not positioned" }
            runner.waitForIdleSync()
            SystemClock.sleep(700)
            check(bar.width()>0 && bar.height()>0)
            settled(1)
            Log.i("PulpNavAudit","AUDIT_BEGIN")
            drag(1f,2f)
            SystemClock.sleep(900)
            settled(2)
            Log.i("PulpNavAudit","AUDIT_END")
            report.put("normalDragSelected",selected.intValue)
            drag(2f,1f,cancel=true)
            SystemClock.sleep(900)
            settled(2)
            report.put("cancelPreservesSelection",true)
            tap(3)
            SystemClock.sleep(45)
            downTime=SystemClock.uptimeMillis()
            send(MotionEvent.ACTION_DOWN,2.5f)
            send(MotionEvent.ACTION_MOVE,2.85f)
            runner.waitForIdleSync()
            var intervalStart=0L
            runner.runOnMainSync { check(motion.value);intervalStart=System.nanoTime() }
            for(i in 1..18) {
                SystemClock.sleep(15)
                send(MotionEvent.ACTION_MOVE,2.85f-i*0.025f)
            }
            val intervalEnd=System.nanoTime()
            val stoppedInside=synchronized(callbacks) { callbacks.filter { (time,running) -> time in intervalStart..intervalEnd && !running } }
            send(MotionEvent.ACTION_CANCEL,2.4f)
            SystemClock.sleep(900)
            settled(3)
            report.put("interruptedSpringPrematureStops",stoppedInside.size)
            check(stoppedInside.isEmpty()) { "Old spring ended the new drag motion" }
            tap(0);SystemClock.sleep(35);tap(3);SystemClock.sleep(35);tap(1)
            SystemClock.sleep(1000)
            settled(1)
            report.put("rapidTapsSettle",true)
            report.put("callbacks",JSONArray(synchronized(callbacks) { callbacks.map { (time,value) -> JSONObject().put("time",time).put("motion",value) } }))
        } catch(error:Throwable) {
            failure=error
            report.put("failure",error.stackTraceToString())
        } finally {
            activity?.let { a -> runner.runOnMainSync { a.finish() } }
        }
        File(runner.targetContext.filesDir,"navigation-interaction-probe.json").writeText(report.toString(2))
        runner.finish(if(failure==null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream",failure?.stackTraceToString() ?: "NavigationInteractionProbe: PASS") })
    }
}
