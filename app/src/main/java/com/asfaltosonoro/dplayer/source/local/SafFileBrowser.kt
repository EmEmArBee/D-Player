package com.asfaltosonoro.dplayer.source.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.asfaltosonoro.dplayer.source.BrowseEntry
import com.asfaltosonoro.dplayer.source.SourceBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AUDIO_EXT = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg")

/**
 * Browses a folder tree granted via ACTION_OPEN_DOCUMENT_TREE (SAF).
 *
 * Deliberately NOT using DocumentFile here: DocumentFile.listFiles() does
 * one query to list child URIs, but then EVERY property access on each
 * resulting DocumentFile (isDirectory, name, ...) fires its own separate
 * ContentProvider IPC round-trip — for a folder with 100 tracks that's 100+
 * queries, which is exactly the "half a minute to open a folder" symptom.
 * A single DocumentsContract query with an explicit projection returns
 * every child's name/mime/id in one cursor pass instead.
 */
class SafFileBrowser(private val context: Context) : SourceBrowser {

    override suspend fun list(uri: String): List<BrowseEntry> = withContext(Dispatchers.IO) {
        val parsed = Uri.parse(uri)
        val parentDocumentId = runCatching { DocumentsContract.getDocumentId(parsed) }.getOrNull()
            ?: return@withContext emptyList()
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parsed, parentDocumentId)

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )

        val entries = mutableListOf<BrowseEntry>()
        runCatching {
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol) ?: continue
                    val mime = cursor.getString(mimeCol)
                    val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                    if (!isDir && name.substringAfterLast('.', "").lowercase() !in AUDIO_EXT) continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(parsed, docId)
                    entries += BrowseEntry(name = name, uri = childUri.toString(), isDirectory = isDir, isAudio = !isDir)
                }
            }
        }

        entries.sortedWith(compareByDescending<BrowseEntry> { it.isDirectory }.thenBy { it.name })
    }

    override fun resolvePlaybackUri(entry: BrowseEntry): String = entry.uri
}
