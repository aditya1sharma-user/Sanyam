package com.example.sanyam

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class EyesService : AccessibilityService() {

    private class Rule(val pkg: String, val ids: List<String>, val wholeApp: Boolean = false)

    private val rules = listOf(
        Rule("com.google.android.youtube", listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_progress_bar",
            "com.google.android.youtube:id/reel_time_bar")),
        Rule("com.instagram.android", listOf(
            "com.instagram.android:id/clips_viewer_view_pager")), // guess: verify with discovery
        Rule("com.facebook.katana", listOf()),  // fill after discovery
        Rule("com.snapchat.android", listOf()), // fill after discovery
        Rule("com.zhiliaoapp.musically", listOf(), wholeApp = true)
    )

    private val keywords = listOf("porn", "xxx", "nude", "sex video") // edit freely
    private val browsers = setOf("com.android.chrome", "org.mozilla.firefox",
        "com.brave.browser", "com.sec.android.app.sbrowser", "com.heytap.browser")

    private var lastAction = 0L
    private var lastScan = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val pkg = event?.packageName?.toString() ?: return
        if (pkg == packageName) return
        val now = System.currentTimeMillis()
        val prefs = getSharedPreferences("sanyam", Context.MODE_PRIVATE)
        if (now < prefs.getLong("pausedUntil", 0L)) return

        val rule = rules.find { it.pkg == pkg }
        val doTextScan = now - lastScan > 600
        if (rule == null && !doTextScan) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        if (rule != null && prefs.getBoolean("discovery", false)) {
            discover(root, prefs)
        }

        if (rule != null && now - lastAction > 1500) {
            val hit = rule.wholeApp || rule.ids.any { id ->
                root.findAccessibilityNodeInfosByViewId(id).any { it.isVisibleToUser }
            }
            if (hit) {
                lastAction = now
                leave(rule, root)
                return
            }
        }

        if (doTextScan) {
            lastScan = now
            if (scanText(root, pkg in browsers)) {
                lastAction = now
                performGlobalAction(GLOBAL_ACTION_BACK)
            }
        }
    }

    private fun leave(rule: Rule, root: AccessibilityNodeInfo) {
        if (rule.wholeApp) {
            performGlobalAction(GLOBAL_ACTION_HOME)
        } else if (rule.pkg == "com.google.android.youtube" || rule.pkg == "com.instagram.android") {
            if (!clickTab(root, "Home")) performGlobalAction(GLOBAL_ACTION_BACK)
        } else {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    private fun clickTab(root: AccessibilityNodeInfo, name: String): Boolean {
        for (n in root.findAccessibilityNodeInfosByText(name)) {
            val label = n.contentDescription?.toString() ?: n.text?.toString()
            if (label != name) continue
            var node: AccessibilityNodeInfo? = n
            while (node != null) {
                if (node.isClickable) {
                    return node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                }
                node = node.parent
            }
        }
        return false
    }

    private fun scanText(node: AccessibilityNodeInfo, checkAll: Boolean): Boolean {
        val text = node.text?.toString()?.lowercase()
        if (text != null && keywords.any { text.contains(it) }) {
            if (node.isEditable) {
                val args = Bundle()
                args.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
                return true
            } else if (checkAll) {
                return true
            }
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scanText(child, checkAll)) return true
        }
        return false
    }

    private fun discover(root: AccessibilityNodeInfo, prefs: SharedPreferences) {
        val saved = HashSet<String>(prefs.getStringSet("ids", emptySet()) ?: emptySet())
        val before = saved.size
        collect(root, saved)
        if (saved.size > before) prefs.edit().putStringSet("ids", saved).apply()
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableSet<String>) {
        node.viewIdResourceName?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, out) }
        }
    }

    override fun onInterrupt() {}
}
