package vn.daopham.codplayer

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import android.util.Size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream

enum class Fmt(val label: String, val color: Color) {
    FLAC("FLAC", Color(0xFFFFC23D)),
    WAV("WAV", Color(0xFF4FD8FF)),
    MP3("MP3", Color(0xFFFF5A5F)),
    AAC("AAC", Color(0xFF8CFF7A)),
    OGG("OGG", Color(0xFFC08CFF)),
    OTHER("FILE", Color(0xFFB0B4C0));

    companion object {
        fun of(ext: String): Fmt = when (ext.lowercase()) {
            "flac" -> FLAC
            "wav", "wave" -> WAV
            "mp3" -> MP3
            "m4a", "aac", "mp4", "alac" -> AAC
            "ogg", "opus", "oga" -> OGG
            else -> OTHER
        }
    }
}

data class Track(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val fileName: String,
    val folder: String,
    val ext: String,
    val size: Long,
    val bitrate: Int,
    val dateAdded: Long,
) {
    val fmt: Fmt = Fmt.of(ext)
    val display: String get() = if (artist.isBlank()) title else "$artist - $title"

    fun toMediaItem(): MediaItem {
        val art = if (albumId > 0) ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId) else null
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artist.ifBlank { "COD Player" })
                    .setAlbumTitle(album)
                    .setArtworkUri(art)
                    .build()
            )
            .build()
    }
}

object Library {
    var tracks by mutableStateOf<List<Track>>(emptyList())
    var loading by mutableStateOf(false)
    var scanned by mutableStateOf(false)

    suspend fun scan(ctx: Context) {
        loading = true
        val list = withContext(Dispatchers.IO) {
            try { query(ctx) } catch (e: Exception) { emptyList() }
        }
        tracks = list
        loading = false
        scanned = true
    }

    private fun query(ctx: Context): List<Track> {
        val out = ArrayList<Track>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
        )
        if (Build.VERSION.SDK_INT >= 29) cols += MediaStore.Audio.Media.RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= 30) cols += MediaStore.Audio.Media.BITRATE
        val sel = "${MediaStore.Audio.Media.DURATION} >= ? AND ${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0"
        ctx.contentResolver.query(base, cols.toTypedArray(), sel, arrayOf((Ui.minDur * 1000).toString()), null)?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val iTitle = c.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val iArtist = c.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val iAlbum = c.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = c.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
            val iDur = c.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val iName = c.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)
            val iSize = c.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val iDate = c.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
            val iData = c.getColumnIndex(MediaStore.Audio.Media.DATA)
            val iRel = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            val iBit = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.BITRATE) else -1
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                val data = if (iData >= 0) c.getString(iData) ?: "" else ""
                val name = (if (iName >= 0) c.getString(iName) else null) ?: data.substringAfterLast('/')
                val ext = name.substringAfterLast('.', "").lowercase()
                var title = if (iTitle >= 0) c.getString(iTitle) ?: "" else ""
                if (title.isBlank()) title = name.substringBeforeLast('.')
                var artist = if (iArtist >= 0) c.getString(iArtist) ?: "" else ""
                if (artist == "<unknown>") artist = ""
                val rel = if (iRel >= 0) c.getString(iRel) ?: "" else ""
                val folder = rel.trim('/').ifBlank {
                    data.substringBeforeLast('/', "").removePrefix("/storage/emulated/0").trim('/')
                }.ifBlank { "Bộ nhớ máy" }
                out += Track(
                    id = id,
                    uri = ContentUris.withAppendedId(base, id),
                    title = title,
                    artist = artist,
                    album = if (iAlbum >= 0) c.getString(iAlbum) ?: "" else "",
                    albumId = if (iAlbumId >= 0) c.getLong(iAlbumId) else 0L,
                    durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                    fileName = name,
                    folder = folder,
                    ext = ext,
                    size = if (iSize >= 0) c.getLong(iSize) else 0L,
                    bitrate = if (iBit >= 0) c.getInt(iBit) else 0,
                    dateAdded = if (iDate >= 0) c.getLong(iDate) else 0L,
                )
            }
        }
        return out
    }
}

