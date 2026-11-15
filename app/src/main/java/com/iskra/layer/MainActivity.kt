package com.iskra.layer

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.File

class MainActivity : Activity() {
    private val bgColor = 0xFF0B0C0F.toInt()
    private val cardColor = 0xFF14161A.toInt()
    private val btnColor = 0xFF1B1D22.toInt()
    private val strokeColor = 0xFF2A2D33.toInt()
    private val grey = 0xFF9297A1.toInt()
    private val mp = LinearLayout.LayoutParams.MATCH_PARENT
    private val wc = LinearLayout.LayoutParams.WRAP_CONTENT

    private var tab = 0
    private var accent = Mood.HAPPY.color
    private var previewIdx = 0
    private var body: LinearLayout? = null
    private var scroll: ScrollView? = null
    private var navBar: LinearLayout? = null

    // Черновик редактора: применяется только по кнопке «Сохранить»
    private val editOrder = ArrayList<String>()
    private val editOn = HashMap<String, Boolean>()
    private var editSize = 1

    private val swatches = intArrayOf(0, 0xFFFFC107.toInt(), 0xFF00E5FF.toInt(), 0xFFFF4081.toInt(), 0xFF69F0AE.toInt(), 0xFFEEEEEE.toInt())
    private val navTitles = arrayOf("Главная", "Редактор", "Моды", "Настройки")
    private val navIcons = arrayOf("🏠", "🧩", "📦", "⚙")

    // ---------- helpers ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

