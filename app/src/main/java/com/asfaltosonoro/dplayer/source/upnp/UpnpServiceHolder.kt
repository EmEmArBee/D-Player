package com.asfaltosonoro.dplayer.source.upnp

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jupnp.android.AndroidUpnpService
import org.jupnp.model.meta.Device

/**
 * App-wide bind to jupnp's AndroidUpnpService (declared in the manifest).
 * One bind for the whole app process; UpnpBrowser instances just read the
 * current UpnpService off upnpService.value rather than each managing their
 * own connection. Also drives discovery (search()) so the shortcut-config
 * screen can eventually list found servers.
 */
object UpnpServiceHolder {
    private val _upnpService = MutableStateFlow<AndroidUpnpService?>(null)
    val upnpService: StateFlow<AndroidUpnpService?> = _upnpService

    private const val TAG = "UpnpServiceHolder"

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            runCatching {
                val s = service as? AndroidUpnpService ?: return
                _upnpService.value = s
                s.get().controlPoint.search()
            }.onFailure { Log.e(TAG, "jupnp init failed, UPnP shortcuts won't work this session", it) }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _upnpService.value = null
        }
    }

    /**
     * Never lets a jupnp failure take the whole app down with it — UPnP is
     * one of three optional source types, not core functionality. If this
     * fails, UPnP shortcuts simply won't browse; USB/SD and FTP are
     * unaffected. Runs off the main thread since jupnp's Android transport
     * does blocking network setup (multicast socket, etc.) on bind/create.
     */
    fun bind(context: Context) {
        Thread {
            runCatching {
                context.applicationContext.bindService(
                    Intent(context, org.jupnp.android.AndroidUpnpServiceImpl::class.java),
                    connection,
                    Context.BIND_AUTO_CREATE,
                )
            }.onFailure { Log.e(TAG, "Could not bind AndroidUpnpServiceImpl, UPnP disabled this session", it) }
        }.start()
    }

    fun findDeviceByUdn(udn: String): Device<*, *, *>? {
        val registry = _upnpService.value?.get()?.registry ?: return null
        return registry.devices.firstOrNull { it.identity.udn.identifierString == udn }
    }
}
