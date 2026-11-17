package com.tgr.keymapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.*
import android.view.*
import android.view.accessibility.AccessibilityEvent
import android.widget.*
import kotlin.math.abs
import kotlin.math.hypot

class LogoView(c: Context) : View(c) {
    var docked = true
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(cv: Canvas) {
        val w = width.toFloat(); val r = w / 2
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(w * 0.35f, w * 0.3f, w, 0xFF3C3C4A.toInt(), 0xFF0A0A10.toInt(), Shader.TileMode.CLAMP)
        cv.drawCircle(r, r, r - 4, p); p.shader = null
        p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.04f; p.color = 0xFFF5C542.toInt()
        cv.drawCircle(r, r, r - w * 0.03f, p)
        cv.save()
        if (docked) { cv.translate(w * 0.5f, w * 0.25f); cv.scale(0.5f, 0.5f) }
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.DEFAULT_BOLD; p.textSize = w * 0.5f
        p.shader = LinearGradient(0f, w * 0.15f, 0f, w * 0.55f, 0xFFFFF3B0.toInt(), 0xFFF5A623.toInt(), Shader.TileMode.CLAMP)
        cv.drawText("T", r, w * 0.52f, p); p.shader = null
        p.color = 0xFFFFD86B.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.03f
        cv.drawRoundRect(w * 0.2f, w * 0.62f, w * 0.58f, w * 0.80f, 6f, 6f, p)
        cv.drawRoundRect(w * 0.65f, w * 0.60f, w * 0.77f, w * 0.82f, w * 0.06f, w * 0.06f, p)
        cv.drawLine(w * 0.71f, w * 0.60f, w * 0.71f, w * 0.70f, p)
        p.style = Paint.Style.FILL
        for (i in 0..3) cv.drawRect(w * (0.24f + i * 0.085f), w * 0.66f, w * (0.28f + i * 0.085f), w * 0.69f, p)
        cv.drawRect(w * 0.27f, w * 0.73f, w * 0.51f, w * 0.76f, p)
        cv.restore()
    }
}

class KeyService : AccessibilityService() {
    private lateinit var wm: WindowManager
    private val maps = HashMap<Int, PointF>()
    private var layer: FrameLayout? = null
    private var logo: LogoView? = null
    private var logoLp: WindowManager.LayoutParams? = null
    private var panel: LinearLayout? = null
    private var mode = 0            // 0 = normal, 1 = create, 2 = edit
    private var pending: PointF? = null
    private var sel: Int? = null
    private var hidden = false
    private var game = ""
    private var size = 170
    private val NF = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
    private val NT = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE

