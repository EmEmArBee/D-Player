package com.asfaltosonoro.dplayer.source.ftp

import com.asfaltosonoro.dplayer.source.BrowseEntry
import com.asfaltosonoro.dplayer.source.SourceBrowser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile

private val AUDIO_EXT = setOf("mp3", "flac", "wav", "aac", "m4a", "ogg")

/**
 * Browses/streams a plain FTP source (target: Primitive-FTPd running on the
 * user's phone as the "server"). Anonymous or user/pass, parsed from the
 * shortcut's ftp://user:pass@host:port/path uri.
 *
 * NOTE: this is a browsing-only client. Media3 opens the resolved ftp:// uri
 * directly via a custom DataSource (see player/FtpDataSource.kt, TODO) — for
 * the scaffold we resolve to an ftp:// string and wire the DataSource next.
 */
class FtpBrowser : SourceBrowser {

    override suspend fun list(uri: String): List<BrowseEntry> = withContext(Dispatchers.IO) {
        val parsed = FtpUri.parse(uri)
        val client = FTPClient()
        try {
            client.connect(parsed.host, parsed.port)
            client.login(parsed.user ?: "anonymous", parsed.pass ?: "")
            client.enterLocalPassiveMode()
            val files: Array<FTPFile> = client.listFiles(parsed.path)
            files
                .filter { it.isDirectory || (it.name.substringAfterLast('.', "").lowercase() in AUDIO_EXT) }
                .sortedWith(compareByDescending<FTPFile> { it.isDirectory }.thenBy { it.name })
                .map {
                    val childUri = parsed.child(it.name)
                    BrowseEntry(name = it.name, uri = childUri, isDirectory = it.isDirectory, isAudio = !it.isDirectory)
                }
        } finally {
            runCatching { client.logout() }
            runCatching { client.disconnect() }
        }
    }

    override fun resolvePlaybackUri(entry: BrowseEntry): String = entry.uri
}

data class FtpUri(val host: String, val port: Int, val user: String?, val pass: String?, val path: String) {
    fun child(name: String): String {
        val userinfo = if (user != null) "$user${pass?.let { ":$it" } ?: ""}@" else ""
        val newPath = if (path.endsWith("/")) "$path$name" else "$path/$name"
        return "ftp://$userinfo$host:$port$newPath"
    }

    companion object {
        fun parse(uri: String): FtpUri {
            val u = java.net.URI(uri)
            val userInfo = u.userInfo?.split(":")
            return FtpUri(
                host = u.host,
                port = if (u.port > 0) u.port else 21,
                user = userInfo?.getOrNull(0),
                pass = userInfo?.getOrNull(1),
                path = u.path.ifEmpty { "/" },
            )
        }
    }
}
