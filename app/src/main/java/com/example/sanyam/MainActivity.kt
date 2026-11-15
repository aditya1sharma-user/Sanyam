package com.example.sanyam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        container = LinearLayout(this)
        container.orientation = LinearLayout.VERTICAL
        container.setPadding(48, 96, 48, 96)
        val scroll = ScrollView(this)
        scroll.addView(container)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun prefs() = getSharedPreferences("sanyam", Context.MODE_PRIVATE)
    private fun paused() = System.currentTimeMillis() < prefs().getLong("pausedUntil", 0L)

    private fun render() {
        container.removeAllViews()
        val p = prefs()
        title("Sanyam")
        val left = p.getLong("pausedUntil", 0L) - System.currentTimeMillis()
        val state = if (left > 0) "Paused: about ${left / 60000 + 1} min left"
        else if (p.getBoolean("master", true)) "Protection is ON" else "Protection is OFF"
        text(state, 18f)

        switchRow("Protection", "master")
        button("Pause 10 minutes (solve a puzzle)") {
            challenge {
                prefs().edit().putLong("pausedUntil", System.currentTimeMillis() + 600000L).apply()
            }
        }
        if (left > 0) {
            button("End pause now") {
                prefs().edit().putLong("pausedUntil", 0L).apply()
                render()
            }
        }
        button("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        section("Block short videos")
        for ((label, pkg) in Apps.list) switchRow(label, "block_$pkg")

        section("Daily time limits")
        for ((label, pkg) in Apps.list) limitRow(label, pkg)

        section("Protect Sanyam itself")
        switchRow("Keep me out of Sanyam's Settings pages", "guard")

        section("Tools")
        button("Advanced (view IDs)") { advanced() }
    }

    private fun title(s: String) {
        val t = TextView(this)
        t.text = s
        t.textSize = 30f
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        container.addView(t)
    }

    private fun section(s: String) {
        val t = TextView(this)
        t.text = s
        t.textSize = 20f
        t.setTypeface(null, android.graphics.Typeface.BOLD)
        t.setPadding(0, 64, 0, 16)
        container.addView(t)
    }

    private fun text(s: String, size: Float) {
        val t = TextView(this)
        t.text = s
        t.textSize = size
        t.setPadding(0, 16, 0, 16)
        container.addView(t)
    }

    private fun button(label: String, onClick: () -> Unit) {
        val b = Button(this)
        b.text = label
        b.isAllCaps = false
        b.setOnClickListener { onClick() }
        container.addView(b)
    }

    private fun switchRow(label: String, key: String) {
        val sw = SwitchCompat(this)
        sw.text = label
        sw.textSize = 16f
        sw.setPadding(0, 24, 0, 24)
        sw.isChecked = prefs().getBoolean(key, true)
        sw.setOnCheckedChangeListener { _, checked ->
            if (checked) {
                prefs().edit().putBoolean(key, true).apply()
                render()
            } else {
                challenge { prefs().edit().putBoolean(key, false).apply() }
            }
        }
        container.addView(sw)
    }

    private fun limitRow(label: String, pkg: String) {
        val limit = prefs().getInt("limit_$pkg", 0)
        val usedMin = prefs().getLong("used_$pkg", 0L) / 60000
        val shown = if (limit == 0) "no limit" else "$limit min"
        button("$label: $shown (used today: $usedMin min)") { askLimit(label, pkg, limit) }
    }

    private fun askLimit(label: String, pkg: String, old: Int) {
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        input.hint = "Minutes per day (0 = no limit)"
        AlertDialog.Builder(this)
            .setTitle("$label daily limit")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val n = input.text.toString().trim().toIntOrNull()
                if (n != null) {
                    val weaker = old != 0 && (n == 0 || n > old)
                    val save = { prefs().edit().putInt("limit_$pkg", n).apply() }
                    if (weaker) {
                        challenge { save() }
                    } else {
                        save()
                        render()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun challenge(onSuccess: () -> Unit) {
        if (paused()) {
            onSuccess()
            render()
            return
        }
        val a = Random.nextInt(12, 40)
        val b = Random.nextInt(12, 40)
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle("Solve: $a x $b")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                if (input.text.toString().trim() == (a * b).toString()) {
                    onSuccess()
                } else {
                    Toast.makeText(this, "Wrong answer", Toast.LENGTH_SHORT).show()
                }
                render()
            }
            .setNegativeButton("Cancel") { _, _ -> render() }
            .setOnCancelListener { render() }
            .show()
    }

    private fun advanced() {
        val p = prefs()
        val on = p.getBoolean("discovery", false)
        val set = p.getStringSet("ids", emptySet()) ?: emptySet()
        val items = arrayOf(
            if (on) "Turn discovery OFF" else "Turn discovery ON",
            "Copy found IDs (${set.size})",
            "Clear found IDs"
        )
        AlertDialog.Builder(this)
            .setTitle("Advanced")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> p.edit().putBoolean("discovery", !on).apply()
                    1 -> {
                        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(
                            ClipData.newPlainText("ids", set.sorted().joinToString("\n")))
                        Toast.makeText(this, "Copied ${set.size} IDs", Toast.LENGTH_SHORT).show()
                    }
                    2 -> p.edit().remove("ids").apply()
                }
            }
            .show()
    }
}
