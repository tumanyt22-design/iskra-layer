package com.iskra.layer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private var eyes: EyesView? = null
    private var statusView: TextView? = null
    private var overlayBtn: TextView? = null
    private var toggleBtn: TextView? = null
    private var musicBtn: TextView? = null
    private var settingsBox: LinearLayout? = null
    private var accent = Mood.HAPPY.color
    private var previewIdx = 0
    private val swatches = intArrayOf(0, 0xFFFFC107.toInt(), 0xFF00E5FF.toInt(), 0xFFFF4081.toInt(), 0xFF69F0AE.toInt(), 0xFFEEEEEE.toInt())

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun text(t: String, sp: Float, color: Int, bold: Boolean): TextView {
        val v = TextView(this)
        v.text = t; v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); v.setTextColor(color); v.gravity = Gravity.CENTER
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun wrapLp(top: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(top); return lp
    }

    private fun fullLp(h: Int, top: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(h))
        lp.topMargin = dp(top); return lp
    }

    private fun button(onClick: () -> Unit): TextView {
        val b = TextView(this); b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); b.typeface = Typeface.DEFAULT_BOLD
        b.gravity = Gravity.CENTER; b.setOnClickListener { onClick() }; return b
    }

    private fun style(b: TextView, primary: Boolean) {
        val bg = GradientDrawable(); bg.cornerRadius = dp(16).toFloat()
        if (primary) { bg.setColor(accent); b.setTextColor(0xFF111111.toInt()) }
        else { bg.setColor(0xFF14161A.toInt()); bg.setStroke(dp(1), 0xFF2A2D33.toInt()); b.setTextColor(Color.WHITE) }
        b.background = bg
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK; window.navigationBarColor = Color.BLACK

        val root = LinearLayout(this); root.orientation = LinearLayout.VERTICAL; root.gravity = Gravity.CENTER_HORIZONTAL
        root.setBackgroundColor(Color.BLACK); root.setPadding(dp(28), dp(56), dp(28), dp(40))

        val ev = EyesView(this)
        ev.setOnClickListener {
            val all = Mood.values(); previewIdx = (previewIdx + 1) % all.size
            val m = all[previewIdx]; ev.setLook(m, m.color); ev.postDelayed({ updateUi() }, 3000L)
        }
        root.addView(ev, LinearLayout.LayoutParams(dp(120), dp(64))); eyes = ev

        root.addView(text("Слой", 30f, Color.WHITE, true), wrapLp(16))
        root.addView(text("Панель за правым краем экрана.\nСвайп от края влево.\nНажми на глаза: покажут эмоции.", 13f, 0xFF9297A1.toInt(), false), wrapLp(8))

        val st = text("", 15f, Color.WHITE, false); root.addView(st, wrapLp(28)); statusView = st

        val ob = button { openOverlaySettings() }; ob.text = "Разрешить поверх приложений"; root.addView(ob, fullLp(52, 20)); overlayBtn = ob
        val tb = button { toggle() }; root.addView(tb, fullLp(52, 12)); toggleBtn = tb
        val mb = button { openListenerSettings() }; root.addView(mb, fullLp(52, 12)); musicBtn = mb

        val box = LinearLayout(this); box.orientation = LinearLayout.VERTICAL; root.addView(box, fullLpWrap()); settingsBox = box

        val scroll = ScrollView(this); scroll.setBackgroundColor(Color.BLACK); scroll.isFillViewport = true
        scroll.addView(root); setContentView(scroll)

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun fullLpWrap(): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        lp.topMargin = dp(8); return lp
    }

    override fun onResume() { super.onResume(); startLayerIfNeeded(); updateUi() }

    private fun startLayerIfNeeded() {
        if (!Settings.canDrawOverlays(this) || !LayerPrefs.isEnabled(this)) return
        try { startForegroundService(Intent(this, LayerService::class.java)) } catch (e: Exception) {}
    }

    private fun restartLayer() {
        if (!Settings.canDrawOverlays(this) || !LayerPrefs.isEnabled(this)) return
        stopService(Intent(this, LayerService::class.java)); startLayerIfNeeded()
    }

    private fun toggle() {
        val now = !LayerPrefs.isEnabled(this); LayerPrefs.setEnabled(this, now)
        if (now) startLayerIfNeeded() else stopService(Intent(this, LayerService::class.java))
        updateUi()
    }

    private fun openOverlaySettings() {
        try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))) } catch (e: Exception) {}
    }

    private fun openListenerSettings() {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) {}
    }

    private fun listenerEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(packageName)
    }

    private fun caption(t: String): TextView {
        val v = text(t, 12f, 0xFF9297A1.toInt(), false); v.gravity = Gravity.START; return v
    }

    private fun optionRow(key: String, def: Int, options: Array<String>): LinearLayout {
        val cur = LayerPrefs.getInt(this, key, def)
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        for (i in options.indices) {
            val c = text(options[i], 13f, Color.WHITE, false)
            val bg = GradientDrawable(); bg.cornerRadius = dp(12).toFloat()
            if (i == cur) { bg.setColor(accent); c.setTextColor(0xFF111111.toInt()) }
            else { bg.setColor(0xFF14161A.toInt()); bg.setStroke(dp(1), 0xFF2A2D33.toInt()) }
            c.background = bg
            c.setOnClickListener { LayerPrefs.putInt(this, key, i); renderSettings(); restartLayer() }
            val lp = LinearLayout.LayoutParams(0, dp(42), 1f); lp.leftMargin = dp(3); lp.rightMargin = dp(3)
            row.addView(c, lp)
        }
        return row
    }

    private fun colorRow(): LinearLayout {
        val cur = LayerPrefs.getInt(this, "color", 0)
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL
        for (i in swatches.indices) {
            val c = text(if (i == 0) "Авто" else if (i == cur) "✓" else "", 13f, Color.WHITE, true)
            val bg = GradientDrawable(); bg.cornerRadius = dp(12).toFloat()
            if (i == 0) bg.setColor(0xFF14161A.toInt()) else { bg.setColor(swatches[i]); c.setTextColor(0xFF111111.toInt()) }
            if (i == cur) bg.setStroke(dp(2), Color.WHITE) else bg.setStroke(dp(1), 0xFF2A2D33.toInt())
            c.background = bg
            c.setOnClickListener { LayerPrefs.putInt(this, "color", i); renderSettings(); restartLayer() }
            val lp = LinearLayout.LayoutParams(0, dp(42), 1f); lp.leftMargin = dp(3); lp.rightMargin = dp(3)
            row.addView(c, lp)
        }
        return row
    }

    // ---------- МОДЫ ----------

    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }

    private fun modsChanged() { renderSettings(); restartLayer() }

    private fun modRow(f: File): LinearLayout {
        val on = Mods.isEnabled(this, f.name)
        val row = LinearLayout(this); row.orientation = LinearLayout.HORIZONTAL

        val c = text((if (on) "✓ " else "○ ") + Mods.title(f), 13f, Color.WHITE, false)
        c.maxLines = 1; c.ellipsize = TextUtils.TruncateAt.END
        val bg = GradientDrawable(); bg.cornerRadius = dp(12).toFloat()
        if (on) { bg.setColor(accent); c.setTextColor(0xFF111111.toInt()) }
        else { bg.setColor(0xFF14161A.toInt()); bg.setStroke(dp(1), 0xFF2A2D33.toInt()) }
        c.background = bg
        c.setOnClickListener { Mods.setEnabled(this, f.name, !on); modsChanged() }
        row.addView(c, LinearLayout.LayoutParams(0, dp(42), 1f))

        val d = text("✕", 15f, Color.WHITE, true)
        val dbg = GradientDrawable(); dbg.cornerRadius = dp(12).toFloat()
        dbg.setColor(0xFF14161A.toInt()); dbg.setStroke(dp(1), 0xFF2A2D33.toInt())
        d.background = dbg
        d.setOnClickListener { Mods.delete(this, f); modsChanged() }
        val lp = LinearLayout.LayoutParams(dp(46), dp(42)); lp.leftMargin = dp(6)
        row.addView(d, lp)
        return row
    }

    private fun pickMod() {
        try {
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            i.addCategory(Intent.CATEGORY_OPENABLE)
            i.type = "*/*"
            startActivityForResult(i, 77)
        } catch (e: Exception) { toast("Не удалось открыть выбор файла") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 77 || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try {
            var name = "mod.json"
            val cur = contentResolver.query(uri, null, null, null, null)
            if (cur != null) {
                cur.use {
                    if (it.moveToFirst()) {
                        val ix = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (ix >= 0) {
                            val n = it.getString(ix)
                            if (n != null) name = n
                        }
                    }
                }
            }
            val txt = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: ""
            val err = Mods.save(this, name, txt)
            toast(err ?: "Мод добавлен")
            modsChanged()
        } catch (e: Exception) { toast("Ошибка чтения файла") }
    }

    private fun pasteMod() {
        val et = EditText(this)
        et.hint = "{\"name\":\"Мой мод\",\"buttons\":[]}"
        et.minLines = 6
        et.gravity = Gravity.TOP or Gravity.START
        AlertDialog.Builder(this)
            .setTitle("Текст мода (JSON)")
            .setView(et)
            .setPositiveButton("Сохранить") { _, _ ->
                val err = Mods.save(this, "paste_" + (System.currentTimeMillis() / 1000L) + ".json", et.text.toString())
                toast(err ?: "Мод добавлен")
                modsChanged()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun addExample() {
        val err = Mods.save(this, "example.json", Mods.EXAMPLE)
        toast(err ?: "Пример добавлен")
        modsChanged()
    }

    private fun renderSettings() {
        val box = settingsBox ?: return
        box.removeAllViews()
        box.addView(caption("РАЗМЕР ПАНЕЛИ"), fullLp(20, 20))
        box.addView(optionRow("size", 1, arrayOf("Компакт", "Обычный", "Широкий")), fullLp(42, 6))
        box.addView(caption("ПОЛОЖЕНИЕ РУЧКИ"), fullLp(20, 16))
        box.addView(optionRow("pos", 1, arrayOf("Сверху", "По центру", "Снизу")), fullLp(42, 6))
        box.addView(caption("ЦВЕТ"), fullLp(20, 16))
        box.addView(colorRow(), fullLp(42, 6))
        box.addView(caption("ГЛАЗА НА ПАНЕЛИ"), fullLp(20, 16))
        box.addView(optionRow("eyes", 1, arrayOf("Выкл", "Вкл")), fullLp(42, 6))

        box.addView(caption("МОДЫ"), fullLp(20, 24))
        for (f in Mods.list(this)) box.addView(modRow(f), fullLp(42, 6))
        val imp = button { pickMod() }; imp.text = "+ Импорт файла мода (.json)"; style(imp, false)
        box.addView(imp, fullLp(46, 10))
        val pst = button { pasteMod() }; pst.text = "+ Вставить текст мода"; style(pst, false)
        box.addView(pst, fullLp(46, 8))
        val smp = button { addExample() }; smp.text = "+ Добавить пример мода"; style(smp, false)
        box.addView(smp, fullLp(46, 8))
    }

    private fun updateUi() {
        val can = Settings.canDrawOverlays(this)
        val on = LayerPrefs.isEnabled(this)
        val mood = when { !can -> Mood.SAD; on -> Mood.HAPPY; else -> Mood.UPSET }
        accent = mood.color; eyes?.setLook(mood, mood.color)
        statusView?.text = when { !can -> "Нужно разрешение «Поверх других приложений»"; on -> "Слой включён"; else -> "Слой выключен" }
        val ob = overlayBtn; if (ob != null) { ob.visibility = if (can) View.GONE else View.VISIBLE; style(ob, true) }
        val tb = toggleBtn; if (tb != null) { tb.text = if (on) "Выключить слой" else "Включить слой"; style(tb, !on && can) }
        val mb = musicBtn; if (mb != null) { mb.text = if (listenerEnabled()) "Музыка на панели: включена ✓" else "Включить музыку на панели"; style(mb, false) }
        renderSettings()
    }
}
