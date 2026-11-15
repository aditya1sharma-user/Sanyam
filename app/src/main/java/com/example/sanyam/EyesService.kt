package com.example.sanyam

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.time.LocalDate

object Apps {
    val list = listOf(
        "YouTube" to "com.google.android.youtube",
        "Instagram" to "com.instagram.android",
        "Facebook" to "com.facebook.katana",
        "Snapchat" to "com.snapchat.android",
        "TikTok" to "com.zhiliaoapp.musically"
    )
}

class EyesService : AccessibilityService() {

    private class Rule(val pkg: String, val ids: List<String>, val wholeApp: Boolean = false)

    private val rules = listOf(
        Rule("com.google.android.youtube", listOf(
            "com.google.android.youtube:id/reel_recycler",
            "com.google.android.youtube:id/reel_progress_bar",
            "com.google.android.youtube:id/reel_time_bar")),
        Rule("com.instagram.android", listOf(
            "com.instagram.android:id/clips_viewer_view_pager")),
        Rule("com.facebook.katana", listOf()),  // fill after discovery
        Rule("com.snapchat.android", listOf()), // fill after discovery
        Rule("com.zhiliaoapp.musically", listOf(), wholeApp = true)
    )

    private val guardPkgs = setOf(
        "com.android.settings", "com.android.packageinstaller",
        "com.google.android.packageinstaller", "com.oplus.appdetail",
        "com.coloros.safecenter"
    )
    private val keywords = listOf("porn", "xxx", "nude", "sex video") // edit freely
    private val browsers = setOf("com.android.chrome", "org.mozilla.firefox",
        "com.brave.browser", "com.sec.android.app.sbrowser", "com.heytap.browser")

    private var lastAction = 0L
    private var lastScan = 0L

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            try { onTick() } catch (e: Exception) { }
            handler.postDelayed(this, 5000L)
        }
    }

    private fun prefs() = getSharedPreferences("sanyam", Context.MODE_PRIVATE)

    override fun onServiceConnected() {
        handler.removeCallbacks(tick)
        handler.post(tick)
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun onTick() {
        val sp = prefs()
        val now = System.currentTimeMillis()
        if (!sp.getBoolean("master", true) || now < sp.getLong("pausedUntil", 0L)) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) return
        val today = LocalDate.now().toString()
        if (sp.getString("day", "") != today) {
            val e = sp.edit()
            for (k in sp.all.keys) if (k.startsWith("used_")) e.remove(k)
            e.putString("day", today).apply()
        }
        val pkg = rootInActiveWindow?.packageName?.toString() ?: return
        if (Apps.list.none { it.second == pkg }) return
        val used = sp.getLong("used_$pkg", 0L) + 5000L
        sp.edit().putLong("used_$pkg", used).apply()
        if (overLimit(sp, pkg)) performGlobalAction(GLOBAL_ACTION_HOME)
    }

    private fun overLimit(sp: SharedPreferences, pkg: String): Boolean {
        val limit = sp.getInt("limit_$pkg", 0)
        return limit > 0 && sp.getLong("used_$pkg", 0L) >= limit * 60000L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val ev = event ?: return
        val pkg = ev.packageName?.toString() ?: return
        if (pkg == packageName) return
        val sp = prefs()
        if (!sp.getBoolean("master", true)) return
        val now = System.currentTimeMillis()
        if (now < sp.getLong("pausedUntil", 0L)) return

        if (now - lastAction > 1500 && overLimit(sp, pkg)) {
            lastAction = now
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        val rule = rules.find { it.pkg == pkg }
        val guardHere = pkg in guardPkgs && sp.getBoolean("guard", true)
        val typing = ev.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED
        val doTextScan = (typing || pkg in browsers) && now - lastScan > 600
        if (rule == null && !guardHere && !doTextScan) return

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return

        if (guardHere && now - lastAction > 1500 && mentionsSanyam(root)) {
            lastAction = now
            performGlobalAction(GLOBAL_ACTION_HOME)
            return
        }

        if (rule != null && sp.getBoolean("discovery", false)) discover(root, sp)

        if (rule != null && now - lastAction > 1500 && sp.getBoolean("block_$pkg", true)) {
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

    private fun mentionsSanyam(node: AccessibilityNodeInfo): Boolean {
        val t = node.text?.toString()?.lowercase()
        val d = node.contentDescription?.toString()?.lowercase()
        if ((t != null && t.contains("sanyam")) || (d != null && d.contains("sanyam"))) return true
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            if (mentionsSanyam(c)) return true
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

    private fun discover(root: AccessibilityNodeInfo, sp: SharedPreferences) {
        val saved = HashSet<String>(sp.getStringSet("ids", emptySet()) ?: emptySet())
        val before = saved.size
        collect(root, saved)
        if (saved.size > before) sp.edit().putStringSet("ids", saved).apply()
    }

    private fun collect(node: AccessibilityNodeInfo, out: MutableSet<String>) {
        node.viewIdResourceName?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collect(it, out) }
        }
    }

    override fun onInterrupt() {}
}
