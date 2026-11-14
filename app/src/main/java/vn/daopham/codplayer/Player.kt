package vn.daopham.codplayer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

object PlayerHolder {
    private var exo: ExoPlayer? = null
    val exoOrNull: ExoPlayer? get() = exo

    var queue by mutableStateOf<List<Track>>(emptyList())
        private set
    var index by mutableIntStateOf(-1)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
        private set
    var sleepAt by mutableLongStateOf(0L)
        private set
    var errorTick by mutableIntStateOf(0)
        private set
    var errorText = ""
        private set

    val current: Track? get() = queue.getOrNull(index)

    private val handler = Handler(Looper.getMainLooper())
    private val sleepTask = Runnable { exo?.pause(); sleepAt = 0L }

    fun player(ctx: Context): ExoPlayer {
        exo?.let { return it }
        val app = ctx.applicationContext
        Prefs.init(app)
        val rf = object : DefaultRenderersFactory(app) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                .setAudioProcessors(arrayOf<AudioProcessor>(CodDsp))
                .build()
        }
        val p = ExoPlayer.Builder(app, rf)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing) current?.let { Prefs.saveLast(it.id, p.currentPosition) }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                index = p.currentMediaItemIndex
                current?.let { Prefs.saveLast(it.id, 0L) }
            }

            override fun onShuffleModeEnabledChanged(enabled: Boolean) { shuffle = enabled }
            override fun onRepeatModeChanged(mode: Int) { repeat = mode }

            override fun onPlayerError(error: PlaybackException) {
                errorText = "Không phát được: ${current?.fileName ?: "bài này"}"
                errorTick++
                if (p.hasNextMediaItem()) {
                    p.seekToNextMediaItem()
                    p.prepare()
                    p.play()
                }
            }
        })
        exo = p
        return p
    }

    fun playList(ctx: Context, list: List<Track>, start: Int, shuffleOn: Boolean? = null) {
        if (list.isEmpty()) return
        val p = player(ctx)
        queue = list
        p.setMediaItems(list.map { it.toMediaItem() }, start.coerceIn(0, list.size - 1), 0L)
        shuffleOn?.let { p.shuffleModeEnabled = it }
        p.prepare()
        p.play()
        index = p.currentMediaItemIndex
        ensureService(ctx)
    }

    fun restore(ctx: Context) {
        val p = player(ctx)
        if (p.mediaItemCount > 0) return
        val all = Library.tracks
        val idx = all.indexOfFirst { it.id == Prefs.lastId() }
        if (idx < 0) return
        queue = all
        p.setMediaItems(all.map { it.toMediaItem() }, idx, Prefs.lastPos())
        p.prepare()
        index = idx
    }

    fun toggle(ctx: Context) {
        val p = player(ctx)
        if (p.mediaItemCount == 0) return
        if (p.isPlaying) { p.pause(); return }
        if (p.playbackState == Player.STATE_IDLE) p.prepare()
        if (p.playbackState == Player.STATE_ENDED) p.seekTo(0L)
        p.play()
        ensureService(ctx)
    }

    fun next(ctx: Context) { player(ctx).seekToNext() }
    fun prev(ctx: Context) { player(ctx).seekToPrevious() }
    fun seek(ms: Long) { exo?.seekTo(ms) }
    fun toggleShuffle(ctx: Context) { val p = player(ctx); p.shuffleModeEnabled = !p.shuffleModeEnabled }
    fun cycleRepeat(ctx: Context) {
        val p = player(ctx)
        p.repeatMode = when (p.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun setSleep(minutes: Int) {
        handler.removeCallbacks(sleepTask)
        if (minutes <= 0) { sleepAt = 0L; return }
        sleepAt = System.currentTimeMillis() + minutes * 60_000L
        handler.postDelayed(sleepTask, minutes * 60_000L)
    }

    private fun ensureService(ctx: Context) {
        try { ctx.startService(Intent(ctx, PlaybackService::class.java)) } catch (_: Exception) {}
    }
}

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val p = PlayerHolder.player(this)
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        session = MediaSession.Builder(this, p).setSessionActivity(open).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        super.onDestroy()
    }
}
