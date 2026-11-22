package io.github.mianxyz.fanqiecustomizer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import io.github.mianxyz.fanqiecustomizer.ui.components.LocalEffectsPaused
import io.github.mianxyz.fanqiecustomizer.ui.components.LocalBackdropPaused
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
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

/** Test APK only: bounded screenshot samples during tier updates and real navigation spring motion. */
class GlassSwitchProbe(private val runner: Instrumentation) {
    fun run() {
        val level=mutableStateOf(GlassLevel.Standard)
        val paused=mutableStateOf(false)
        val selected=mutableIntStateOf(2)
        val bar=Rect()
        val positioned=CountDownLatch(1)
        val frames=JSONArray()
        val report=JSONObject()
        var activity:SettingsActivity?=null
        var failure:Throwable?=null
        var mounts=0
        var disposals=0
        var minimumForeground=Int.MAX_VALUE
        var minimumBarPixels=Int.MAX_VALUE
        val directory=File(runner.targetContext.filesDir,"glass-switch-probe").apply { mkdirs() }
        try {
            activity=runner.startActivitySync(Intent(runner.targetContext,SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SettingsActivity
            runner.runOnMainSync {
                activity.setContent {
                    FanqieCustomizerTheme(darkTheme=true,glassLevel=level.value) {
                        val haze=rememberHazeState()
                        Box(Modifier.fillMaxSize().background(Color(0xff000000))) {
                            Box(Modifier.fillMaxSize().hazeSource(haze).background(Color(0xff000000)))
                            CompositionLocalProvider(
                                LocalEffectsPaused provides paused.value,
                                LocalBackdropPaused provides paused.value,
                            ) {
                            FloatingPillNavBar(
                                listOf(NavItem("仪表盘",AppIcons.Dashboard),NavItem("功能",AppIcons.Tune),
                                    NavItem("主题",AppIcons.Palette),NavItem("关于",AppIcons.Info)),
                                selected.intValue,{ selected.intValue=it },level.value,haze,
                                Modifier.align(Alignment.BottomCenter).padding(horizontal=18.dp,vertical=48.dp)
                                    .onGloballyPositioned { c ->
                                        val b=c.boundsInWindow()
                                        bar.set(b.left.toInt(),b.top.toInt(),b.right.toInt(),b.bottom.toInt())
                                        positioned.countDown()
                                    },
                                onMotionChanged={ paused.value=it },
                            )
                            }
                            DisposableEffect(Unit) { mounts++;onDispose { disposals++ } }
                        }
                    }
                }
            }
            check(positioned.await(5,TimeUnit.SECONDS)) { "Bottom bar did not appear" }
            runner.waitForIdleSync()
            SystemClock.sleep(600)
            val rect=Rect(bar)
            // Outer padding is symmetric: the measured rectangle and icon row share a center.
            val density=runner.targetContext.resources.displayMetrics.density
            val centerY=rect.centerY()
            report.put("barBounds",rect.flattenToString())
            val top=centerY-(22*density).toInt()
            val bottom=centerY+(22*density).toInt()
            val left=(rect.left+24*density).toInt()
            val right=(rect.right-24*density).toInt()
            val image=checkNotNull(runner.uiAutomation.takeScreenshot())
            check(top>=0 && bottom<image.height && left>=0 && right<image.width)
            image.recycle()
            for (step in 0 until 24) {
                runner.runOnMainSync {
                    level.value=if(step%2==0) GlassLevel.Ultra else GlassLevel.Standard
                    if (step>=12) selected.intValue=(selected.intValue+1)%4
                }
                for (sample in 0 until 8) {
                    val bitmap=checkNotNull(runner.uiAutomation.takeScreenshot())
                    var foreground=0
                    var barPixels=0
                    for (y in top until bottom step 2) for (x in left until right step 2) {
                        val pixel=bitmap.getPixel(x,y)
                        val r=(pixel shr 16) and 255
                        val g=(pixel shr 8) and 255
                        val b=pixel and 255
                        if(maxOf(r,g,b)>75) foreground++
                        if(maxOf(r,g,b)>3) barPixels++
                    }
                    minimumForeground=minOf(minimumForeground,foreground)
                    minimumBarPixels=minOf(minimumBarPixels,barPixels)
                    frames.put(JSONObject().put("step",step).put("sample",sample).put("level",level.value.name)
                        .put("foregroundPixels",foreground).put("barPixels",barPixels)
                        .put("paused",paused.value).put("selected",selected.intValue))
                    if(step==0 && sample==0) File(directory,"first-frame.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                    }
                    if(foreground<30 || barPixels<200) {
                        File(directory,"missing-"+step+"-"+sample+".png").outputStream().use {
                            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)
                        }
                    }
                    bitmap.recycle()
                    check(foreground>=30) { "Bottom bar foreground vanished after tier change" }
                    check(barPixels>=200) { "Bottom bar background vanished after tier change" }
                    SystemClock.sleep(8)
                }
            }
            runner.runOnMainSync { check(mounts==1 && disposals==0) { "Theme remounted the navigation tree" } }
            report.put("switches",24).put("navigationTransitions",12).put("samples",frames.length()).put("minimumForegroundPixels",minimumForeground)
                .put("minimumBarPixels",minimumBarPixels).put("mounts",mounts).put("disposalsBeforeFinish",disposals)
        } catch(error:Throwable) {
            failure=error
            report.put("failure",error.stackTraceToString())
        } finally {
            activity?.let { a -> runner.runOnMainSync { a.finish() } }
        }
        report.put("frames",frames)
        File(directory,"result.json").writeText(report.toString(2))
        runner.finish(if(failure==null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream",failure?.stackTraceToString() ?: "GlassSwitchProbe: PASS "+report.toString()) })
    }
}