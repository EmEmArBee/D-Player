package com.asfaltosonoro.dplayer.source.ftp

import androidx.media3.common.C
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import android.net.Uri
import org.apache.commons.net.ftp.FTPClient
import java.io.InputStream

/**
 * Minimal sequential-read DataSource for ftp:// media items (the scheme our
 * FtpBrowser resolves entries to). FTP streaming is inherently sequential —
 * FTPClient.retrieveFileStream() gives us an InputStream with no seek — so
 * this supports forward playback only. Random seeks (scrubbing) will re-open
 * the connection from byte 0 and skip forward, which is slow on a slow link
 * but correct; acceptable trade-off for a head unit source that's mostly
 * played start-to-end rather than scrubbed.
 */
class FtpDataSource : BaseDataSource(/* isNetwork = */ true) {

    private var client: FTPClient? = null
    private var stream: InputStream? = null
    private var uri: Uri? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()

    override fun open(dataSpec: DataSpec): Long {
        uri = dataSpec.uri
        val parsed = FtpUri.parse(dataSpec.uri.toString())
        val c = FTPClient()
        c.connect(parsed.host, parsed.port)
        c.login(parsed.user ?: "anonymous", parsed.pass ?: "")
        c.enterLocalPassiveMode()
        c.setFileType(org.apache.commons.net.ftp.FTP.BINARY_FILE_TYPE)

        if (dataSpec.position > 0) {
            c.restartOffset = dataSpec.position
        }
        val input = c.retrieveFileStream(parsed.path)
            ?: throw java.io.IOException("FTP: impossibile aprire ${parsed.path}")

        client = c
        stream = input
        bytesRemaining = if (dataSpec.length != C.LENGTH_UNSET.toLong()) dataSpec.length else C.LENGTH_UNSET.toLong()
        transferStarted(dataSpec)
        return bytesRemaining
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        val toRead = if (bytesRemaining != C.LENGTH_UNSET.toLong()) minOf(length.toLong(), bytesRemaining).toInt() else length
        val read = stream?.read(buffer, offset, toRead) ?: -1
        if (read == -1) {
            return C.RESULT_END_OF_INPUT
        }
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri() = uri

    override fun close() {
        runCatching { stream?.close() }
        runCatching { client?.completePendingCommand() }
        runCatching { client?.logout() }
        runCatching { client?.disconnect() }
        stream = null
        client = null
        transferEnded()
    }

    class Factory : DataSource.Factory {
        override fun createDataSource(): DataSource = FtpDataSource()
    }
}
