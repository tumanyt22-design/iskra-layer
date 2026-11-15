package com.iskra.layer

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import java.io.File
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class LayerOverlay(private val ctx: Context) {

    private class Tap(val v: View, val pad: Int, val run: () -> Unit)
    private class BarParts(val bar: LinearLayout, val fill: View)

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val ui = Handler(Looper.getMainLooper())
    private val density = ctx.resources.displayMetrics.density
    private fun dp(v: Int): Int = (v * density).toInt()

    // Моды читаются один раз при создании панели
    private val mods: ModState = Mods.load(ctx)
    private fun hid(key: String): Boolean = mods.hidden.contains(key)

    // Блок показывается, если включён в редакторе и не скрыт модом
    private fun blockVisible(id: String): Boolean {
        if (!PanelLayout.isOn(ctx, id)) return false
        return id == "buttons" || !hid(id)
    }

    private val sizeIdx = LayerPrefs.getInt(ctx, "size", 1)
    private val posIdx = LayerPrefs.getInt(ctx, "pos", 1)
    private val fixedColor: Int? = mods.accent ?: when (LayerPrefs.getInt(ctx, "color", 0)) {
        1 -> 0xFFFFC107.toInt()
        2 -> 0xFF00E5FF.toInt()
        3 -> 0xFFFF4081.toInt()
        4 -> 0xFF69F0AE.toInt()
        5 -> 0xFFEEEEEE.toInt()
        else -> null
    }

    private val panelW = dp(when (sizeIdx) { 0 -> 230; 2 -> 300; else -> 260 })
    private val handleW = dp(22)
    private val closedH = dp(130)
    private val grey = 0xFF9297A1.toInt()
    private val dark = 0xFF1B1D22.toInt()

    private var root: FrameLayout? = null
    private var content: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var eyes: EyesView? = null
    private var batFill: View? = null
    private var volFill: View? = null
    private var volBar: View? = null
    private var brightFill: View? = null
    private var brightBar: View? = null
    private var timeView: TextView? = null
    private var dateView: TextView? = null
    private var batteryView: TextView? = null
    private var weatherView: TextView? = null
    private var volView: TextView? = null
    private var brightView: TextView? = null
    private var messageView: TextView? = null
    private var musicBox: LinearLayout? = null
    private var musicTitle: TextView? = null
    private var playChip: TextView? = null
    private var torchChip: TextView? = null
    private var silentChip: TextView? = null
    private var torchBg: GradientDrawable? = null
    private var silentBg: GradientDrawable? = null
    private var musicCtrl: MediaController? = null

    private val targets = ArrayList<Tap>()
    private var isOpen = false
    private var expanded = false
    private var gestureDone = false
    private var sliderDrag = false
    private var brightDrag = false
    private var torchOn = false
    private var silentMode = false
    private var downX = 0f
    private var downY = 0f
    private var lastWeather = 0L

    private var mood = Mood.HAPPY
    private var accent = fixedColor ?: Mood.HAPPY.color
    private var accentAnim: ValueAnimator? = null
    private val toggles = ArrayList<Long>()
    private var surpriseUntil = 0L
    private var batteryPct = 100
    private var charging = false
    private var batteryKnown = false

    private val pillBg = GradientDrawable()
    private val haloBg = GradientDrawable()
    private val batFillBg = GradientDrawable()
    private val volFillBg = GradientDrawable()
    private val brightFillBg = GradientDrawable()
    private val lineBg = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0, 0, 0))
    private val sepBg = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0, 0, 0))

    private val ticker = object : Runnable {
        override fun run() { updateClock(); updateBattery(); ui.postDelayed(this, 10000L) }
    }
    private val musicRun = object : Runnable {
        override fun run() {
            if (!isOpen) return
            pollMusic(); updateVolume(); updateBrightness(); ui.postDelayed(this, 2000L)
        }
    }

    fun show() {
        if (root != null) return
        val frame = build()
        val p = makeParams()
        try { wm.addView(frame, p) } catch (e: Exception) { e.printStackTrace(); return }
        root = frame; params = p; expanded = false
        updateClock(); updateBattery(); ui.postDelayed(ticker, 10000L)
    }

    fun hide() {
        ui.removeCallbacksAndMessages(null); accentAnim?.cancel(); content?.animate()?.cancel()
        val r = root; if (r != null) { try { wm.removeView(r) } catch (_: Exception) {} }
        root = null; content = null; params = null; eyes = null; batFill = null; volFill = null; volBar = null
        brightFill = null; brightBar = null; messageView = null; musicBox = null; targets.clear()
        isOpen = false; expanded = false
    }

    private fun makeParams(): WindowManager.LayoutParams {
        val p = WindowManager.LayoutParams(handleW, closedH, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH, PixelFormat.TRANSLUCENT)
        when (posIdx) {
            0 -> { p.gravity = Gravity.END or Gravity.TOP; p.y = dp(140) }
            2 -> { p.gravity = Gravity.END or Gravity.BOTTOM; p.y = dp(140) }
            else -> { p.gravity = Gravity.END or Gravity.CENTER_VERTICAL }
        }
        return p
    }

    private fun label(text: String, sp: Float, color: Int, bold: Boolean): TextView {
        val t = TextView(ctx); t.text = text; t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(color)
        if (bold) t.typeface = Typeface.DEFAULT_BOLD; return t
    }

    private fun valueLabel(): TextView {
        val t = label("…", 13f, Color.WHITE, false); t.maxLines = 1; t.ellipsize = TextUtils.TruncateAt.END; return t
    }

    private fun chip(text: String, sp: Float): TextView {
        val t = TextView(ctx); t.text = text; t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); t.setTextColor(Color.WHITE)
        t.gravity = Gravity.CENTER
        val bg = GradientDrawable(); bg.setColor(dark); bg.cornerRadius = dp(14).toFloat(); t.background = bg
        return t
    }

    private fun cell(h: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(0, h, 1f); lp.leftMargin = dp(3); lp.rightMargin = dp(3); return lp
    }

    private fun margin(top: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top); return lp
    }

    private fun infoRow(title: String, value: TextView): LinearLayout {
        val r = LinearLayout(ctx); r.orientation = LinearLayout.HORIZONTAL; r.gravity = Gravity.CENTER_VERTICAL
        r.addView(label(title, 12f, grey, false), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(value, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return r
    }

    private fun makeBar(fillBg: GradientDrawable): BarParts {
        val track = GradientDrawable(); track.setColor(0xFF23262C.toInt()); track.cornerRadius = dp(3).toFloat()
        fillBg.cornerRadius = dp(3).toFloat()
        val bar = LinearLayout(ctx); bar.orientation = LinearLayout.HORIZONTAL; bar.weightSum = 100f; bar.background = track
        val fill = View(ctx); fill.background = fillBg
        bar.addView(fill, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 0f))
        return BarParts(bar, fill)
    }

    private fun setFill(f: View?, pct: Float) {
        if (f == null) return
        val lp = f.layoutParams as LinearLayout.LayoutParams
        lp.weight = pct; f.layoutParams = lp
    }

    private fun barLp(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(5))
        lp.topMargin = dp(8)
        return lp
    }

    // Действия кнопок из модов
    private fun runModAction(b: ModButton) {
        when (b.action) {
            "torch" -> toggleTorch()
            "silent" -> toggleSilent()
            "camera" -> launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA))
            "settings" -> launch(Intent(Settings.ACTION_SETTINGS))
            "wifi" -> launch(Intent(Settings.ACTION_WIFI_SETTINGS))
            "bluetooth" -> launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            "launch" -> {
                val li = ctx.packageManager.getLaunchIntentForPackage(b.arg)
                if (li != null) launch(li) else say("Нет приложения " + b.arg)
            }
            "url" -> {
                try { launch(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(b.arg))) } catch (_: Exception) {}
            }
            "say" -> say(b.arg)
            else -> say("Неизвестное действие")
        }
    }

    // ---------- блоки панели ----------

    private fun makeEyes(): EyesView {
        val ev = EyesView(ctx)
        ev.setActive(false)
        eyes = ev
        targets.add(Tap(ev, dp(8)) { poke() })
        return ev
    }

    private fun buildClock(): LinearLayout {
        val tv = label("--:--", 38f, Color.WHITE, true)
        val dv = label("", 12f, grey, false)
        timeView = tv; dateView = dv
        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
        col.addView(tv); col.addView(dv)
        return col
    }

    // Часы и глаза рядом в одной строке (как в старой панели)
    private fun buildHead(): LinearLayout {
        val timeCol = buildClock()
        val ev = makeEyes()
        val head = LinearLayout(ctx); head.orientation = LinearLayout.HORIZONTAL; head.gravity = Gravity.CENTER_VERTICAL
        head.addView(timeCol, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(ev, LinearLayout.LayoutParams(dp(56), dp(32)))
        return head
    }

    private fun buildEyesRow(): FrameLayout {
        val ev = makeEyes()
        val f = FrameLayout(ctx)
        f.addView(ev, FrameLayout.LayoutParams(dp(84), dp(48), Gravity.CENTER))
        return f
    }

    private fun buildMusic(): LinearLayout {
        val mTitle = label("", 13f, Color.WHITE, false); mTitle.maxLines = 1; mTitle.ellipsize = TextUtils.TruncateAt.END
        val prev = chip("⏮", 17f); val play = chip("▶", 17f); val next = chip("⏭", 17f)
        val ctl = LinearLayout(ctx); ctl.orientation = LinearLayout.HORIZONTAL
        ctl.addView(prev, cell(dp(38))); ctl.addView(play, cell(dp(38))); ctl.addView(next, cell(dp(38)))
        val mBox = LinearLayout(ctx); mBox.orientation = LinearLayout.VERTICAL; mBox.visibility = View.GONE
        mBox.addView(mTitle); mBox.addView(ctl, margin(8))
        musicBox = mBox; musicTitle = mTitle; playChip = play
        targets.add(Tap(prev, 0) { musicCmd(-1) }); targets.add(Tap(play, 0) { musicCmd(0) }); targets.add(Tap(next, 0) { musicCmd(1) })
        return mBox
    }

    private fun buildBattery(): LinearLayout {
        val bv = valueLabel(); batteryView = bv
        val parts = makeBar(batFillBg); batFill = parts.fill
        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
        col.addView(infoRow("Батарея", bv))
        col.addView(parts.bar, barLp())
        return col
    }

    private fun buildWeather(): LinearLayout {
        val wv = valueLabel(); weatherView = wv
        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
        col.addView(infoRow("Погода", wv))
        return col
    }

    private fun buildVolume(): LinearLayout {
        val vv = valueLabel(); volView = vv
        val parts = makeBar(volFillBg); volFill = parts.fill; volBar = parts.bar
        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
        col.addView(infoRow("Громкость", vv))
        col.addView(parts.bar, barLp())
        return col
    }

    private fun buildBrightness(): LinearLayout {
        val brv = valueLabel(); brightView = brv
        val parts = makeBar(brightFillBg); brightFill = parts.fill; brightBar = parts.bar
        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL
        col.addView(infoRow("Яркость", brv))
        col.addView(parts.bar, barLp())
        return col
    }

    private fun buildButtons(): View? {
        val showDefaults = !hid("buttons")
        if (!showDefaults && mods.buttons.isEmpty()) return null
        val col = LinearLayout(ctx); col.orientation = LinearLayout.VERTICAL

        if (showDefaults) {
            val torch = chip("🔦", 18f); val cam = chip("📷", 18f); val cfg = chip("⚙", 18f); val silent = chip("🔔", 18f)
            torchChip = torch; torchBg = torch.background as GradientDrawable
            silentChip = silent; silentBg = silent.background as GradientDrawable
            val actions = LinearLayout(ctx); actions.orientation = LinearLayout.HORIZONTAL
            actions.addView(torch, cell(dp(40))); actions.addView(silent, cell(dp(40)))
            actions.addView(cam, cell(dp(40))); actions.addView(cfg, cell(dp(40)))
            targets.add(Tap(torch, 0) { toggleTorch() }); targets.add(Tap(silent, 0) { toggleSilent() })
            targets.add(Tap(cam, 0) { launch(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) })
            targets.add(Tap(cfg, 0) { launch(Intent(Settings.ACTION_SETTINGS)) })
            col.addView(actions)
        }

        if (mods.buttons.isNotEmpty()) {
            val extra = LinearLayout(ctx); extra.orientation = LinearLayout.VERTICAL
            var rowL = LinearLayout(ctx)
            for ((idx, mb) in mods.buttons.withIndex()) {
                if (idx % 4 == 0) {
                    rowL = LinearLayout(ctx); rowL.orientation = LinearLayout.HORIZONTAL
                    extra.addView(rowL, margin(if (idx == 0) 0 else 6))
                }
                val cv = chip(mb.icon, 18f)
                rowL.addView(cv, cell(dp(40)))
                targets.add(Tap(cv, 0) { runModAction(mb) })
            }
            val rem = mods.buttons.size % 4
            if (rem != 0) { for (k in rem until 4) rowL.addView(View(ctx), cell(dp(40))) }
            col.addView(extra, margin(if (showDefaults) 8 else 0))
        }
        return col
    }

    private fun buildBlock(id: String): View? {
        return when (id) {
            "clock" -> buildClock()
            "eyes" -> buildEyesRow()
            "music" -> buildMusic()
            "battery" -> buildBattery()
            "weather" -> buildWeather()
            "volume" -> buildVolume()
            "brightness" -> buildBrightness()
            "buttons" -> buildButtons()
            else -> null
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun build(): FrameLayout {
        targets.clear()
        val mv = label("", 12f, grey, false); mv.visibility = View.GONE; messageView = mv

        val panel = LinearLayout(ctx); panel.orientation = LinearLayout.VERTICAL
        panel.setPadding(dp(20), dp(20), dp(14), dp(18))
        val r = dp(22).toFloat()
        val panelBg = GradientDrawable(); panelBg.setColor(mods.bgColor ?: 0xF20E1013.toInt())
        panelBg.cornerRadii = floatArrayOf(r, r, 0f, 0f, 0f, 0f, r, r); panel.background = panelBg

        // Собираем блоки в порядке из редактора
        val ids = ArrayList<String>()
        for (id in PanelLayout.order(ctx)) { if (blockVisible(id)) ids.add(id) }

        var first = true
        var i = 0
        while (i < ids.size) {
            val id = ids[i]
            val v: View? = if (id == "clock" && i + 1 < ids.size && ids[i + 1] == "eyes") { i++; buildHead() } else buildBlock(id)
            i++
            if (v != null) {
                panel.addView(v, margin(if (first) 0 else 14))
                if (first) {
                    val sep = View(ctx); sep.background = sepBg
                    val sepLp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                    sepLp.topMargin = dp(14); sepLp.bottomMargin = dp(10)
                    panel.addView(sep, sepLp)
                    panel.addView(mv, margin(4))
                    first = false
                }
            }
        }
        if (first) panel.addView(mv, margin(0))

        val handle = FrameLayout(ctx)
        val line = View(ctx); line.background = lineBg
        handle.addView(line, FrameLayout.LayoutParams(dp(2), FrameLayout.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL))
        val halo = View(ctx); halo.background = haloBg; handle.addView(halo, FrameLayout.LayoutParams(dp(10), dp(64), Gravity.CENTER))
        val pill = View(ctx); pill.background = pillBg; handle.addView(pill, FrameLayout.LayoutParams(dp(4), dp(40), Gravity.CENTER))
        pillBg.cornerRadius = dp(2).toFloat(); haloBg.cornerRadius = dp(5).toFloat()

        val row = LinearLayout(ctx); row.orientation = LinearLayout.HORIZONTAL
        row.addView(panel, LinearLayout.LayoutParams(panelW, LinearLayout.LayoutParams.WRAP_CONTENT))
        row.addView(handle, LinearLayout.LayoutParams(handleW, LinearLayout.LayoutParams.MATCH_PARENT))
        content = row

        val frame = FrameLayout(ctx)
        frame.addView(row, FrameLayout.LayoutParams(panelW + handleW, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))
        frame.setOnTouchListener { _, e -> onTouch(e) }

        if (Build.VERSION.SDK_INT >= 29) {
            frame.addOnLayoutChangeListener { v, left, top, right, bottom, _, _, _, _ ->
                try {
                    val h = minOf(bottom - top, dp(200)); val y = ((bottom - top) - h) / 2
                    v.systemGestureExclusionRects = listOf(Rect(0, y, right - left, y + h))
                } catch (_: Exception) {}
            }
        }
        applyAccent(); updateVolume(); updateBrightness()
        return frame
    }

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

    private fun applyAccent() {
        pillBg.setColor(accent); haloBg.setColor(withAlpha(accent, 0x33))
        batFillBg.setColor(accent); volFillBg.setColor(accent); brightFillBg.setColor(accent)
        lineBg.setColors(intArrayOf(withAlpha(accent, 0), withAlpha(accent, 0x99), withAlpha(accent, 0)))
        sepBg.setColors(intArrayOf(withAlpha(accent, 0), accent, withAlpha(accent, 0)))
        val tb = torchBg; if (tb != null) tb.setColor(if (torchOn) accent else dark)
        val sb = silentBg; if (sb != null) sb.setColor(if (silentMode) accent else dark)
        torchChip?.setTextColor(if (torchOn) 0xFF111111.toInt() else Color.WHITE)
        silentChip?.setTextColor(if (silentMode) 0xFF111111.toInt() else Color.WHITE)
        eyes?.setLook(mood, accent)
    }

    private fun setMood(m: Mood) {
        if (m == mood) return
        mood = m; val target = fixedColor ?: m.color; accentAnim?.cancel()
        if (target != accent) {
            val a = ValueAnimator.ofArgb(accent, target); a.duration = 400L
            a.addUpdateListener { anim -> accent = anim.animatedValue as Int; applyAccent() }
            a.start(); accentAnim = a
        }
        applyAccent()
    }

    private fun say(text: String) {
        if (hid("message")) return
        val mv = messageView ?: return
        mv.text = text; mv.visibility = View.VISIBLE; mv.alpha = 0f
        mv.animate().alpha(1f).setDuration(300L).start()
        ui.postDelayed({ if (mv.text == text) mv.visibility = View.GONE }, 5000L)
    }

    private fun recentToggles(): Int {
        val now = System.currentTimeMillis(); val iter = toggles.iterator()
        while (iter.hasNext()) { if (now - iter.next() > 20000L) iter.remove() }
        return toggles.size
    }

    private fun evaluateMood() {
        val n = recentToggles(); val now = System.currentTimeMillis()
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val m = when {
            now < surpriseUntil -> Mood.SURPRISED
            n >= 10 -> Mood.ANGRY
            n >= 6 -> Mood.SICK
            !charging && batteryPct <= 5 -> Mood.UPSET
            !charging && batteryPct <= 15 -> Mood.SAD
            hour < 6 -> Mood.SLEEPY
            else -> Mood.HAPPY
        }
        setMood(m)
    }

    private fun noteToggle() { toggles.add(System.currentTimeMillis()); evaluateMood() }

    private fun poke() {
        toggles.add(System.currentTimeMillis()); surpriseUntil = System.currentTimeMillis() + 1500L
        eyes?.blinkNow(); say("Хи-хи! 🤭"); evaluateMood(); ui.postDelayed({ evaluateMood() }, 1600L)
    }

    private fun hit(v: View, x: Float, y: Float, pad: Int): Boolean {
        if (!v.isShown) return false
        val loc = IntArray(2); v.getLocationOnScreen(loc)
        return x >= loc[0] - pad && x <= loc[0] + v.width + pad && y >= loc[1] - pad && y <= loc[1] + v.height + pad
    }

    private fun handleTap(x: Float, y: Float) {
        for (t in targets) {
            if (hit(t.v, x, y, t.pad)) {
                t.v.alpha = 0.4f; t.v.animate().alpha(1f).setDuration(160L).start(); t.run(); return
            }
        }
    }

    private fun onTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = e.rawX; downY = e.rawY; gestureDone = false
                val vb = volBar; val bb = brightBar
                sliderDrag = isOpen && vb != null && hit(vb, e.rawX, e.rawY, dp(14))
                brightDrag = isOpen && bb != null && hit(bb, e.rawX, e.rawY, dp(14))
                if (sliderDrag) setVolumeFrom(e.rawX)
                if (brightDrag) setBrightnessFrom(e.rawX)
            }
            MotionEvent.ACTION_MOVE -> {
                if (sliderDrag) setVolumeFrom(e.rawX)
                else if (brightDrag) setBrightnessFrom(e.rawX)
                else if (!gestureDone) {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) > dp(16) && abs(dx) > abs(dy)) { gestureDone = true; if (dx < 0f) open() else close() }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (sliderDrag) sliderDrag = false
                else if (brightDrag) brightDrag = false
                else if (!gestureDone) {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (abs(dx) < dp(10) && abs(dy) < dp(10)) {
                        if (!isOpen) open()
                        else if (e.x >= panelW) close()
                        else handleTap(e.rawX, e.rawY)
                    }
                }
            }
            MotionEvent.ACTION_CANCEL -> { sliderDrag = false; brightDrag = false }
            MotionEvent.ACTION_OUTSIDE -> { if (isOpen) close(); return false }
        }
        return true
    }

    private fun tick() { try { root?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Exception) {} }

    private fun open() {
        if (isOpen) return
        val r = root ?: return; val c = content ?: return; val p = params ?: return
        isOpen = true; noteToggle(); tick(); c.animate().cancel(); c.visibility = View.VISIBLE
        if (!expanded) {
            expanded = true; p.width = panelW + handleW; p.height = WindowManager.LayoutParams.WRAP_CONTENT
            try { wm.updateViewLayout(r, p) } catch (_: Exception) {}
            c.translationX = panelW.toFloat()
        }
        eyes?.setActive(true); updateClock(); updateBattery(); updateVolume(); updateBrightness(); maybeFetchWeather()
        ui.removeCallbacks(musicRun); ui.post(musicRun)
        c.animate().translationX(0f).setDuration(260L).setInterpolator(DecelerateInterpolator()).start()

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val hello: String = when {
            hour in 5..11 -> "Доброе утро! ☀️"
            hour in 12..17 -> "Как дела? 👋"
            hour in 18..22 -> "Добрый вечер 🌆"
            else -> "Тсс... уже поздно 🌙"
        }
        say(mods.greeting ?: hello)
    }

    private fun close() {
        if (!isOpen) return
        val c = content ?: return; isOpen = false; noteToggle(); tick(); ui.removeCallbacks(musicRun); c.animate().cancel()
        c.animate().translationX(panelW.toFloat()).setDuration(220L).setInterpolator(DecelerateInterpolator()).withEndAction { collapse() }.start()
    }

    private fun collapse() {
        if (isOpen) return
        val c = content; val r = root; val p = params
        if (c == null || r == null || p == null) return
        eyes?.setActive(false); c.visibility = View.INVISIBLE; expanded = false; p.width = handleW; p.height = closedH
        try { wm.updateViewLayout(r, p) } catch (_: Exception) {}
        ui.postDelayed({
            val cc = content
            if (cc != null) { if (!isOpen) cc.translationX = 0f; cc.visibility = View.VISIBLE }
        }, 80L)
    }

    private fun launch(i: Intent) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(i); close() } catch (_: Exception) {}
    }

    private fun toggleTorch() {
        try {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            var id: String? = null
            for (i in cm.cameraIdList) {
                val ch = cm.getCameraCharacteristics(i)
                if (ch.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) { id = i; break }
            }
            if (id != null) { torchOn = !torchOn; cm.setTorchMode(id, torchOn); applyAccent() }
            say(if (torchOn) "Светло! 🔦" else "Темно... 🌑")
        } catch (_: Exception) {}
    }

    private fun toggleSilent() {
        try {
            silentMode = !silentMode
            audio.ringerMode = if (silentMode) AudioManager.RINGER_MODE_SILENT else AudioManager.RINGER_MODE_NORMAL
            applyAccent(); say(if (silentMode) "Тишина... 🔇" else "Звук включен! 🔔")
        } catch (_: Exception) {}
    }

    private fun setVolumeFrom(x: Float) {
        val b = volBar ?: return; if (b.width <= 0) return
        val loc = IntArray(2); b.getLocationOnScreen(loc)
        val f = ((x - loc[0]) / b.width).coerceIn(0f, 1f)
        try {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(f * max), 0)
        } catch (_: Exception) {}
        updateVolume()
    }

    private fun updateVolume() {
        try {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC); val cur = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            val pct = if (max > 0) cur * 100f / max else 0f
            setFill(volFill, pct); volView?.text = pct.toInt().toString() + "%"
        } catch (_: Exception) {}
    }

    private fun setBrightnessFrom(x: Float) {
        val b = brightBar ?: return; if (b.width <= 0) return
        val loc = IntArray(2); b.getLocationOnScreen(loc)
        val f = ((x - loc[0]) / b.width).coerceIn(0f, 1f)
        try {
            if (Settings.System.canWrite(ctx)) {
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, (f * 255).toInt())
            } else {
                say("Дай разрешение в настройках ⚙️")
                val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, android.net.Uri.parse("package:" + ctx.packageName))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); ctx.startActivity(intent); close()
            }
        } catch (_: Exception) {}
        updateBrightness()
    }

    private fun updateBrightness() {
        try {
            val cur = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
            val pct = cur * 100f / 255f
            setFill(brightFill, pct); brightView?.text = pct.toInt().toString() + "%"
        } catch (_: Exception) {}
    }

    private fun musicCmd(d: Int) {
        val c = musicCtrl ?: return
        try {
            val tc = c.transportControls
            if (d < 0) tc.skipToPrevious()
            else if (d > 0) tc.skipToNext()
            else if (c.playbackState?.state == PlaybackState.STATE_PLAYING) tc.pause() else tc.play()
        } catch (_: Exception) {}
        ui.postDelayed({ pollMusic() }, 350L)
    }

    private fun pollMusic() {
        if (musicBox == null) return
        var ctrl: MediaController? = null
        try {
            val msm = ctx.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val list = msm.getActiveSessions(ComponentName(ctx, MusicListener::class.java))
            for (mc in list) { if (mc.playbackState?.state == PlaybackState.STATE_PLAYING) { ctrl = mc; break } }
            if (ctrl == null && list.isNotEmpty()) ctrl = list[0]
        } catch (_: Exception) {}
        val cur: MediaController? = ctrl; musicCtrl = cur
        val box = musicBox ?: return
        if (cur == null) { box.visibility = View.GONE; eyes?.setDancing(false); return }
        val playing = cur.playbackState?.state == PlaybackState.STATE_PLAYING
        val md = cur.metadata; val t = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        val a = md?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
        val line = if (a.isEmpty()) t else t + " — " + a
        box.visibility = View.VISIBLE; musicTitle?.text = if (line.isEmpty()) "Играет музыка" else line
        playChip?.text = if (playing) "⏸" else "▶"; eyes?.setDancing(playing)
    }

    private fun updateClock() {
        val d = Date(); timeView?.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
        dateView?.text = SimpleDateFormat("EEEE, d MMMM", Locale.forLanguageTag("ru")).format(d)
    }

    private fun updateBattery() {
        val i = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        val level = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val status = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val was = charging
        charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        if (batteryKnown && charging && !was) { surpriseUntil = System.currentTimeMillis() + 2500L; ui.postDelayed({ evaluateMood() }, 2600L) }
        batteryKnown = true; batteryPct = if (level >= 0 && scale > 0) level * 100 / scale else 0
        val suffix = if (charging) " · заряд" else ""
        batteryView?.text = batteryPct.toString() + "%" + suffix; setFill(batFill, batteryPct.toFloat()); evaluateMood()
        if (batteryPct <= 15 && !charging && isOpen) say("Покорми меня... 🔋")
    }

    private fun maybeFetchWeather() {
        if (weatherView == null) return
        val now = System.currentTimeMillis(); if (now - lastWeather < 20L * 60L * 1000L) return
        lastWeather = now
        Thread {
            var text: String? = null
            try {
                val c = URL("https://wttr.in/?format=%c+%t+%C&lang=ru").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 8000; c.setRequestProperty("User-Agent", "curl/8.0")
                if (c.responseCode == 200) { text = c.inputStream.bufferedReader().use { it.readText() }.trim() }
                c.disconnect()
            } catch (_: Exception) {}
            val result: String? = text
            ui.post {
                if (result != null && result.isNotEmpty() && result.length < 60) { weatherView?.text = result }
                else { weatherView?.text = "нет данных"; lastWeather = System.currentTimeMillis() - 15L * 60L * 1000L }
            }
        }.start()
    }
}
