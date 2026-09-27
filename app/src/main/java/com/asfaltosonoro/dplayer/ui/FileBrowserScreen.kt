package com.asfaltosonoro.dplayer.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.asfaltosonoro.dplayer.settings.PlayerPreferencesHolder
import com.asfaltosonoro.dplayer.source.BrowseEntry
import com.asfaltosonoro.dplayer.source.SourceBrowser
import com.asfaltosonoro.dplayer.source.SourceType
import com.asfaltosonoro.dplayer.source.ftp.FtpBrowser
import com.asfaltosonoro.dplayer.source.local.SafFileBrowser
import com.asfaltosonoro.dplayer.source.upnp.UpnpBrowser
import kotlinx.coroutines.launch

/**
 * Browses one of the 3 shortcuts (USB/SD via SAF, FTP, UPnP — same
 * SourceBrowser contract for all three) and plays a track/folder/selection
 * via the shared MediaController. No local DB: every list() call hits the
 * backend live, per the "leggero e veloce" requirement.
 */
@Composable
fun FileBrowserScreen(
    shortcutIndex: Int,
    controller: MediaController?,
    onBack: () -> Unit,
    onConfigure: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferencesHolder.get(context) }
    val shortcut = remember { prefs.loadShortcut(shortcutIndex) }

    if (shortcut == null) {
        Column(modifier = Modifier.fillMaxSize().background(Color.Black).padding(24.dp)) {
            Text("Shortcut ${shortcutIndex + 1} not configured yet.", color = Color.White)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onConfigure) { Text("Configure now") }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onBack) { Text("Back") }
        }
        return
    }

    val browser: SourceBrowser = remember(shortcut) {
        when (shortcut.type) {
            SourceType.LOCAL_SAF -> SafFileBrowser(context)
            SourceType.FTP -> FtpBrowser()
            SourceType.UPNP -> UpnpBrowser(context)
        }
    }

    val pathStack = remember { mutableStateListOf(shortcut.uri to shortcut.label) }
    var entries by remember { mutableStateOf<List<BrowseEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var selectMode by remember { mutableStateOf(false) }
    val selected = remember { mutableStateListOf<BrowseEntry>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(pathStack.last().first) {
        loading = true
        entries = runCatching { browser.list(pathStack.last().first) }.getOrDefault(emptyList())
        loading = false
    }

    fun play(items: List<BrowseEntry>, startIndex: Int) {
        val audioItems = items.filter { it.isAudio }
        if (audioItems.isEmpty()) return
        val mediaItems = audioItems.map { entry ->
            MediaItem.Builder()
                .setUri(Uri.parse(browser.resolvePlaybackUri(entry)))
                .setMediaMetadata(MediaMetadata.Builder().setTitle(entry.name).build())
                .build()
        }
        val clampedStart = startIndex.coerceIn(0, mediaItems.lastIndex)
        controller?.setMediaItems(mediaItems, clampedStart, 0L)
        controller?.prepare()
        controller?.play()
        onBack()
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                if (pathStack.size > 1) pathStack.removeAt(pathStack.lastIndex) else onBack()
            }) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text(pathStack.last().second, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))

            if (pathStack.size == 1) {
                IconButton(onClick = {
                    scope.launch {
                        loading = true
                        val all = collectAudioRecursive(browser, shortcut.uri)
                        loading = false
                        play(all.shuffled(), 0)
                    }
                }) { Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle whole folder", tint = Color(0xFFDFF5E1)) }
            }
            IconButton(onClick = { selectMode = !selectMode; selected.clear() }) {
                Icon(Icons.Filled.Checklist, contentDescription = "Select multiple", tint = if (selectMode) Color(0xFFDFF5E1) else Color.Gray)
            }
        }

        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFDFF5E1))
            }
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(entries) { entry ->
                    val isSelected = selected.contains(entry)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (selectMode) {
                                    if (isSelected) selected.remove(entry) else selected.add(entry)
                                } else if (entry.isDirectory) {
                                    pathStack.add(entry.uri to entry.name)
                                } else {
                                    play(entries, entries.indexOf(entry))
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selectMode) {
                            Checkbox(checked = isSelected, onCheckedChange = {
                                if (it) selected.add(entry) else selected.remove(entry)
                            })
                        }
                        Icon(
                            if (entry.isDirectory) Icons.Filled.Folder else Icons.Filled.AudioFile,
                            contentDescription = null, tint = Color(0xFFDFF5E1),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(entry.name, color = Color.White)
                    }
                }
            }
        }

        if (selectMode && selected.isNotEmpty()) {
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        val gathered = mutableListOf<BrowseEntry>()
                        for (e in selected) {
                            if (e.isDirectory) gathered += collectAudioRecursive(browser, e.uri) else gathered += e
                        }
                        loading = false
                        play(gathered, 0)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) { Text("Play selected (${selected.size})") }
        }
    }
}

/** Depth-limited recursive audio gather — used for "shuffle mother folder" and multi-folder selection. */
private suspend fun collectAudioRecursive(browser: SourceBrowser, uri: String, depth: Int = 0, maxDepth: Int = 6): List<BrowseEntry> {
    if (depth > maxDepth) return emptyList()
    val entries = runCatching { browser.list(uri) }.getOrDefault(emptyList())
    val audio = entries.filter { it.isAudio }
    val subfolders = entries.filter { it.isDirectory }
    val nested = subfolders.flatMap { collectAudioRecursive(browser, it.uri, depth + 1, maxDepth) }
    return audio + nested
}
