package com.asfaltosonoro.dplayer.player

import android.content.Context
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import com.asfaltosonoro.dplayer.source.ftp.FtpDataSource

/**
 * Dispatches by URI scheme: content:// (SAF, USB/SD) and http(s):// (UPnP
 * stream URLs) go through Media3's own DefaultDataSource; ftp:// goes
 * through our FtpDataSource. One factory, all three shortcut source types
 * play through the same ExoPlayer/MediaSession pipeline.
 */
class MultiSchemeDataSourceFactory(context: Context) : DataSource.Factory {
    private val default = DefaultDataSource.Factory(context)
    private val ftp = FtpDataSource.Factory()

    override fun createDataSource(): DataSource = SchemeDispatchingDataSource(default.createDataSource(), ftp.createDataSource())
}

private class SchemeDispatchingDataSource(
    private val defaultDataSource: DataSource,
    private val ftpDataSource: DataSource,
) : DataSource by defaultDataSource {

    private var active: DataSource = defaultDataSource

    override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
        active = if (dataSpec.uri.scheme == "ftp") ftpDataSource else defaultDataSource
        return active.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active.read(buffer, offset, length)
    override fun getUri() = active.uri
    override fun close() = active.close()
}
