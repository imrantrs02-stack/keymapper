package com.tgr.keymapper

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.hardware.input.InputManager
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.InputDevice
import android.widget.*

class MainActivity : Activity(), InputManager.InputDeviceListener {
    private lateinit var status: TextView
    private lateinit var list: LinearLayout
    private lateinit var acc: Button
    private val sp get() = getSharedPreferences("tgr", 0)

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 100, 40, 40)
            setBackgroundColor(Color.WHITE)
        }
        root.addView(TextView(this).apply { text = "Keymapper Pro"; textSize = 22f; setTextColor(Color.BLACK) })
        status = TextView(this).apply {
            textSize = 16f; setPadding(30, 30, 30, 30)
            setBackgroundColor(0xFF7B61FF.toInt()); setTextColor(Color.WHITE)
        }
        root.addView(status)
        acc = Button(this).apply { setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } }
        root.addView(acc)
        root.addView(Button(this).apply { text = "+ Add Game"; setOnClickListener { addGame() } })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(list) })
        setContentView(root)
        (getSystemService(INPUT_SERVICE) as InputManager).registerInputDeviceListener(this, null)
    }

    override fun onResume() { super.onResume(); refresh() }

    private fun refresh() {
        var kb = false; var ms = false
        for (id in InputDevice.getDeviceIds()) {
            val d = InputDevice.getDevice(id) ?: continue
            if (d.isVirtual) continue
            if (d.supportsSource(InputDevice.SOURCE_MOUSE)) ms = true
            if (d.keyboardType == InputDevice.KEYBOARD_TYPE_ALPHABETIC && d.supportsSource(InputDevice.SOURCE_KEYBOARD)) kb = true
        }
        status.text = "Keyboard: " + (if (kb) "Connected ✅" else "Disconnected ❌") +
            "\nMouse: " + (if (ms) "Connected ✅" else "Disconnected ❌")
        val on = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.contains(packageName) == true
        acc.text = if (on) "Service চালু ✅" else "Accessibility Service চালু করুন (Keymapper Pro)"
        list.removeAllViews()
        sp.getStringSet("games", emptySet())!!.forEach { pkg ->
            val label = try { packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
            val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(0, 20, 0, 20) }
            row.addView(TextView(this).apply { text = label; textSize = 18f; setTextColor(Color.BLACK) },
                LinearLayout.LayoutParams(0, -2, 1f))
            row.addView(Button(this).apply {
                text = "Play"
                setOnClickListener {
                    sp.edit().putString("cur", pkg).apply()
                    packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
                }
            })
            list.addView(row)
        }
    }

    private fun addGame() {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != packageName }
            .sortedBy { it.loadLabel(packageManager).toString() }
        AlertDialog.Builder(this).setTitle("Game বাছুন")
            .setItems(apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()) { _, w ->
                val s = sp.getStringSet("games", emptySet())!!.toMutableSet()
                s.add(apps[w].activityInfo.packageName)
                sp.edit().putStringSet("games", s).apply()
                refresh()
            }.show()
    }

    override fun onInputDeviceAdded(id: Int) = refresh()
    override fun onInputDeviceRemoved(id: Int) = refresh()
    override fun onInputDeviceChanged(id: Int) = refresh()
    override fun onDestroy() {
        (getSystemService(INPUT_SERVICE) as InputManager).unregisterInputDeviceListener(this)
        super.onDestroy()
    }
}