object Art {
    private val cache = LruCache<Long, Bitmap>(30)
    private val missing: MutableSet<Long> = java.util.Collections.synchronizedSet(HashSet<Long>())

    fun load(ctx: Context, t: Track): Bitmap? {
        cache.get(t.id)?.let { return it }
        if (t.id in missing) return null
        var bmp: Bitmap? = null
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, t.uri)
                r.embeddedPicture?.let { bmp = decode(it, 720) }
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        } catch (_: Throwable) {}
        if (bmp == null && Build.VERSION.SDK_INT >= 29) {
            try { bmp = ctx.contentResolver.loadThumbnail(t.uri, Size(640, 640), null) } catch (_: Throwable) {}
        }
        val b = bmp
        if (b != null) cache.put(t.id, b) else missing.add(t.id)
        return b
    }

    private val thumbs = LruCache<Long, Bitmap>(400)
    private val thumbMissing = HashSet<Long>()

    /** Ảnh bìa nhỏ cho danh sách. */
    fun thumb(ctx: Context, t: Track): Bitmap? {
        thumbs.get(t.id)?.let { return it }
        synchronized(thumbMissing) { if (t.id in thumbMissing) return null }
        var bmp: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 29) {
            try { bmp = ctx.contentResolver.loadThumbnail(t.uri, Size(144, 144), null) } catch (_: Throwable) {}
        }
        if (bmp == null) {
            load(ctx, t)?.let { big -> bmp = Bitmap.createScaledBitmap(big, 144, 144 * big.height / maxOf(1, big.width), true) }
        }
        val b = bmp
        if (b != null) thumbs.put(t.id, b) else synchronized(thumbMissing) { thumbMissing.add(t.id) }
        return b
    }

    private fun decode(bytes: ByteArray, max: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= max && o.outHeight / (s * 2) >= max) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }
}

data class Tech(val sampleRate: Int, val bits: Int, val channels: Int, val kbps: Int) {
    val hiRes: Boolean get() = bits > 16 || sampleRate > 48000
}

object TechReader {
    private val cache = java.util.concurrent.ConcurrentHashMap<Long, Tech>()

