package com.winamp.reborn

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import org.json.JSONArray
import org.json.JSONObject

data class Track(
    val id: String,
    val uri: Uri,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val added: Long,
    val isSystem: Boolean
)

fun Track.toItem(): MediaItem =
    MediaItem.Builder().setMediaId(id).setUri(uri)
        .setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
        .build()

fun loadLibrary(ctx: Context): List<Track> {
    val base = if (Build.VERSION.SDK_INT >= 29)
        MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    val proj = arrayOf(
        MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST,
        MediaStore.Audio.Media.DURATION,
        MediaStore.Audio.Media.DATE_ADDED,
        MediaStore.Audio.Media.IS_RINGTONE,
        MediaStore.Audio.Media.IS_NOTIFICATION,
        MediaStore.Audio.Media.IS_ALARM
    )
    val out = ArrayList<Track>()
    runCatching {
        ctx.contentResolver.query(base, proj, null, null, null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val iRing = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_RINGTONE)
            val iNotif = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_NOTIFICATION)
            val iAlarm = c.getColumnIndexOrThrow(MediaStore.Audio.Media.IS_ALARM)
            while (c.moveToNext()) {
                val uri = ContentUris.withAppendedId(base, c.getLong(iId))
                val artist = c.getString(iArtist)?.takeIf { it != MediaStore.UNKNOWN_STRING } ?: ""
                val system = c.getInt(iRing) != 0 || c.getInt(iNotif) != 0 || c.getInt(iAlarm) != 0
                out.add(
                    Track(
                        uri.toString(), uri, c.getString(iTitle) ?: "Track", artist,
                        c.getLong(iDur), c.getLong(iAdded), system
                    )
                )
            }
        }
    }
    return out
}

class PlaylistStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("playlists", Context.MODE_PRIVATE)

    fun load(): Map<String, List<String>> {
        val s = prefs.getString("data", null) ?: return emptyMap()
        return runCatching {
            val o = JSONObject(s)
            o.keys().asSequence().associateWith { k ->
                val a = o.getJSONArray(k)
                (0 until a.length()).map { a.getString(it) }
            }
        }.getOrDefault(emptyMap())
    }

    fun save(m: Map<String, List<String>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONArray(v)) }
        prefs.edit().putString("data", o.toString()).apply()
    }
}

class FilterPrefs(ctx: Context) {
    private val p = ctx.getSharedPreferences("filters", Context.MODE_PRIVATE)

    var minSec: Int
        get() = p.getInt("minSec", 30)
        set(v) { p.edit().putInt("minSec", v).apply() }

    var sort: Int
        get() = p.getInt("sort", 0)
        set(v) { p.edit().putInt("sort", v).apply() }

    var hideSystem: Boolean
        get() = p.getBoolean("hideSystem", true)
        set(v) { p.edit().putBoolean("hideSystem", v).apply() }
}
