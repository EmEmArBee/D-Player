package com.asfaltosonoro.dplayer.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.asfaltosonoro.dplayer.settings.PlayerPreferencesHolder
import com.asfaltosonoro.dplayer.source.FolderShortcut
import com.asfaltosonoro.dplayer.source.SourceType
import com.asfaltosonoro.dplayer.source.local.LocalPathBrowser

@Composable
fun ShortcutConfigScreen(index: Int, onDone: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferencesHolder.get(context) }
    val existing = remember { prefs.loadShortcut(index) }

    var type by remember { mutableStateOf(existing?.type ?: SourceType.LOCAL_SAF) }
    var label by remember { mutableStateOf(existing?.label ?: "Shortcut ${index + 1}") }
    var localUri by remember { mutableStateOf(existing?.uri?.takeIf { type == SourceType.LOCAL_SAF }) }
    var manualPath by remember { mutableStateOf(existing?.uri?.takeIf { LocalPathBrowser.isLocalPathUri(it) }?.removePrefix("localpath://") ?: "") }
    var pickerError by remember { mutableStateOf<String?>(null) }

    var ftpHost by remember { mutableStateOf("") }
    var ftpPort by remember { mutableStateOf("21") }
    var ftpUser by remember { mutableStateOf("") }
    var ftpPass by remember { mutableStateOf("") }
    var ftpPath by remember { mutableStateOf("/") }

    var upnpUdn by remember { mutableStateOf("") }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            localUri = uri.toString()
            val name = DocumentFile.fromTreeUri(context, uri)?.name
            if (!name.isNullOrBlank()) label = name
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDone) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) }
            Text("Configure shortcut ${index + 1}", color = Color.White, fontSize = 20.sp)
        }

        OutlinedTextField(
            value = label, onValueChange = { label = it },
            label = { Text("Label") }, modifier = Modifier.fillMaxWidth().padding(16.dp, 4.dp),
        )

        TabRow(selectedTabIndex = SourceType.entries.indexOf(type)) {
            SourceType.entries.forEach { t ->
                Tab(selected = type == t, onClick = { type = t }, text = { Text(t.name) })
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            when (type) {
                SourceType.LOCAL_SAF -> {
                    Text(localUri?.let { "Folder: $it" } ?: "No folder chosen yet", color = Color.Gray)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = {
                        pickerError = null
                        // Some stripped-down head unit ROMs have no
                        // DocumentsUI app at all to handle this intent —
                        // without the try/catch this crashes the whole app
                        // instead of just failing to open a picker.
                        runCatching { pickFolder.launch(null) }
                            .onFailure { pickerError = "Nessuna app di selezione cartelle trovata su questo dispositivo — usa il percorso manuale qui sotto." }
                    }) { Text("Choose folder (USB/SD)") }
                    pickerError?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = Color(0xFFFF8A65), fontSize = 12.sp)
                    }

                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = Color(0xFF333333))
                    Spacer(Modifier.height(12.dp))
                    Text("Oppure percorso manuale (se la scelta cartella non funziona)", color = Color.Gray, fontSize = 12.sp)
                    OutlinedTextField(
                        value = manualPath, onValueChange = { manualPath = it },
                        label = { Text("es. /storage/usb0/Music") }, modifier = Modifier.fillMaxWidth(),
                    )
                    TextButton(onClick = {
                        if (manualPath.isNotBlank()) {
                            localUri = LocalPathBrowser.wrap(manualPath.trim())
                            pickerError = null
                        }
                    }) { Text("Usa questo percorso") }
                }
                SourceType.FTP -> {
                    OutlinedTextField(ftpHost, { ftpHost = it }, label = { Text("Host / IP") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ftpPort, { ftpPort = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ftpUser, { ftpUser = it }, label = { Text("User (blank = anonymous)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ftpPass, { ftpPass = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(ftpPath, { ftpPath = it }, label = { Text("Start path") }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "Compatibile con Primitive-FTPd sul telefono: usa l'indirizzo/porta mostrati lì.",
                        color = Color.Gray, fontSize = 12.sp,
                    )
                }
                SourceType.UPNP -> {
                    OutlinedTextField(upnpUdn, { upnpUdn = it }, label = { Text("Server UDN") }, modifier = Modifier.fillMaxWidth())
                    Text(
                        "UPnP browsing è ancora in sviluppo (vedi TODO in UpnpBrowser.kt) — per ora inserisci manualmente lo UDN del media server.",
                        color = Color.Gray, fontSize = 12.sp,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val uri = when (type) {
                        SourceType.LOCAL_SAF -> localUri ?: return@Button
                        SourceType.FTP -> {
                            val userinfo = if (ftpUser.isNotBlank()) "$ftpUser${if (ftpPass.isNotBlank()) ":$ftpPass" else ""}@" else ""
                            "ftp://$userinfo$ftpHost:${ftpPort.ifBlank { "21" }}${if (ftpPath.startsWith("/")) ftpPath else "/$ftpPath"}"
                        }
                        SourceType.UPNP -> "upnp://$upnpUdn/0"
                    }
                    prefs.saveShortcut(FolderShortcut(index, label, type, uri))
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
            Spacer(Modifier.height(32.dp))
        }
    }
}
