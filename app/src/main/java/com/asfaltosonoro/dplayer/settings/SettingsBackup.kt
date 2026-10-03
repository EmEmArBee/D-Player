package com.asfaltosonoro.dplayer.settings

import android.content.Context
import androidx.core.content.edit
import org.json.JSONObject

/**
 * Dumps/restores every SharedPreferences file this app uses, as one JSON
 * blob. Generic by design (reads prefs.all / writes back by runtime type)
 * so a new setting added later is automatically included without having to
 * remember to update this file too. Deliberately excludes playback_state —
 * exporting "what I was playing" isn't really a "setting" to carry between
 * installs/devices.
 */
object SettingsBackup {
    private val PREF_FILES = listOf("player_prefs", "skin", "visualizer_appearance")

    fun export(context: Context): String {
        val root = JSONObject()
        for (name in PREF_FILES) {
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val obj = JSONObject()
            for ((key, value) in prefs.all) {
                when (value) {
                    is Boolean -> obj.put(key, value)
                    is Int -> obj.put(key, value)
                    is Long -> obj.put(key, value)
                    is Float -> obj.put(key, value.toDouble())
                    is String -> obj.put(key, value)
                    else -> Unit // StringSet etc. — unused by this app, skip
                }
            }
            root.put(name, obj)
        }
        return root.toString(2)
    }

    /** @return true if at least one recognized prefs section was restored. */
    fun import(context: Context, json: String): Boolean {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return false
        var restored = false
        for (name in PREF_FILES) {
            val obj = root.optJSONObject(name) ?: continue
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            prefs.edit {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    when (val v = obj.get(k)) {
                        is Boolean -> putBoolean(k, v)
                        is Int -> putInt(k, v)
                        is Long -> putLong(k, v)
                        is Double -> putFloat(k, v.toFloat())
                        is String -> putString(k, v)
                    }
                }
            }
            restored = true
        }
        return restored
    }
}
