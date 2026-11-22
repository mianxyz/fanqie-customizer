package io.github.mianxyz.fanqiecustomizer

import android.app.Activity
import android.app.Instrumentation
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import io.github.mianxyz.fanqiecustomizer.ui.settings.Catalog
import io.github.mianxyz.fanqiecustomizer.ui.theme.GlassLevel

/** Standalone device regression checks; no AndroidX test runner or JUnit required. */
class ConfigBridgeInstrumentation : Instrumentation() {
    private var navigationProbe = false
    private var referenceProbe = false
    private var glassProbe = false
    private var perfettoProbe = false
    private var dragProbe = false
    private var navInteractionProbe = false
    private var glassSwitchProbe = false
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        navigationProbe = arguments?.getString("navigationProbe") == "true"
        referenceProbe = arguments?.getString("referenceProbe") == "true"
        glassProbe = arguments?.getString("glassProbe") == "true"
        perfettoProbe = arguments?.getString("perfettoProbe") == "true"
        dragProbe = arguments?.getString("dragProbe") == "true"
        navInteractionProbe = arguments?.getString("navInteractionProbe") == "true"
        glassSwitchProbe = arguments?.getString("glassSwitchProbe") == "true"
        start()
    }

    override fun onStart() {
        super.onStart()
        if (glassSwitchProbe) {
            GlassSwitchProbe(this).run()
            return
        }
        if (navInteractionProbe) {
            NavigationInteractionProbe(this).run()
            return
        }
        if (glassProbe) {
            GlassRenderingProbe(this).run()
            return
        }
        if (navigationProbe) {
            NavigationFrameProbe(this, referenceProbe, perfettoProbe, dragProbe).run()
            return
        }
        val report = StringBuilder()
        var failure: Throwable? = null
        sendStatus(0, Bundle().apply {
            putString("stream", "\nConfigBridgeInstrumentation: RUNNING\n")
        })
        try {
            // Catch inside the Runnable: an assertion must not crash the app main thread.
            runOnMainSync {
                try {
                    val prefs = targetContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
                    val original = backup(prefs)
                    try {
                        runChecks(prefs, report)
                    } finally {
                        restore(prefs, original)
                        check(backup(prefs) == original) { "Original preferences were not restored" }
                        report.appendLine("PASS original preferences restored")
                    }
                } catch (error: Throwable) {
                    failure = error
                }
            }
        } catch (error: Throwable) {
            failure = error
        }
        val error = failure
        finish(
            if (error == null) Activity.RESULT_OK else Activity.RESULT_CANCELED,
            Bundle().apply {
                putString("stream", buildString {
                    appendLine()
                    append(report)
                    if (error == null) {
                        appendLine("ConfigBridgeInstrumentation: PASS")
                    } else {
                        appendLine("ConfigBridgeInstrumentation: FAIL")
                        appendLine(error.stackTraceToString())
                    }
                })
                if (error != null) putString("shortMsg", "${error.javaClass.simpleName}: ${error.message}")
            },
        )
    }

    private fun runChecks(prefs: SharedPreferences, report: StringBuilder) {
        val validKeys = Catalog.allToggleKeys.filter { PrivacyRules.settingKey(it) }
        check(validKeys.size == 61) { "Expected 61 valid catalog keys, found ${validKeys.size}" }
        check(validKeys.size == validKeys.distinct().size) { "Duplicate valid catalog keys" }
        check("__launcher_hidden__" !in validKeys && GlassLevel.KEY_GLASS !in validKeys) {
            "Launcher or glass key leaked into the Boolean whitelist"
        }
        check(validKeys.all { Catalog.defaultOn(it) == Rules.defaultOn(it) }) {
            "Catalog defaults differ from Java Rules.defaultOn"
        }
        report.appendLine("PASS 61 unique valid catalog keys and Java default parity")

        check(prefs.edit().clear()
            .putBoolean("enabled", true)
            .putBoolean("in_app_entry", false)
            .putString("debug", "legacy-wrong-type")
            .commit()) { "Fixture preferences commit failed" }
        val bridge = ConfigBridge(targetContext)
        val allKeys = (Catalog.allToggleKeys + ConfigBridge.STARTUP_KEYS).distinct()
        val initial = allKeys.associateWith { key ->
            when (key) {
                "enabled" -> true
                "in_app_entry" -> false
                else -> Catalog.defaultOn(key)
            }
        }
        check(allKeys.associateWith(bridge::get) == initial) {
            "Initialization lost a persisted Boolean, a catalog default, or a startup key"
        }
        check(bridge.glass.value == GlassLevel.None) { "Initial glass default is not None" }
        report.appendLine("PASS prefs.all plus catalog defaults; wrong-type Boolean fallback")

        // Flush state creation before observing any mutation under test.
        Snapshot.sendApplyNotifications()
        val observer = SnapshotStateObserver { callback -> callback() }
        observer.start()
        try {
            assertObservedMutation(observer, "get subscribes to set", { bridge.get("enabled") }, {
                check(bridge.set("enabled", false)) { "Toggle commit failed" }
            }) { value ->
                check(!value && !prefs.getBoolean("enabled", true)) {
                    "Toggle state or persisted value did not update"
                }
            }
            report.appendLine("PASS get subscription invalidated once by set")

            val first = ConfigBridge.STARTUP_KEYS.first()
            val second = ConfigBridge.STARTUP_KEYS.last()
            for (selected in listOf(first, second, null)) {
                assertObservedMutation(observer, "startup $selected", {
                    StartupState(bridge.startupTab(), ConfigBridge.STARTUP_KEYS.map(bridge::get))
                }, {
                    check(bridge.setStartupTab(selected)) { "Startup commit failed" }
                }) { value ->
                    check(value.selected == selected) { "startupTab() has stale selection" }
                    check(value.flags == ConfigBridge.STARTUP_KEYS.map { it == selected }) {
                        "Startup observer saw an intermediate or non-exclusive selection"
                    }
                    check(ConfigBridge.STARTUP_KEYS.all { key ->
                        if (key == selected) prefs.getBoolean(key, false) else !prefs.contains(key)
                    }) { "Startup preferences did not preserve remove-others semantics" }
                }
            }
            report.appendLine("PASS startup first -> last -> automatic: one atomic invalidation each")

            val beforeInvalid = backup(prefs)
            check(!bridge.set("__launcher_hidden__", true)) { "Launcher pseudo-key was accepted" }
            check(!bridge.setStartupTab("invalid_startup_key")) { "Invalid startup key was accepted" }
            check(backup(prefs) == beforeInvalid) { "Invalid key changed persisted preferences" }
            check(!bridge.get("__launcher_hidden__")) { "Launcher pseudo-key changed bridge state" }
            report.appendLine("PASS invalid and launcher pseudo-keys leave preferences unchanged")

            check(bridge.set("enabled", true))
            check(bridge.set("in_app_entry", true))
            check(bridge.set("hide_reader_live_popup", true))
            check(bridge.setStartupTab(first))
            bridge.setGlass(GlassLevel.Ultra)
            check(bridge.glass.value == GlassLevel.Ultra) { "Glass fixture commit failed" }
            Snapshot.sendApplyNotifications()
            val resetDefaults = allKeys.associateWith { key ->
                when (key) {
                    "in_app_entry", "hide_reader_live_popup" -> false
                    else -> Catalog.defaultOn(key)
                }
            }
            assertObservedMutation(observer, "reset defaults", { allKeys.associateWith(bridge::get) }, {
                check(bridge.resetAll()) { "Reset commit failed" }
            }) { values ->
                check(values == resetDefaults) { "Reset observer saw incomplete defaults" }
            }
            check(bridge.startupTab() == null) { "Reset retained a startup selection" }
            check(bridge.glass.value == GlassLevel.None) { "Reset did not reset glass StateFlow" }
            check(backup(prefs) == mapOf("in_app_entry" to false, "hide_reader_live_popup" to false)) {
                "Reset preferences differ from the original two-explicit-false contract"
            }
            val reopened = ConfigBridge(targetContext)
            check(allKeys.associateWith(reopened::get) == resetDefaults) { "Reopened reset state differs" }
            check(reopened.glass.value == GlassLevel.None) { "Reopened glass is not reset" }
            report.appendLine("PASS atomic reset defaults, two explicit false values, glass and reopen")
        } finally {
            observer.stop()
            observer.clear()
        }
    }

    private fun <T> assertObservedMutation(
        observer: SnapshotStateObserver,
        label: String,
        read: () -> T,
        mutate: () -> Unit,
        verify: (T) -> Unit,
    ) {
        val scope = Any()
        val observed = mutableListOf<T>()
        observer.observeReads(scope, { _: Any -> observed.add(read()); Unit }) { read() }
        try {
            mutate()
            Snapshot.sendApplyNotifications()
            check(observed.size == 1) { "$label: expected one invalidation, got ${observed.size}" }
            verify(observed.single())
            verify(read())
        } finally {
            observer.clear(scope)
        }
    }

    private fun backup(prefs: SharedPreferences): Map<String, Any?> =
        prefs.all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }

    private fun restore(prefs: SharedPreferences, values: Map<String, Any?>) {
        val editor = prefs.edit().clear()
        for ((key, value) in values) {
            when (value) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> {
                    check(value.all { it is String }) { "Non-string preference set for $key" }
                    editor.putStringSet(key, value.map { it as String }.toSet())
                }
                else -> error("Unsupported preference type for $key: ${value.javaClass.simpleName}")
            }
        }
        check(editor.commit()) { "Original preferences restore commit failed" }
    }

    private data class StartupState(val selected: String?, val flags: List<Boolean>)
}
