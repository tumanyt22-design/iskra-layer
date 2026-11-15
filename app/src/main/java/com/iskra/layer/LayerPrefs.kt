package com.iskra.layer

import android.content.Context

object LayerPrefs {
    private const val FILE = "layer"
    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, value: Boolean) { sp(c).edit().putBoolean("enabled", value).apply() }
    fun getInt(c: Context, key: String, def: Int): Int = sp(c).getInt(key, def)
    fun putInt(c: Context, key: String, value: Int) { sp(c).edit().putInt(key, value).apply() }
    fun getString(c: Context, key: String, def: String): String = sp(c).getString(key, def) ?: def
    fun putString(c: Context, key: String, value: String) { sp(c).edit().putString(key, value).apply() }
}

// Порядок и видимость блоков панели (настраивается во вкладке «Редактор»)
object PanelLayout {
    val IDS = arrayOf("clock", "eyes", "music", "battery", "weather", "volume", "brightness", "buttons")

    fun title(id: String): String {
        return when (id) {
            "clock" -> "🕒  Часы"
            "eyes" -> "👀  Глаза"
            "music" -> "🎵  Музыка"
            "battery" -> "🔋  Батарея"
            "weather" -> "⛅  Погода"
            "volume" -> "🔊  Громкость"
            "brightness" -> "☀  Яркость"
            "buttons" -> "🔘  Кнопки"
            else -> id
        }
    }

    // Сохранённый порядок; неизвестные id отбрасываются, недостающие добавляются в конец
    fun order(c: Context): List<String> {
        val saved = LayerPrefs.getString(c, "order", "")
        val out = ArrayList<String>()
        for (p in saved.split(",")) {
            if (IDS.contains(p) && !out.contains(p)) out.add(p)
        }
        for (id in IDS) {
            if (!out.contains(id)) out.add(id)
        }
        return out
    }

    fun saveOrder(c: Context, list: List<String>) {
        LayerPrefs.putString(c, "order", list.joinToString(","))
    }

    fun isOn(c: Context, id: String): Boolean = LayerPrefs.getInt(c, "blk_" + id, 1) == 1
    fun setOn(c: Context, id: String, on: Boolean) { LayerPrefs.putInt(c, "blk_" + id, if (on) 1 else 0) }
}
