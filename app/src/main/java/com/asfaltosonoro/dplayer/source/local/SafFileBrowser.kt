package com.asfaltosonoro.dplayer.source.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.asfaltosonoro.dplayer.source.BrowseEntry
import com.asfaltosonoro.dplayer.source.SourceBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AUDIO_EXT = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg")

/**
 * Browses a folder tree granted via ACTION_OPEN_DOCUMENT_TREE (SAF).
 * Used uniformly across API 23+ for USB/SD shortcuts: simplest single code
 * path, persisted permission survives reboots, and DocumentFile listing is
 * plenty fast for the "browse one folder at a time" UX we want (no local DB).
 */
class SafFileBrowser(private val context: Context) : SourceBrowser {

    override suspend fun list(uri: String): List<BrowseEntry> = withContext(Dispatchers.IO) {
        val dir = DocumentFile.fromTreeUri(context, Uri.parse(uri)) ?: return@withContext emptyList()
        dir.listFiles()
            .filter { it.isDirectory || (it.name?.substringAfterLast('.', "")?.lowercase() in AUDIO_EXT) }
            .sortedWith(compareByDescending<DocumentFile> { it.isDirectory }.thenBy { it.name })
            .map {
                BrowseEntry(
                    name = it.name ?: "?",
                    uri = it.uri.toString(),
                    isDirectory = it.isDirectory,
                    isAudio = !it.isDirectory,
                )
            }
    }

    override fun resolvePlaybackUri(entry: BrowseEntry): String = entry.uri
}
