package com.asfaltosonoro.dplayer.settings

import android.content.Context

/** App-wide single instance, avoids threading a PlayerPreferences param through every call site. */
object PlayerPreferencesHolder {
    @Volatile private var instance: PlayerPreferences? = null

    fun get(context: Context): PlayerPreferences =
        instance ?: synchronized(this) {
            instance ?: PlayerPreferences(context.applicationContext).also { instance = it }
        }
}
