package com.iskra.layer

import android.content.Context
import android.graphics.Color
import org.json.JSONObject
import java.io.File

class ModButton(val icon: String, val action: String, val arg: String)

class ModState(
    val hidden: Set<String>,
    val buttons: List<ModButton>,
    val accent: Int?,
    val greeting: String?
)

object Mods {
    const val MAX_BUTTONS = 12

    val EXAMPLE = """{"name":"Пример","greeting":"Привет из мода!","hide":[],"buttons":[{"icon":"📶","action":"wifi"},{"icon":"🔵","action":"bluetooth"},{"icon":"🌐","action":"url","arg":"https://google.com"},{"icon":"💬","action":"say","arg":"Это кнопка из мода"}]}"""

    fun dir(c: Context): File {
        val d = File(c.filesDir, "mods")
        if (!d.exists()) d.mkdirs()
        return d
    }

    fun list(c: Context): List<File> {
        val arr = dir(c).listFiles() ?: return emptyList()
        val out = ArrayList<File>()
        for (f in arr) {
            if (f.isFile && f.name.endsWith(".json")) out.add(f)
        }
        out.sortBy { it.name }
        return out
    }

    fun isEnabled(c: Context, name: String): Boolean = LayerPrefs.getInt(c, "mod_" + name, 1) == 1
    fun setEnabled(c: Context, name: String, on: Boolean) { LayerPrefs.putInt(c, "mod_" + name, if (on) 1 else 0) }

    fun delete(c: Context, f: File) { try { f.delete() } catch (_: Exception) {} }

    fun title(f: File): String {
        return try {
            JSONObject(f.readText()).optString("name", f.name)
        } catch (e: Exception) {
            f.name + " (ошибка)"
        }
    }

    // Возвращает текст ошибки или null, если всё хорошо
    fun save(c: Context, rawName: String, text: String): String? {
        try {
            JSONObject(text)
        } catch (e: Exception) {
            return "Это не JSON: " + (e.message ?: "")
        }
        var n = rawName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        if (!n.endsWith(".json")) n = n + ".json"
        if (!n.any { it.isLetterOrDigit() }) n = "mod_" + (System.currentTimeMillis() / 1000L) + ".json"
        if (n.length > 60) n = n.substring(n.length - 60)
        return try {
            File(dir(c), n).writeText(text)
            null
        } catch (e: Exception) {
            "Не удалось сохранить файл"
        }
    }

    fun load(c: Context): ModState {
        val hidden = HashSet<String>()
        val buttons = ArrayList<ModButton>()
        var accent: Int? = null
        var greeting: String? = null
        for (f in list(c)) {
            if (!isEnabled(c, f.name)) continue
            try {
                val o = JSONObject(f.readText())
                val h = o.optJSONArray("hide")
                if (h != null) {
                    for (i in 0 until h.length()) hidden.add(h.optString(i))
                }
                val b = o.optJSONArray("buttons")
                if (b != null) {
                    for (i in 0 until b.length()) {
                        if (buttons.size >= MAX_BUTTONS) break
                        val bo = b.optJSONObject(i) ?: continue
                        val icon = bo.optString("icon", "•")
                        val action = bo.optString("action", "")
                        val arg = bo.optString("arg", bo.optString("pkg", bo.optString("url", bo.optString("text", ""))))
                        buttons.add(ModButton(icon, action, arg))
                    }
                }
                val ac = o.optString("accent", "")
                if (ac.isNotEmpty()) {
                    try { accent = Color.parseColor(ac) } catch (_: Exception) {}
                }
                val g = o.optString("greeting", "")
                if (g.isNotEmpty()) greeting = g
            } catch (_: Exception) {
                // кривой мод просто пропускаем
            }
        }
        return ModState(hidden, buttons, accent, greeting)
    }
}