    private fun tv(t: String, sp: Float, color: Int, bold: Boolean, center: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        v.setTextColor(color)
        v.gravity = if (center) Gravity.CENTER else Gravity.START
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun lpFull(hDp: Int, topDp: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(mp, dp(hDp))
        lp.topMargin = dp(topDp)
        return lp
    }

    private fun lpWrapFull(topDp: Int): LinearLayout.LayoutParams {
        val lp = LinearLayout.LayoutParams(mp, wc)
        lp.topMargin = dp(topDp)
        return lp
    }

    private fun rounded(fill: Int, strokeCol: Int, strokeDp: Int, radiusDp: Int): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(radiusDp).toFloat()
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeCol)
        return g
    }

    // Фон с волной (ripple) при нажатии
    private fun pressable(fill: Int, strokeCol: Int, strokeDp: Int, radiusDp: Int): Drawable {
        val mask = GradientDrawable()
        mask.setColor(Color.WHITE)
        mask.cornerRadius = dp(radiusDp).toFloat()
        return RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), rounded(fill, strokeCol, strokeDp, radiusDp), mask)
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.setPadding(dp(16), dp(16), dp(16), dp(16))
        c.background = rounded(cardColor, strokeColor, 1, 18)
        c.elevation = dp(2).toFloat()
        return c
    }

    private fun btn(label: String, primary: Boolean, onClick: () -> Unit): TextView {
        val b = TextView(this)
        b.text = label
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        b.typeface = Typeface.DEFAULT_BOLD
        b.gravity = Gravity.CENTER
        if (primary) {
            b.background = pressable(accent, 0, 0, 14)
            b.setTextColor(0xFF111111.toInt())
        } else {
            b.background = pressable(btnColor, strokeColor, 1, 14)
            b.setTextColor(Color.WHITE)
        }
        b.setOnClickListener { onClick() }
        return b
    }

    private fun smallChip(label: String, active: Boolean, onClick: () -> Unit): TextView {
        val c = tv(label, 14f, if (active) Color.WHITE else 0xFF50555E.toInt(), true, true)
        c.background = pressable(cardColor, strokeColor, 1, 10)
        if (active) c.setOnClickListener { onClick() }
        return c
    }

    private fun segmented(options: Array<String>, cur: Int, onPick: (Int) -> Unit): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.background = rounded(btnColor, strokeColor, 1, 14)
        row.setPadding(dp(3), dp(3), dp(3), dp(3))
        for (i in options.indices) {
            val sel = i == cur
            val c = tv(options[i], 13f, if (sel) 0xFF111111.toInt() else Color.WHITE, sel, true)
            c.background = if (sel) pressable(accent, 0, 0, 11) else pressable(Color.TRANSPARENT, 0, 0, 11)
            c.setOnClickListener { onPick(i) }
            row.addView(c, LinearLayout.LayoutParams(0, dp(40), 1f))
        }
        return row
    }

    private fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_LONG).show() }

    // ---------- жизненный цикл ----------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bgColor
        window.navigationBarColor = cardColor

        val rootCol = LinearLayout(this)
        rootCol.orientation = LinearLayout.VERTICAL
        rootCol.setBackgroundColor(bgColor)

        val b = LinearLayout(this)
        b.orientation = LinearLayout.VERTICAL
        b.setPadding(dp(16), dp(24), dp(16), dp(24))
        val sv = ScrollView(this)
        sv.setBackgroundColor(bgColor)
        sv.addView(b, FrameLayout.LayoutParams(mp, wc))
        rootCol.addView(sv, LinearLayout.LayoutParams(mp, 0, 1f))
        body = b
        scroll = sv

        val nb = LinearLayout(this)
        nb.orientation = LinearLayout.HORIZONTAL
        nb.setBackgroundColor(cardColor)
        nb.setPadding(dp(8), dp(6), dp(8), dp(6))
        nb.elevation = dp(8).toFloat()
        rootCol.addView(nb, LinearLayout.LayoutParams(mp, dp(68)))
        navBar = nb

        setContentView(rootCol)
        loadEdit()

        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        startLayerIfNeeded()
        updateUi()
    }

    private fun startLayerIfNeeded() {
        if (!Settings.canDrawOverlays(this) || !LayerPrefs.isEnabled(this)) return
        try { startForegroundService(Intent(this, LayerService::class.java)) } catch (e: Exception) {}
    }

    private fun restartLayer() {
        if (!Settings.canDrawOverlays(this) || !LayerPrefs.isEnabled(this)) return
        stopService(Intent(this, LayerService::class.java))
        startLayerIfNeeded()
    }

    private fun toggle() {
        val now = !LayerPrefs.isEnabled(this)
        LayerPrefs.setEnabled(this, now)
        if (now) startLayerIfNeeded() else stopService(Intent(this, LayerService::class.java))
        updateUi()
    }

    private fun openOverlaySettings() {
        try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + packageName))) } catch (e: Exception) {}
    }

    private fun openWriteSettings() {
        try { startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + packageName))) } catch (e: Exception) {}
    }

    private fun openListenerSettings() {
        try { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } catch (e: Exception) {}
    }

    private fun listenerEnabled(): Boolean {
        val s = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        return s.contains(packageName)
    }

    private fun updateUi() {
        val can = Settings.canDrawOverlays(this)
        val on = LayerPrefs.isEnabled(this)
        val mood = if (!can) Mood.SAD else if (on) Mood.HAPPY else Mood.UPSET
        accent = mood.color
        renderNav()
        renderTab()
    }

    // ---------- навигация ----------

    private fun renderNav() {
        val nb = navBar ?: return
        nb.removeAllViews()
        for (i in navTitles.indices) {
            val sel = i == tab
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            item.addView(tv(navIcons[i], 20f, Color.WHITE, false, true))
            item.addView(tv(navTitles[i], 11f, if (sel) accent else grey, sel, true))
            item.background = pressable(if (sel) withAlpha(accent, 0x22) else Color.TRANSPARENT, 0, 0, 14)
            item.setOnClickListener {
                tab = i
                renderNav()
                renderTab()
                scroll?.scrollTo(0, 0)
            }
            val lp = LinearLayout.LayoutParams(0, mp, 1f)
            lp.leftMargin = dp(3)
            lp.rightMargin = dp(3)
            nb.addView(item, lp)
        }
    }

    private fun renderTab() {
        val b = body ?: return
        b.removeAllViews()
        when (tab) {
            0 -> renderHome(b)
            1 -> renderEditor(b)
            2 -> renderMods(b)
            else -> renderSettings(b)
        }
    }

    // ---------- вкладка: Главная ----------

    private fun renderHome(b: LinearLayout) {
        val can = Settings.canDrawOverlays(this)
        val on = LayerPrefs.isEnabled(this)
        val mood = if (!can) Mood.SAD else if (on) Mood.HAPPY else Mood.UPSET

        val hero = card()
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val ev = EyesView(this)
        ev.setLook(mood, mood.color)
        ev.setOnClickListener {
            val all = Mood.values()
            previewIdx = (previewIdx + 1) % all.size
            val m = all[previewIdx]
            ev.setLook(m, m.color)
            ev.postDelayed({ if (tab == 0) updateUi() }, 3000L)
        }
        hero.addView(ev, LinearLayout.LayoutParams(dp(120), dp(64)))
        hero.addView(tv("Слой", 28f, Color.WHITE, true, true), lpWrapFull(12))
        val status = if (!can) "Нужно разрешение «Поверх других приложений»" else if (on) "Слой включён" else "Слой выключен"
        hero.addView(tv(status, 14f, accent, true, true), lpWrapFull(6))
        hero.addView(tv("Панель за правым краем экрана. Свайп от края влево.\nНажми на глаза, чтобы увидеть эмоции.", 12f, grey, false, true), lpWrapFull(8))
        b.addView(hero, lpWrapFull(0))

        val ctl = card()
        if (!can) ctl.addView(btn("Разрешить поверх приложений", true) { openOverlaySettings() }, lpFull(52, 0))
        ctl.addView(btn(if (on) "Выключить слой" else "Включить слой", !on && can) { toggle() }, lpFull(52, if (can) 0 else 10))
        ctl.addView(btn(if (listenerEnabled()) "Музыка на панели: включена ✓" else "Включить музыку на панели", false) { openListenerSettings() }, lpFull(52, 10))
        b.addView(ctl, lpWrapFull(12))
    }

    // ---------- вкладка: Редактор ----------

    private fun loadEdit() {
        editOrder.clear()
        editOrder.addAll(PanelLayout.order(this))
        editOn.clear()
        for (id in PanelLayout.IDS) editOn[id] = PanelLayout.isOn(this, id)
        editSize = LayerPrefs.getInt(this, "size", 1)
    }

    private fun moveBlock(idx: Int, d: Int) {
        val j = idx + d
        if (j < 0 || j >= editOrder.size) return
        val t = editOrder[idx]
        editOrder[idx] = editOrder[j]
        editOrder[j] = t
        renderTab()
    }

    private fun saveEdit() {
        PanelLayout.saveOrder(this, editOrder)
        for (id in PanelLayout.IDS) PanelLayout.setOn(this, id, editOn[id] ?: true)
        LayerPrefs.putInt(this, "size", editSize)
        restartLayer()
        toast("Сохранено и применено")
    }

    private fun resetEdit() {
        editOrder.clear()
        for (id in PanelLayout.IDS) editOrder.add(id)
        for (id in PanelLayout.IDS) editOn[id] = true
        editSize = 1
        renderTab()
        toast("Сброшено. Нажми «Сохранить», чтобы применить")
    }

    private fun editorRow(idx: Int, id: String): LinearLayout {
        val on = editOn[id] ?: true
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.background = rounded(btnColor, strokeColor, 1, 14)
        row.setPadding(dp(14), 0, dp(8), 0)

        val name = tv(PanelLayout.title(id), 14f, if (on) Color.WHITE else grey, on)
        name.gravity = Gravity.CENTER_VERTICAL
        row.addView(name, LinearLayout.LayoutParams(0, mp, 1f))

        val pill = tv(if (on) "ВКЛ" else "ВЫКЛ", 11f, if (on) 0xFF111111.toInt() else Color.WHITE, true, true)
        pill.background = if (on) pressable(accent, 0, 0, 10) else pressable(0xFF2A2D33.toInt(), 0, 0, 10)
        pill.setOnClickListener {
            editOn[id] = !on
            renderTab()
        }
        val plp = LinearLayout.LayoutParams(dp(50), dp(32))
        plp.leftMargin = dp(6)
        row.addView(pill, plp)

        val up = smallChip("▲", idx > 0) { moveBlock(idx, -1) }
        val ulp = LinearLayout.LayoutParams(dp(34), dp(34))
        ulp.leftMargin = dp(6)
        row.addView(up, ulp)

        val down = smallChip("▼", idx < editOrder.size - 1) { moveBlock(idx, 1) }
        val dlp = LinearLayout.LayoutParams(dp(34), dp(34))
        dlp.leftMargin = dp(6)
        row.addView(down, dlp)
        return row
    }

    private fun renderEditor(b: LinearLayout) {
        val w = card()
        w.addView(tv("Ширина панели", 13f, grey, true))
        w.addView(segmented(arrayOf("Узкая", "Обычная", "Широкая"), editSize) {
            editSize = it
            renderTab()
        }, lpFull(46, 8))
        b.addView(w, lpWrapFull(0))

        val list = card()
        list.addView(tv("Блоки панели (сверху вниз)", 13f, grey, true))
        for ((idx, id) in editOrder.withIndex()) {
            list.addView(editorRow(idx, id), lpFull(52, 8))
        }
        b.addView(list, lpWrapFull(12))

        b.addView(btn("Сохранить и применить", true) { saveEdit() }, lpFull(54, 16))
        b.addView(btn("Сбросить по умолчанию", false) { resetEdit() }, lpFull(48, 8))
    }

    // ---------- вкладка: Моды ----------

    private fun modRow(f: File): LinearLayout {
        val on = Mods.isEnabled(this, f.name)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL

        val c = tv((if (on) "✓  " else "○  ") + Mods.title(f), 13f, if (on) 0xFF111111.toInt() else Color.WHITE, on)
        c.gravity = Gravity.CENTER_VERTICAL
        c.setPadding(dp(14), 0, dp(10), 0)
        c.maxLines = 1
        c.ellipsize = TextUtils.TruncateAt.END
        c.background = if (on) pressable(accent, 0, 0, 12) else pressable(btnColor, strokeColor, 1, 12)
        c.setOnClickListener {
            Mods.setEnabled(this, f.name, !on)
            modsChanged()
        }
        row.addView(c, LinearLayout.LayoutParams(0, mp, 1f))

        val d = tv("✕", 15f, Color.WHITE, true, true)
        d.background = pressable(btnColor, strokeColor, 1, 12)
        d.setOnClickListener {
            Mods.delete(this, f)
            modsChanged()
        }
        val lp = LinearLayout.LayoutParams(dp(46), mp)
        lp.leftMargin = dp(6)
        row.addView(d, lp)
        return row
    }

    private fun modsChanged() {
        renderTab()
        restartLayer()
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

    private fun renderMods(b: LinearLayout) {
        val c = card()
        c.addView(tv("Моды", 18f, Color.WHITE, true))
        c.addView(tv("Мод — это .json файл: прячет блоки, добавляет кнопки, меняет цвет. Нажми на мод, чтобы включить или выключить.", 12f, grey, false), lpWrapFull(6))
        val files = Mods.list(this)
        if (files.isEmpty()) c.addView(tv("Модов пока нет", 13f, grey, false), lpWrapFull(14))
        for (f in files) c.addView(modRow(f), lpFull(46, 8))
        b.addView(c, lpWrapFull(0))

        val act = card()
        act.addView(btn("+ Импорт файла (.json)", false) { pickMod() }, lpFull(48, 0))
        act.addView(btn("+ Вставить текст мода", false) { pasteMod() }, lpFull(48, 8))
        act.addView(btn("+ Добавить пример мода", false) { addExample() }, lpFull(48, 8))
        b.addView(act, lpWrapFull(12))
    }

    // ---------- вкладка: Настройки ----------

    private fun colorRow(): LinearLayout {
        val cur = LayerPrefs.getInt(this, "color", 0)
        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        for (i in swatches.indices) {
            val c = tv(if (i == 0) "Авто" else if (i == cur) "✓" else "", 13f, if (i == 0) Color.WHITE else 0xFF111111.toInt(), true, true)
            val fill = if (i == 0) btnColor else swatches[i]
            val sc = if (i == cur) Color.WHITE else strokeColor
            c.background = pressable(fill, sc, if (i == cur) 2 else 1, 12)
            c.setOnClickListener {
                LayerPrefs.putInt(this, "color", i)
                restartLayer()
                renderTab()
            }
            val lp = LinearLayout.LayoutParams(0, mp, 1f)
            lp.leftMargin = dp(3)
            lp.rightMargin = dp(3)
            row.addView(c, lp)
        }
        return row
    }

    private fun renderSettings(b: LinearLayout) {
        val c1 = card()
        c1.addView(tv("Положение ручки", 13f, grey, true))
        c1.addView(segmented(arrayOf("Сверху", "По центру", "Снизу"), LayerPrefs.getInt(this, "pos", 1)) {
            LayerPrefs.putInt(this, "pos", it)
            restartLayer()
            renderTab()
        }, lpFull(46, 8))
        c1.addView(tv("Цвет панели", 13f, grey, true), lpWrapFull(18))
        c1.addView(colorRow(), lpFull(44, 8))
        b.addView(c1, lpWrapFull(0))

        val c2 = card()
        c2.addView(tv("Разрешения", 13f, grey, true))
        c2.addView(btn("Разрешить изменение яркости", false) { openWriteSettings() }, lpFull(48, 8))
        c2.addView(btn("Доступ к музыке (уведомления)", false) { openListenerSettings() }, lpFull(48, 8))
        b.addView(c2, lpWrapFull(12))

        val c3 = card()
        c3.addView(tv("О приложении", 13f, grey, true))
        c3.addView(tv("Слой v8 · конструктор панели", 14f, Color.WHITE, false), lpWrapFull(6))
        b.addView(c3, lpWrapFull(12))
    }
}
