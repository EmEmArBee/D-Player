package com.asfaltosonoro.dplayer.source.local

import com.asfaltosonoro.dplayer.source.BrowseEntry
import com.asfaltosonoro.dplayer.source.SourceBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val AUDIO_EXT = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg")
private const val SCHEME = "localpath://"

/**
 * Fallback for USB/SD when the device has no SAF document picker at all —
 * some stripped-down aftermarket head unit ROMs genuinely lack a
 * DocumentsUI app, so ACTION_OPEN_DOCUMENT_TREE has nothing to resolve to
 * and crashes instead of showing a picker. This browses a manually-typed
 * absolute path with plain java.io.File instead of SAF.
 *
 * Needs android:requestLegacyExternalStorage="true" (manifest) and
 * READ_EXTERNAL_STORAGE granted to actually see anything outside the app's
 * own sandbox on API 29; on API 30+ scoped storage blocks this path
 * regardless of the permission (would need MANAGE_EXTERNAL_STORAGE, not
 * requested here — SAF is still the primary path on those devices).
 */
class LocalPathBrowser : SourceBrowser {

    override suspend fun list(uri: String): List<BrowseEntry> = withContext(Dispatchers.IO) {
        val path = uri.removePrefix(SCHEME)
        val dir = File(path)
        val children = dir.listFiles() ?: return@withContext emptyList()
        children
            .filter { it.isDirectory || it.extension.lowercase() in AUDIO_EXT }
            .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name })
            .map {
                BrowseEntry(
                    name = it.name,
                    uri = SCHEME + it.absolutePath,
                    isDirectory = it.isDirectory,
                    isAudio = !it.isDirectory,
                )
            }
    }

    /** ExoPlayer's default DataSource handles file:// out of the box. */
    override fun resolvePlaybackUri(entry: BrowseEntry): String = "file://" + entry.uri.removePrefix(SCHEME)

    companion object {
        fun isLocalPathUri(uri: String) = uri.startsWith(SCHEME)
        fun wrap(path: String) = SCHEME + path
    }
}
