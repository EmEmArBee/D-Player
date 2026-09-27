package com.asfaltosonoro.dplayer.source.upnp

import android.content.Context
import com.asfaltosonoro.dplayer.source.BrowseEntry
import com.asfaltosonoro.dplayer.source.SourceBrowser

/**
 * Browses a UPnP/DLNA MediaServer (e.g. a DLNA server app on the phone) using
 * jupnp's ContentDirectory service (client/browsing only — no renderer role,
 * as requested: the head unit is always the controller + the sink).
 *
 * TODO (post-scaffold):
 *  - start an AndroidUpnpService bound in DPlayerApp, keep an UpnpService
 *    instance alive, run a ContentDirectory "Browse" SOAP action per list()
 *  - map <container> -> BrowseEntry(isDirectory=true), <item audioItem> ->
 *    BrowseEntry(isAudio=true), uri = the res@protocolInfo http stream URL
 *  - resolvePlaybackUri just returns that http:// url, ExoPlayer plays it
 *    directly as a normal HTTP source (no custom DataSource needed)
 *
 * Left as a stub for the scaffold so the module compiles and the shortcut
 * type is wired end-to-end in the UI; browsing logic to fill in next pass.
 */
class UpnpBrowser(private val context: Context) : SourceBrowser {

    override suspend fun list(uri: String): List<BrowseEntry> {
        // uri format for UPnP shortcuts: upnp://<UDN>/<containerId>
        return emptyList() // TODO: jupnp ContentDirectory Browse call
    }

    override fun resolvePlaybackUri(entry: BrowseEntry): String = entry.uri
}