    private fun lp(w: Int, h: Int, flags: Int, x: Int = 0, y: Int = 0) =
        WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, flags, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; this.x = x; this.y = y }

    private fun sp() = getSharedPreferences("tgr", 0)

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        size = (64 * resources.displayMetrics.density).toInt()
        layer = FrameLayout(this).also {
            it.setOnTouchListener { _, e -> layerTouch(e); true }
            wm.addView(it, lp(-1, -1, NF or NT))
        }
        logo = LogoView(this)
        logoLp = lp(size, size, NF, -size / 2, 200)
        var y0 = 0; var d0 = 0f; var moved = false
        logo!!.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { d0 = e.rawY; y0 = logoLp!!.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    val d = e.rawY - d0
                    if (abs(d) > 12) moved = true
                    if (moved) { logoLp!!.y = (y0 + d).toInt().coerceAtLeast(0); wm.updateViewLayout(logo, logoLp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) onLogo()
            }
            true
        }
        wm.addView(logo, logoLp)
        dock(); load(); show(false)
    }

    private fun dock() { logo?.apply { docked = true; alpha = 0.5f; invalidate() }; logoLp?.x = -size / 2; wm.updateViewLayout(logo, logoLp) }
    private fun undock() { logo?.apply { docked = false; alpha = 1f; invalidate() }; logoLp?.x = 0; wm.updateViewLayout(logo, logoLp) }

    private fun layerTouch(e: MotionEvent) {
        if (mode == 1 && e.action == MotionEvent.ACTION_DOWN) {
            pending = PointF(e.rawX, e.rawY); toast("এবার কিবোর্ডের যে বাটন দিতে চান সেটা চাপুন")
        }
        if (mode == 2) when (e.action) {
            MotionEvent.ACTION_DOWN -> {
                val near = maps.entries.minByOrNull { hypot(e.rawX - it.value.x, e.rawY - it.value.y) }
                sel = near?.takeIf { hypot(e.rawX - it.value.x, e.rawY - it.value.y) < 90 }?.key
            }
            MotionEvent.ACTION_MOVE -> sel?.let { maps[it] = PointF(e.rawX, e.rawY); redraw() }
            MotionEvent.ACTION_UP -> { save(); if (sel != null) toast("কী বদলাতে নতুন কী চাপুন, মুছতে Delete চাপুন") }
        }
    }

    private fun setMode(m: Int, msg: String) {
        mode = m; pending = null; sel = null; closePanel()
        wm.updateViewLayout(layer, lp(-1, -1, if (m != 0) NF else NF or NT))
        toast(msg)
    }

    private fun onLogo() {
        if (mode != 0) { setMode(0, "Keymap সেভ হয়েছে"); save(); dock(); return }
        if (panel != null) { closePanel(); dock(); return }
        undock()
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(0xEE222222.toInt())
            fun btn(t: String, a: () -> Unit) = addView(Button(context).apply { text = t; isAllCaps = false; setOnClickListener { a() } })
            btn("Create Keymap") { setMode(1, "স্ক্রিনে বাটনের জায়গায় ট্যাপ করুন, তারপর কিবোর্ডের কী চাপুন। শেষে লোগোতে ট্যাপ") }
            btn("Edit Keymap") { setMode(2, "বাটন টেনে সরান। কী বদলাতে বাটন ধরে নতুন কী চাপুন। শেষে লোগোতে ট্যাপ") }
            btn("Macro") { closePanel(); dock(); toast("Macro পরে আসছে") }
            btn("Hide / Show Buttons") { hidden = !hidden; redraw(); closePanel(); dock() }
            btn("Settings") { closePanel(); dock(); toast("Settings পরে আসছে") }
        }
        wm.addView(panel, lp(-2, -2, NF, size + 10, logoLp!!.y))
    }

    private fun closePanel() { panel?.let { wm.removeView(it) }; panel = null }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    private fun show(v: Boolean) {
        val vis = if (v) View.VISIBLE else View.GONE
        logo?.visibility = vis; layer?.visibility = vis
        if (!v) closePanel()
    }

    private fun load() {
        game = sp().getString("cur", "")!!
        maps.clear()
        sp().getString("map_$game", "")!!.split(";").filter { it.isNotEmpty() }.forEach {
            val s = it.split(","); maps[s[0].toInt()] = PointF(s[1].toFloat(), s[2].toFloat())
        }
        redraw()
    }

    private fun save() {
        sp().edit().putString("map_$game", maps.entries.joinToString(";") { "${it.key},${it.value.x},${it.value.y}" }).apply()
    }

    private fun redraw() {
        val l = layer ?: return
        l.removeAllViews()
        if (hidden) return
        maps.forEach { (code, p) ->
            l.addView(TextView(this).apply {
                text = KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
                gravity = Gravity.CENTER; textSize = 12f; setTextColor(Color.WHITE)
                setBackgroundColor(if (code == sel) 0xCCE5A000.toInt() else 0x99000000.toInt())
            }, FrameLayout.LayoutParams(90, 90).apply { leftMargin = (p.x - 45).toInt(); topMargin = (p.y - 45).toInt() })
        }
    }

    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (mode == 1) {
            val p = pending ?: return false
            if (e.action == KeyEvent.ACTION_DOWN) { maps[e.keyCode] = p; pending = null; save(); redraw() }
            return true
        }
        if (mode == 2) {
            val s = sel ?: return false
            if (e.action == KeyEvent.ACTION_DOWN) {
                val pt = maps.remove(s)
                if (pt != null && e.keyCode != KeyEvent.KEYCODE_DEL && e.keyCode != KeyEvent.KEYCODE_FORWARD_DEL) maps[e.keyCode] = pt
                sel = null; save(); redraw()
            }
            return true
        }
        val p = maps[e.keyCode] ?: return false
        if (e.action == KeyEvent.ACTION_DOWN && e.repeatCount == 0) tap(p)
        return true
    }

    private fun tap(p: PointF) {
        val path = Path().apply { moveTo(p.x, p.y) }
        dispatchGesture(GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 40)).build(), null, null)
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        load()
        show(e.packageName?.toString() == game)
    }

    override fun onInterrupt() {}
}