    fun read(ctx: Context, t: Track): Tech {
        cache[t.id]?.let { return it }
        var sr = 0; var bits = 0; var chs = 0; var kbps = 0
        try {
            ctx.contentResolver.openInputStream(t.uri)?.use { ins ->
                val head = ByteArray(12)
                if (readFully(ins, head, 12) == 12) {
                    var off = 0
                    if (head[0] == 'I'.code.toByte() && head[1] == 'D'.code.toByte() && head[2] == '3'.code.toByte()) {
                        val sz = ((head[6].toInt() and 0x7f) shl 21) or ((head[7].toInt() and 0x7f) shl 14) or
                            ((head[8].toInt() and 0x7f) shl 7) or (head[9].toInt() and 0x7f)
                        skipFully(ins, (sz + 10 - 12).toLong())
                        off = -1
                    }
                    if (off == -1) {
                        val b = ByteArray(4); readFully(ins, b, 4)
                        if (String(b, Charsets.ISO_8859_1) == "fLaC") {
                            parseFlac(ins)?.let { sr = it[0]; chs = it[1]; bits = it[2] }
                        }
                    } else if (String(head, 0, 4, Charsets.ISO_8859_1) == "fLaC") {
                        // đã đọc 8 byte của khối STREAMINFO: 4 byte header + 4 byte đầu
                        val rest = ByteArray(34 - 4); readFully(ins, rest, rest.size)
                        val si = ByteArray(34); System.arraycopy(head, 8, si, 0, 4); System.arraycopy(rest, 0, si, 4, rest.size)
                        parseStreamInfo(si).let { sr = it[0]; chs = it[1]; bits = it[2] }
                    } else if (String(head, 0, 4, Charsets.ISO_8859_1) == "RIFF" && String(head, 8, 4, Charsets.ISO_8859_1) == "WAVE") {
                        parseWav(ins)?.let { sr = it[0]; chs = it[1]; bits = it[2] }
                    }
                }
            }
        } catch (_: Throwable) {}
        try {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, t.uri)
                r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toIntOrNull()?.let { kbps = it / 1000 }
                if (Build.VERSION.SDK_INT >= 31) {
                    if (sr == 0) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE)?.toIntOrNull()?.let { sr = it }
                    if (bits == 0) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.let { bits = it }
                }
            } finally {
                try { r.release() } catch (_: Exception) {}
            }
        } catch (_: Throwable) {}
        if (sr == 0 || chs == 0) {
            try {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(ctx, t.uri, null)
                    if (ex.trackCount > 0) {
                        val f = ex.getTrackFormat(0)
                        if (sr == 0 && f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sr = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        if (chs == 0 && f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) chs = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    }
                } finally { ex.release() }
            } catch (_: Throwable) {}
        }
        if ((t.fmt == Fmt.FLAC || t.fmt == Fmt.WAV) && t.size > 0 && t.durationMs > 0) {
            kbps = (t.size * 8 / t.durationMs).toInt()
        }
        if (kbps == 0 && t.bitrate > 0) kbps = t.bitrate / 1000
        val tech = Tech(sr, bits, chs, kbps)
        cache[t.id] = tech
        return tech
    }

    private fun parseStreamInfo(si: ByteArray): IntArray {
        val b10 = si[10].toInt() and 0xff; val b11 = si[11].toInt() and 0xff
        val b12 = si[12].toInt() and 0xff; val b13 = si[13].toInt() and 0xff
        val sr = (b10 shl 12) or (b11 shl 4) or (b12 shr 4)
        val ch = ((b12 shr 1) and 7) + 1
        val bps = (((b12 and 1) shl 4) or (b13 shr 4)) + 1
        return intArrayOf(sr, ch, bps)
    }

    private fun parseFlac(ins: InputStream): IntArray? {
        val hdr = ByteArray(4)
        if (readFully(ins, hdr, 4) < 4) return null
        if ((hdr[0].toInt() and 0x7f) != 0) return null
        val si = ByteArray(34)
        if (readFully(ins, si, 34) < 34) return null
        return parseStreamInfo(si)
    }

    private fun parseWav(ins: InputStream): IntArray? {
        val ch = ByteArray(8)
        repeat(40) {
            if (readFully(ins, ch, 8) < 8) return null
            val id = String(ch, 0, 4, Charsets.ISO_8859_1)
            val size = (ch[4].toLong() and 0xff) or ((ch[5].toLong() and 0xff) shl 8) or
                ((ch[6].toLong() and 0xff) shl 16) or ((ch[7].toLong() and 0xff) shl 24)
            if (id == "fmt ") {
                val f = ByteArray(16)
                if (readFully(ins, f, 16) < 16) return null
                val chans = (f[2].toInt() and 0xff) or ((f[3].toInt() and 0xff) shl 8)
                val rate = (f[4].toInt() and 0xff) or ((f[5].toInt() and 0xff) shl 8) or
                    ((f[6].toInt() and 0xff) shl 16) or ((f[7].toInt() and 0xff) shl 24)
                val bits = (f[14].toInt() and 0xff) or ((f[15].toInt() and 0xff) shl 8)
                return intArrayOf(rate, chans, bits)
            }
            skipFully(ins, size + (size and 1))
        }
        return null
    }

    private fun readFully(ins: InputStream, buf: ByteArray, n: Int): Int {
        var got = 0
        while (got < n) {
            val r = ins.read(buf, got, n - got)
            if (r <= 0) break
            got += r
        }
        return got
    }

    private fun skipFully(ins: InputStream, n: Long) {
        var left = n
        val tmp = ByteArray(8192)
        while (left > 0) {
            val s = ins.skip(left)
            if (s > 0) { left -= s; continue }
            val r = ins.read(tmp, 0, minOf(tmp.size.toLong(), left).toInt())
            if (r <= 0) return
            left -= r
        }
    }
}
