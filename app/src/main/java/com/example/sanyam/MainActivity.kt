package com.example.sanyam

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.random.Random

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var idsView: TextView
    private lateinit var discoveryBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(48, 120, 48, 48)

        status = TextView(this)
        status.textSize = 18f
        layout.addView(status)

        addButton(layout, "Pause 10 min (solve math)") { askMath() }
        discoveryBtn = addButton(layout, "") { toggleDiscovery() }
        addButton(layout, "Copy found IDs") { copyIds() }
        addButton(layout, "Clear found IDs") {
            prefs().edit().remove("ids").apply()
            refresh()
        }

        idsView = TextView(this)
        idsView.textSize = 11f
        val scroll = ScrollView(this)
        scroll.addView(idsView)
        layout.addView(scroll)
        setContentView(layout)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun addButton(parent: LinearLayout, label: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = label
        b.setOnClickListener { onClick() }
        parent.addView(b)
        return b
    }

    private fun prefs() = getSharedPreferences("sanyam", Context.MODE_PRIVATE)

    private fun refresh() {
        val remaining = prefs().getLong("pausedUntil", 0L) - System.currentTimeMillis()
        status.text = if (remaining > 0) {
            "Paused: about ${remaining / 60000 + 1} min left"
        } else {
            "Sanyam is guarding"
        }
        val on = prefs().getBoolean("discovery", false)
        discoveryBtn.text = if (on) "Discovery mode: ON (tap to turn off)"
        else "Discovery mode: OFF (tap to turn on)"
        val set = prefs().getStringSet("ids", emptySet()) ?: emptySet()
        idsView.text = "${set.size} IDs\n" + set.sorted().joinToString("\n")
    }

    private fun toggleDiscovery() {
        val on = prefs().getBoolean("discovery", false)
        prefs().edit().putBoolean("discovery", !on).apply()
        refresh()
    }

    private fun copyIds() {
        val set = prefs().getStringSet("ids", emptySet()) ?: emptySet()
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("ids", set.sorted().joinToString("\n")))
        Toast.makeText(this, "Copied ${set.size} IDs", Toast.LENGTH_SHORT).show()
    }

    private fun askMath() {
        val a = Random.nextInt(12, 40)
        val b = Random.nextInt(12, 40)
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this)
            .setTitle("Solve: $a x $b")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ ->
                if (input.text.toString().trim() == (a * b).toString()) {
                    prefs().edit()
                        .putLong("pausedUntil", System.currentTimeMillis() + 600000L).apply()
                    refresh()
                } else {
                    Toast.makeText(this, "Wrong answer", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
