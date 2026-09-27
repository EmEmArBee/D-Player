package com.asfaltosonoro.dplayer.source

/** Where a folder shortcut points to. */
enum class SourceType { LOCAL_SAF, FTP, UPNP }

/** One of the 3 configurable top-bar shortcuts. */
data class FolderShortcut(
    val id: Int,               // 0, 1, 2
    val label: String,
    val type: SourceType,
    val uri: String,           // SAF tree uri, or ftp://user:pass@host/path, or UPnP container id/UDN
)

/** A single browsable/playable entry inside a source (file or subfolder). */
data class BrowseEntry(
    val name: String,
    val uri: String,
    val isDirectory: Boolean,
    val isAudio: Boolean = false,
)

/** Common contract every source backend (SAF/FTP/UPnP) implements. */
interface SourceBrowser {
    suspend fun list(uri: String): List<BrowseEntry>
    /** Resolves a playable entry to a URI ExoPlayer can open directly (content://, ftp://, http://). */
    fun resolvePlaybackUri(entry: BrowseEntry): String
}
