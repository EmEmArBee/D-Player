package com.asfaltosonoro.dplayer.settings

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.MediaItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the current queue + position so the app resumes where it left
 * off (important for a head-unit: you get in the car, you don't want to
 * rebuild the playlist from scratch). No local DB — one JSON blob in
 * SharedPreferences, same philosophy as everything else here.
 */
class PlaybackStateStore(context: Context) {
    private val prefs = context.getSharedPreferences("playback_state", Context.MODE_PRIVATE)

    data class SavedItem(val uri: String, val title: String)
    data class SavedState(val items: List<SavedItem>, val index: Int, val positionMs: Long)

    fun save(items: List<MediaItem>, currentIndex: Int, positionMs: Long) {
        if (items.isEmpty()) return
        val arr = JSONArray()
        for (item in items) {
            val uri = item.localConfiguration?.uri?.toString() ?: continue
            val o = JSONObject()
            o.put("uri", uri)
            o.put("title", item.mediaMetadata.title?.toString() ?: "")
            arr.put(o)
        }
        prefs.edit {
            putString("queue", arr.toString())
            putInt("index", currentIndex.coerceIn(0, items.lastIndex))
            putLong("position", positionMs.coerceAtLeast(0L))
        }
    }

    fun load(): SavedState? {
        val queueJson = prefs.getString("queue", null) ?: return null
        val arr = runCatching { JSONArray(queueJson) }.getOrNull() ?: return null
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            SavedItem(uri, o.optString("title"))
        }
        if (items.isEmpty()) return null
        return SavedState(items, prefs.getInt("index", 0), prefs.getLong("position", 0L))
    }

    fun clear() = prefs.edit { clear() }
}
