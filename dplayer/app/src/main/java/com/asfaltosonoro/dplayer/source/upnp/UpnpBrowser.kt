package com.asfaltosonoro.dplayer.source.upnp

import android.content.Context
import com.asfaltosonoro.dplayer.source.BrowseEntry
import com.asfaltosonoro.dplayer.source.SourceBrowser
import kotlinx.coroutines.suspendCancellableCoroutine
import org.jupnp.model.action.ActionInvocation
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.RemoteService
import org.jupnp.model.types.UDAServiceType
import org.jupnp.support.contentdirectory.callback.Browse
import org.jupnp.support.model.BrowseFlag
import org.jupnp.support.model.DIDLContent
import org.jupnp.support.model.container.Container
import org.jupnp.support.model.item.Item
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Browses a UPnP/DLNA MediaServer's ContentDirectory (client/browsing only —
 * no renderer role: the head unit is always the controller + the sink, as
 * requested). Shortcut uri format: "upnp://<UDN>/<containerId>" — root
 * container id in the UPnP ContentDirectory spec is always "0".
 *
 * Uses jupnp's Browse callback (org.jupnp.support), which already parses the
 * DIDL-Lite XML into typed Container/Item objects — no manual XML handling.
 */
class UpnpBrowser(private val context: Context) : SourceBrowser {

    override suspend fun list(uri: String): List<BrowseEntry> {
        val (udn, containerId) = parseUpnpUri(uri) ?: return emptyList()
        val device = UpnpServiceHolder.findDeviceByUdn(udn) ?: return emptyList()
        val service = device.findService(UDAServiceType("ContentDirectory")) as? RemoteService ?: return emptyList()
        val upnpService = UpnpServiceHolder.upnpService.value ?: return emptyList()

        return suspendCancellableCoroutine { cont ->
            val browse = object : Browse(service, containerId, BrowseFlag.DIRECT_CHILDREN) {
                override fun received(actionInvocation: ActionInvocation<*>?, didl: DIDLContent) {
                    val entries = mutableListOf<BrowseEntry>()
                    for (container: Container in didl.containers) {
                        entries += BrowseEntry(
                            name = container.title ?: "?",
                            uri = "upnp://$udn/${container.id}",
                            isDirectory = true,
                        )
                    }
                    for (item: Item in didl.items) {
                        val isAudio = item.resources.firstOrNull()?.protocolInfo?.contentFormatMimeType?.type == "audio"
                        if (!isAudio) continue
                        val streamUrl = item.resources.firstOrNull()?.value ?: continue
                        entries += BrowseEntry(
                            name = item.title ?: "?",
                            uri = streamUrl,
                            isDirectory = false,
                            isAudio = true,
                        )
                    }
                    if (cont.isActive) cont.resume(entries)
                }

                override fun updateStatus(status: Status?) { /* no-op: single-shot browse, no progress UI */ }

                override fun failure(invocation: ActionInvocation<*>?, operation: UpnpResponse?, defaultMsg: String?) {
                    if (cont.isActive) cont.resumeWithException(RuntimeException(defaultMsg ?: "UPnP browse failed"))
                }
            }
            upnpService.controlPoint.execute(browse)
        }
    }

    /** Items resolve to their own stream URL directly (http://...), set as BrowseEntry.uri already. */
    override fun resolvePlaybackUri(entry: BrowseEntry): String = entry.uri

    companion object {
        fun parseUpnpUri(uri: String): Pair<String, String>? {
            val withoutScheme = uri.removePrefix("upnp://")
            val parts = withoutScheme.split("/", limit = 2)
            if (parts.size < 2) return null
            return parts[0] to parts[1]
        }
    }
}
