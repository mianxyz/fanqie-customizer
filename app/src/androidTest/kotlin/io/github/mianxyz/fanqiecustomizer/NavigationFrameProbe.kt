package io.github.mianxyz.fanqiecustomizer

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.MotionEvent
import android.view.InputDevice
import android.view.Window
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Test APK only: device-local input and native frame metrics, no host input subprocesses. */
class NavigationFrameProbe(
    private val runner: Instrumentation,
    private val referenceProbe: Boolean = false,
    private val perfettoProbe: Boolean = false,
    private val dragProbe: Boolean = false,
) {
    private data class Sample(val intended: Long, val total: Long, val ui: Long, val draw: Long, val layout: Long, val deadline: Long)
    private val samples = mutableListOf<Sample>()
    private val thread = HandlerThread("NavigationFrameProbe")
    private var collecting = false
    private var tracePid: String? = null
    private fun shell(command: String): String =
        android.os.ParcelFileDescriptor.AutoCloseInputStream(runner.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
    private fun stopTrace() {
        tracePid?.let { shell("kill -TERM $it"); tracePid = null; SystemClock.sleep(250) }
    }
    private val cachedNavBounds = mutableMapOf<String, android.graphics.Rect>()
    private val json = JSONObject()
    private val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
        val m = FrameMetrics(metrics)
        val ui = m.getMetric(FrameMetrics.INPUT_HANDLING_DURATION) +
            m.getMetric(FrameMetrics.ANIMATION_DURATION) + m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) +
            m.getMetric(FrameMetrics.DRAW_DURATION)
        synchronized(samples) {
            if (collecting && m.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L) samples += Sample(
                m.getMetric(FrameMetrics.INTENDED_VSYNC_TIMESTAMP), m.getMetric(FrameMetrics.TOTAL_DURATION), ui,
                m.getMetric(FrameMetrics.DRAW_DURATION), m.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION),
                m.getMetric(FrameMetrics.DEADLINE),
            )
        }
    }

    fun run() {
        val prefs = runner.targetContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val hadGlass = prefs.contains("glass_level")
        val glass = prefs.all["glass_level"]
        var activity: Activity? = null
        var failed: Throwable? = null
        thread.start()
        val service = runner.uiAutomation.serviceInfo
        service.flags = service.flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        runner.uiAutomation.serviceInfo = service
        try {
            val tiers = JSONArray()
            json.put("tiers", tiers)
            json.put("referenceProbe", referenceProbe)
            json.put("dragProbe", dragProbe)
            for (tier in if (referenceProbe) listOf(0) else listOf(2, 1)) {
                cachedNavBounds.clear()
                runner.runOnMainSync { check(prefs.edit().putInt("glass_level", tier).commit()) }
                activity = runner.startActivitySync(Intent(runner.targetContext, SettingsActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                if (referenceProbe) runner.runOnMainSync { installReference(activity!!) }
                val window = activity!!.window
                runner.runOnMainSync { window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper)) }
                runner.waitForIdleSync()
                SystemClock.sleep(1200)
                repeat(5) { for (tab in listOf("功能", "主题", "关于")) { tap(tab); SystemClock.sleep(850) } }
                tap("功能")
                SystemClock.sleep(850)
                // 查询控件身份和坐标在采样前完成，避免读屏查询触发主线程工作。
                val tree = checkNotNull(runner.uiAutomation.rootInActiveWindow)
                cachedNavBounds.clear()
                for (label in listOf("仪表盘", "功能", "主题", "关于")) {
                    val node = checkNotNull(search(tree) { it.contentDescription?.toString() == label })
                    cachedNavBounds[label] = android.graphics.Rect().also(node::getBoundsInScreen)
                }
                if (dragProbe) {
                    var previous = "功能"
                    repeat(2) {
                        for (tab in listOf("主题", "关于", "功能")) {
                            drag(previous, tab)
                            SystemClock.sleep(850)
                            assertVisible(when(tab) { "主题" -> "液态玻璃"; "关于" -> "日志与关于"; else -> "界面精简" })
                            previous = tab
                        }
                    }
                }
                var previousTab = "功能"
                val activeWindow = runner.uiAutomation.windows.first { it.isActive }.id
                if (perfettoProbe) {
                    val output = shell("sh /data/local/tmp/pulp-start-trace.sh $tier")
                    tracePid = output.trim().lines().last()
                    check(tracePid!!.matches(Regex("[0-9]+"))) { "Trace did not start: $output" }
                }
                SystemClock.sleep(200)
                synchronized(samples) { samples.clear(); collecting = true }
                val steps = JSONArray()
                repeat(4) { round ->
                    for (tab in listOf("主题", "关于", "功能")) {
                        val active = activeWindow
                        val cleared = runner.uiAutomation.clearWindowContentFrameStats(active)
                        val before = System.nanoTime()
                        val inputUpNanos = if (dragProbe) drag(previousTab, tab) else tap(tab)
                        previousTab = tab
                        SystemClock.sleep(900)
                        val stats = runner.uiAutomation.getWindowContentFrameStats(active)
                        val stamps = if (cleared && stats != null) (0 until stats.frameCount)
                            .map { stats.getFramePresentedTimeNano(it) }.filter { it > before && it < Long.MAX_VALUE }
                            .distinct().sorted() else emptyList()
                        val timeline = JSONArray()
                        if (cleared && stats != null) {
                            for (i in 0 until stats.frameCount) {
                                val presented = stats.getFramePresentedTimeNano(i)
                                if (presented > before && presented < Long.MAX_VALUE) {
                                    timeline.put(JSONObject().put("posted", stats.getFramePostedTimeNano(i))
                                        .put("ready", stats.getFrameReadyTimeNano(i)).put("presented", presented))
                                }
                            }
                        }
                        steps.put(JSONObject().put("round", round).put("tab", tab)
                            .put("frameTimeline", timeline)
                            .put("presentedNanos", JSONArray(stamps)).put("statsAvailable", cleared && stats != null)
                            .put("sampleStartedNanos",before).put("inputUpNanos",inputUpNanos)
                            .put("sampleEndedNanos",System.nanoTime()))
                        assertVisible(when(tab) { "主题" -> "液态玻璃"; "关于" -> "日志与关于"; else -> "界面精简" })
                    }
                }
                val latch = CountDownLatch(1)
                Handler(thread.looper).post { latch.countDown() }
                check(latch.await(5, TimeUnit.SECONDS))
                val captured = synchronized(samples) { collecting = false; samples.toList() }
                stopTrace()
                assertVisible("界面精简")
                check(captured.size > 100) { "Too few native frame samples: "+captured.size }
                val raw = JSONArray()
                captured.forEach { s -> raw.put(JSONObject().put("intended", s.intended).put("total",s.total)
                    .put("ui",s.ui).put("draw",s.draw).put("layout",s.layout).put("deadline",s.deadline)) }
                val data = JSONObject().put("tier",tier).put("steps",steps).put("frames",raw)
                tiers.put(data)
                runner.sendStatus(0, Bundle().apply { putString("stream", "Tier "+tier+": native frames="+captured.size) })
                runner.runOnMainSync { window.removeOnFrameMetricsAvailableListener(listener); activity!!.finish() }
                activity = null
                runner.waitForIdleSync()
            }
            json.put("tiers", tiers)
        } catch (e: Throwable) {
            failed = e
            json.put("failure",e.stackTraceToString())
        } finally {
            stopTrace()
            activity?.let { a -> runner.runOnMainSync { a.window.removeOnFrameMetricsAvailableListener(listener); a.finish() } }
            runner.runOnMainSync {
                val e = prefs.edit()
                if (!hadGlass) e.remove("glass_level") else when(glass) {
                    is Int -> e.putInt("glass_level",glass)
                    is String -> e.putString("glass_level",glass)
                    is Boolean -> e.putBoolean("glass_level",glass)
                    else -> error("Unexpected glass type")
                }
                check(e.commit())
            }
            thread.quitSafely()
        }
        val reportName = if (referenceProbe) "navigation-reference-probe.json" else "navigation-frame-probe.json"
        val report = json.toString(2)
        File(runner.targetContext.filesDir, reportName).writeText(report)
        // Test-owned export for non-debuggable same-signature benchmark targets (run-as is unavailable).
        runner.targetContext.getExternalFilesDir(null)?.let { File(it, reportName).writeText(report) }
        runner.finish(if (failed == null) Activity.RESULT_OK else Activity.RESULT_CANCELED, Bundle().apply {
            putString("stream", if (failed == null) "NavigationFrameProbe: COLLECTED" else failed!!.stackTraceToString())
        })
    }

    /** Same window/display, minimal native controls; test-only rendering baseline, not app UI. */
    private fun installReference(activity: Activity) {
        val density = activity.resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val root = android.widget.FrameLayout(activity).apply {
            setBackgroundColor(0xff1b1b1b.toInt())
        }
        val title = android.widget.TextView(activity).apply {
            text = "界面精简"
            setTextColor(0xffededed.toInt())
            textSize = 30f
        }
        root.addView(title, android.widget.FrameLayout.LayoutParams(-1,dp(100)).apply {
            topMargin = dp(80); leftMargin = dp(18)
        })
        val bar = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setBackgroundColor(0xff212121.toInt())
        }
        root.addView(bar, android.widget.FrameLayout.LayoutParams(-1,dp(62),android.view.Gravity.BOTTOM).apply {
            bottomMargin=dp(40); leftMargin=dp(18); rightMargin=dp(18)
        })
        val names = listOf("仪表盘","功能","主题","关于")
        var current = 1
        var animation: android.animation.Animator? = null
        names.forEachIndexed { index, name ->
            val button = android.widget.TextView(activity).apply {
                val targetButton = this
                text=name; contentDescription=name
                gravity=android.view.Gravity.CENTER
                setTextColor(0xffd9a441.toInt())
                setOnClickListener {
                    if (index == current) return@setOnClickListener
                    val forward = index > current
                    current=index
                    animation?.cancel()
                    title.text = when(index) { 0 -> "Pulp"; 1 -> "界面精简"; 2 -> "液态玻璃"; else -> "日志与关于" }
                    val slide=android.animation.ObjectAnimator.ofFloat(title,android.view.View.TRANSLATION_X,
                        dp(if(forward) 52 else -26).toFloat(),0f).apply { duration=240 }
                    val fade=android.animation.ObjectAnimator.ofFloat(title,android.view.View.ALPHA,0f,1f).apply { duration=180 }
                    val tint=android.animation.ValueAnimator.ofFloat(0f,1f).apply {
                        duration=420
                        addUpdateListener { value -> targetButton.alpha=0.6f+0.4f*(value.animatedValue as Float) }
                    }
                    animation=android.animation.AnimatorSet().apply { playTogether(slide,fade,tint); start() }
                }
            }
            bar.addView(button,android.widget.LinearLayout.LayoutParams(0,-1,1f))
        }
        activity.setContentView(root)
    }

    private fun drag(from: String, to: String): Long {
        val start = checkNotNull(cachedNavBounds[from])
        val end = checkNotNull(cachedNavBounds[to])
        val downTime = SystemClock.uptimeMillis()
        fun send(action: Int, fraction: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action,
                start.exactCenterX() + (end.exactCenterX() - start.exactCenterX()) * fraction,
                start.exactCenterY(), 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            try { check(runner.uiAutomation.injectInputEvent(event, true)) } finally { event.recycle() }
        }
        send(MotionEvent.ACTION_DOWN, 0f)
        for (i in 1..30) {
            SystemClock.sleep(15)
            send(MotionEvent.ACTION_MOVE, i / 30f)
        }
        val inputUpNanos = System.nanoTime()
        send(MotionEvent.ACTION_UP, 1f)
        return inputUpNanos
    }

    private fun tap(label: String): Long {
        val bounds = cachedNavBounds[label] ?: run {
            val root = checkNotNull(runner.uiAutomation.rootInActiveWindow)
            val node = search(root) { it.contentDescription?.toString() == label }
                ?: error("Missing navigation control: "+label)
            android.graphics.Rect().also(node::getBoundsInScreen)
        }
        val now = SystemClock.uptimeMillis()
        var inputUpNanos = 0L
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            if (action == MotionEvent.ACTION_UP) SystemClock.sleep(32)
            val event = MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,bounds.exactCenterX(),bounds.exactCenterY(),0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            if (action == MotionEvent.ACTION_UP) inputUpNanos = System.nanoTime()
            try { check(runner.uiAutomation.injectInputEvent(event,true)) } finally { event.recycle() }
        }
        return inputUpNanos
    }
    private fun assertVisible(text: String) {
        val deadline = SystemClock.uptimeMillis() + 2000
        var visible = emptyList<String>()
        do {
            val root = runner.uiAutomation.rootInActiveWindow
            if (root != null) visible = labels(root)
            if (text in visible) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        error("Navigation did not reach "+text+"; visible="+visible.joinToString(" | "))
    }
    private fun labels(node: AccessibilityNodeInfo): List<String> = buildList {
        if (node.isVisibleToUser && !node.text.isNullOrBlank()) add(node.text.toString())
        for (i in 0 until node.childCount) node.getChild(i)?.let { addAll(labels(it)) }
    }
    private fun search(node: AccessibilityNodeInfo, predicate: (AccessibilityNodeInfo)->Boolean): AccessibilityNodeInfo? {
        if (predicate(node) && node.isVisibleToUser) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            search(child,predicate)?.let { return it }
        }
        return null
    }
}