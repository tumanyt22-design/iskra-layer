package com.iskra.layer

import android.content.Context

object LayerPrefs {
    private const val FILE = "layer"
    private fun sp(c: Context) = c.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isEnabled(c: Context): Boolean = sp(c).getBoolean("enabled", true)
    fun setEnabled(c: Context, value: Boolean) { sp(c).edit().putBoolean("enabled", value).apply() }
    fun getInt(c: Context, key: String, def: Int): Int = sp(c).getInt(key, def)
    fun putInt(c: Context, key: String, value: Int) { sp(c).edit().putInt(key, value).apply() }
}
