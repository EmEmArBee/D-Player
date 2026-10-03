package com.asfaltosonoro.dplayer

import android.app.Application
import com.asfaltosonoro.dplayer.source.upnp.UpnpServiceHolder

class DPlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        UpnpServiceHolder.bind(this)
    }
}
