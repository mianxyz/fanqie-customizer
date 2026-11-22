package io.github.mianxyz.fanqiecustomizer

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import io.github.mianxyz.fanqiecustomizer.ui.components.LocalEffectsPaused
import io.github.mianxyz.fanqiecustomizer.ui.components.LocalBackdropPaused
import io.github.mianxyz.fanqiecustomizer.ui.components.PillShape
import io.github.mianxyz.fanqiecustomizer.ui.components.liquidGlass
import io.github.mianxyz.fanqiecustomizer.ui.theme.FanqieCustomizerTheme
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.sqrt

/** Test APK only: independent raw control plus nested-page blur, pure-scrim pause and blur resume. */
class GlassRenderingProbe(private val runner: Instrumentation) {
    private data class Measurement(val contrast: Double, val red: Double, val green: Double, val blue: Double) {
        fun json() = JSONObject().put("contrast", contrast).put("red", red).put("green", green).put("blue", blue)
        fun distance(other: Measurement) = (abs(red-other.red)+abs(green-other.green)+abs(blue-other.blue))/3.0
    }

    fun run() {
        val report = JSONObject().put("nestedPageLayers", true).put("independentRawControl", true)
        val results = JSONArray()
        val paused = mutableStateOf(false)
        val redBackground = mutableStateOf(false)
        var activity: SettingsActivity? = null
        var failure: Throwable? = null
        val directory = File(runner.targetContext.filesDir, "glass-probe").apply { mkdirs() }
        try {
            activity = runner.startActivitySync(Intent(runner.targetContext, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as SettingsActivity
            for (level in listOf(GlassLevel.Standard, GlassLevel.Ultra)) {
                val bounds = Rect()
                val positioned = CountDownLatch(1)
                runner.runOnMainSync {
                    paused.value = false
                    redBackground.value = false
                    activity.setContent {
                        FanqieCustomizerTheme(darkTheme = true, glassLevel = level) {
                            val haze = rememberHazeState()
                            Box(Modifier.fillMaxSize().background(Color.White)) {
                                Box(Modifier.fillMaxSize().hazeSource(haze).background(Color.White)) {
                                    Canvas(Modifier.fillMaxSize().graphicsLayer {
                                        compositingStrategy = CompositingStrategy.ModulateAlpha
                                    }) {
                                        if (redBackground.value) {
                                            drawRect(Color.Red)
                                        } else {
                                            val stripe = 8.dp.toPx()
                                            var x = 0f
                                            var black = true
                                            while (x < size.width) {
                                                drawRect(if (black) Color.Black else Color.White,
                                                    topLeft = Offset(x, 0f), size = Size(stripe, size.height))
                                                x += stripe
                                                black = !black
                                            }
                                        }
                                    }
                                }
                                CompositionLocalProvider(
                                    LocalEffectsPaused provides paused.value,
                                    LocalBackdropPaused provides paused.value,
                                ) {
                                    liquidGlass(level, haze, PillShape,
                                        Modifier.align(Alignment.Center).size(280.dp, 100.dp)
                                            .onGloballyPositioned { coordinates ->
                                                val b = coordinates.boundsInWindow()
                                                bounds.set(b.left.toInt(), b.top.toInt(), b.right.toInt(), b.bottom.toInt())
                                                positioned.countDown()
                                            }) { }
                                }
                            }
                        }
                    }
                }
                check(positioned.await(5, TimeUnit.SECONDS)) { "Glass not positioned" }
                runner.waitForIdleSync()
                SystemClock.sleep(900)
                val stable = capture(level.name + "-stable", directory, bounds)
                val raw = capture(level.name + "-raw-control", directory, bounds, bounds.top-60)
                check(raw.contrast > 100.0) { "Independent raw stripe control is missing" }
                check(stable.contrast < raw.contrast * 0.10) { "Stable glass has no real blur" }

                runner.runOnMainSync { paused.value = true }
                runner.waitForIdleSync()
                SystemClock.sleep(250)
                val pausedInitial = capture(level.name + "-paused", directory, bounds)
                runner.runOnMainSync { redBackground.value = true }
                runner.waitForIdleSync()
                SystemClock.sleep(350)
                val pausedChanged = capture(level.name + "-paused-background-changed", directory, bounds)
                val rawChanged = capture(level.name + "-raw-changed-control", directory, bounds, bounds.top-60)
                check(rawChanged.red > 240 && rawChanged.green < 10) { "Background change was not rendered" }
                check(pausedChanged.distance(pausedInitial) > 25.0) { "Paused surface does not use the plain scrim" }
                check(pausedInitial.contrast > 15.0) { "Complex blur remains active while paused" }

                runner.runOnMainSync { paused.value = false }
                runner.waitForIdleSync()
                SystemClock.sleep(900)
                val resumedRed = capture(level.name + "-resumed-red", directory, bounds)
                check(resumedRed.red - resumedRed.green > 30.0) { "Source does not update on resume" }
                runner.runOnMainSync { redBackground.value = false }
                runner.waitForIdleSync()
                SystemClock.sleep(900)
                val resumed = capture(level.name + "-resumed-stripes", directory, bounds)
                check(resumed.contrast < raw.contrast * 0.10) { "Resumed stripes have no real blur" }
                check(resumed.distance(stable) < 5.0) { "Glass does not return to its original appearance" }
                val result = JSONObject().put("level", level.name).put("rawControl", raw.json())
                    .put("stable", stable.json()).put("paused", pausedInitial.json())
                    .put("pausedAfterBackgroundChanged", pausedChanged.json()).put("rawChangedControl", rawChanged.json())
                    .put("resumedRed", resumedRed.json()).put("resumedStripes", resumed.json())
                    .put("bounds", bounds.flattenToString())
                results.put(result)
                runner.sendStatus(0, Bundle().apply { putString("stream", result.toString()) })
            }
        } catch (error: Throwable) {
            failure = error
            report.put("failure", error.stackTraceToString())
        } finally {
            activity?.let { a -> runner.runOnMainSync { a.finish() } }
        }
        report.put("results", results)
        File(directory, "result.json").writeText(report.toString(2))
        runner.finish(if (failure == null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply { putString("stream", failure?.stackTraceToString() ?: "GlassRenderingProbe: PASS") })
    }

    private fun capture(name: String, directory: File, bounds: Rect, row: Int = bounds.centerY()): Measurement {
        val bitmap = checkNotNull(runner.uiAutomation.takeScreenshot()) { "No screenshot" }
        File(directory, name + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val margin = bounds.height()/2
        val pixels = (bounds.left+margin until bounds.right-margin).map { bitmap.getPixel(it, row) }
        bitmap.recycle()
        val reds = pixels.map { (it shr 16 and 255).toDouble() }
        val greens = pixels.map { (it shr 8 and 255).toDouble() }
        val blues = pixels.map { (it and 255).toDouble() }
        val luminance = pixels.indices.map { (reds[it]+greens[it]+blues[it])/3.0 }
        val mean = luminance.average()
        return Measurement(sqrt(luminance.sumOf { (it-mean)*(it-mean) }/luminance.size),
            reds.average(), greens.average(), blues.average())
    }
}