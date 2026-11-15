package com.iskra.layer

import android.content.Context
import android.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

data class ModButton(
    val icon: String,
    val action: String,
    val arg: String = ""
)

data class ModLayout(
    val width: String = "",
    val padding: Int = 20,
    val scale: Float = 1f
)

data class ModState(
    val name: String = "",
    val greeting: String? = null,
    val accent: Int? = null,
    val bgColor: Int? = null,
    val bgImage: String? = null,
    val order: List<String> = emptyList(),
    val hidden: Set<String> = emptySet(),
    val buttons: List<ModButton> = emptyList(),
    val layout: ModLayout = ModLayout(),
    val text: Map<String, String> = emptyMap()
)

object Mods {
    private const val PREFS = "layer"
    private const val KEY_GALLERY_PENDING = "mod_gallery_pending"
    private const val KEY_MOD_BG_IMAGE = "mod_bg_image"

    private fun dir(c: Context): File {
        val f = File(c.filesDir, "mods")
        if (!f.exists()) f.mkdirs()
        return f
    }

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun list(c: Context): List<File> =
        dir(c).listFiles()?.filter { it.isFile && it.name.endsWith(".json", true) }?.sortedBy { it.name } ?: emptyList()

    fun isEnabled(c: Context, fileName: String): Boolean =
        prefs(c).getBoolean("mod_" + fileName, false)

    fun setEnabled(c: Context, fileName: String, enabled: Boolean) {
        prefs(c).edit().putBoolean("mod_" + fileName, enabled).apply()
        if (enabled) {
            try {
                val obj = JSONObject(File(dir(c), fileName).readText())
                if (obj.optString("bg_image", "") == "gallery") {
                    prefs(c).edit().putBoolean(KEY_GALLERY_PENDING, true).apply()
                }
            } catch (_: Exception) {}
        }
    }

    fun delete(c: Context, file: File) {
        file.delete()
        prefs(c).edit().remove("mod_" + file.name).apply()
    }

    fun title(file: File): String {
        return try {
            val name = JSONObject(file.readText()).optString("name", "")
            if (name.isNotBlank()) name else file.nameWithoutExtension
        } catch (_: Exception) {
            file.nameWithoutExtension
        }
    }

    fun save(c: Context, fileName: String, json: String): String? {
        return try {
            val obj = JSONObject(json)
            val safeName = fileName.substringAfterLast('/').substringBeforeLast('.')
                .replace(Regex("[^A-Za-z0-9А-Яа-я_-]"), "_")
                .ifBlank { "mod" } + ".json"
            val out = File(dir(c), safeName)
            out.writeText(obj.toString(2))
            if (obj.optString("bg_image", "") == "gallery") {
                prefs(c).edit().putBoolean(KEY_GALLERY_PENDING, true).apply()
            }
            null
        } catch (e: Exception) {
            "Ошибка JSON: ${e.message ?: "проверь синтаксис"}"
        }
    }

    fun consumeGalleryRequest(c: Context): Boolean {
        val p = prefs(c)
        val pending = p.getBoolean(KEY_GALLERY_PENDING, false)
        if (pending) p.edit().putBoolean(KEY_GALLERY_PENDING, false).apply()
        return pending
    }

    fun clearGalleryRequest(c: Context) {
        prefs(c).edit().putBoolean(KEY_GALLERY_PENDING, false).apply()
    }

    fun load(c: Context): ModState {
        var name = ""
        var greeting: String? = null
        var accent: Int? = null
        var bgColor: Int? = null
        var bgImage: String? = null
        var order: List<String> = emptyList()
        val hidden = LinkedHashSet<String>()
        val buttons = ArrayList<ModButton>()
        var layout = ModLayout()
        val text = LinkedHashMap<String, String>()

        for (file in list(c)) {
            if (!isEnabled(c, file.name)) continue
            try {
                val obj = JSONObject(file.readText())
                if (obj.has("name")) name = obj.optString("name", name)
                if (obj.has("greeting")) greeting = obj.optString("greeting", "")
                if (obj.has("accent")) accent = parseColor(obj.optString("accent", ""))
                if (obj.has("bg")) bgColor = parseColor(obj.optString("bg", ""))
                if (obj.has("bg_image")) {
                    val image = obj.optString("bg_image", "")
                    bgImage = if (image == "gallery") {
                        prefs(c).getString(KEY_MOD_BG_IMAGE, null)
                    } else image.takeIf { it.isNotBlank() }
                }
                if (obj.has("order")) order = readStrings(obj.optJSONArray("order"))
                readStrings(obj.optJSONArray("hide")).forEach { hidden.add(it) }

                val arr = obj.optJSONArray("buttons")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        val b = arr.optJSONObject(i) ?: continue
                        val icon = b.optString("icon", "•")
                        val action = b.optString("action", "")
                        val arg = b.optString("arg", "")
                        if (action.isNotBlank()) buttons.add(ModButton(icon, action, arg))
                    }
                }

                val lo = obj.optJSONObject("layout")
                if (lo != null) {
                    val width = lo.optString("width", layout.width).lowercase(Locale.ROOT)
                    val safeWidth = if (width in listOf("wide", "narrow", "normal")) width else layout.width
                    val padding = if (lo.has("padding")) lo.optInt("padding", layout.padding).coerceIn(4, 40) else layout.padding
                    val scale = if (lo.has("scale")) lo.optDouble("scale", layout.scale.toDouble()).toFloat().coerceIn(0.65f, 1.35f) else layout.scale
                    layout = ModLayout(safeWidth, padding, scale)
                }

                val tx = obj.optJSONObject("text")
                if (tx != null) {
                    val keys = tx.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (tx.opt(key) is String) text[key] = tx.optString(key)
                    }
                }
            } catch (_: Exception) {
                // Повреждённый или неподдерживаемый мод пропускается, остальные продолжают работать.
            }
        }

        return ModState(
            name = name,
            greeting = greeting,
            accent = accent,
            bgColor = bgColor,
            bgImage = bgImage,
            order = order,
            hidden = hidden,
            buttons = buttons,
            layout = layout,
            text = text
        )
    }

    private fun readStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val result = ArrayList<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i, "").trim()
            if (value.isNotEmpty() && !result.contains(value)) result.add(value)
        }
        return result
    }

    private fun parseColor(value: String): Int? {
        val v = value.trim()
        if (v.isEmpty()) return null
        return try {
            Color.parseColor(if (v.startsWith("#")) v else "#$v")
        } catch (_: Exception) {
            null
        }
    }

    const val EXAMPLE = """{
  "name": "Неоновый слой",
  "bg": "#E6101724",
  "bg_image": "",
  "order": ["clock", "eyes", "buttons", "weather", "battery", "music", "volume", "brightness"],
  "hide": [],
  "buttons": [
    {"icon": "🔦", "action": "torch", "arg": ""},
    {"icon": "⚙️", "action": "settings", "arg": ""}
  ],
  "layout": {"width": "wide", "padding": 20, "scale": 1.0},
  "text": {"greeting": "Привет из нового мода!", "battery_label": "Заряд"}
}"""
}
