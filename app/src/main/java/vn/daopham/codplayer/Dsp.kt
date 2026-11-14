package vn.daopham.codplayer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

val EQ_FREQS = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
val EQ_LABELS = listOf("31", "62", "125", "250", "500", "1K", "2K", "4K", "8K", "16K")
const val EQ_Q = 1.41
const val BASS_HZ = 105.0
const val TREBLE_HZ = 7500.0

/** Bộ lọc biquad (công thức RBJ), lưu trạng thái riêng cho tối đa 8 kênh. */
class Biquad {
    var b0 = 1.0; var b1 = 0.0; var b2 = 0.0; var a1 = 0.0; var a2 = 0.0
    private val z1 = DoubleArray(8)
    private val z2 = DoubleArray(8)

    private fun norm(nb0: Double, nb1: Double, nb2: Double, a0: Double, na1: Double, na2: Double) {
        b0 = nb0 / a0; b1 = nb1 / a0; b2 = nb2 / a0; a1 = na1 / a0; a2 = na2 / a0
    }

    fun setPeaking(fs: Double, f0: Double, q: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w0 = 2 * PI * f0 / fs
        val alpha = sin(w0) / (2 * q)
        val c = cos(w0)
        norm(1 + alpha * a, -2 * c, 1 - alpha * a, 1 + alpha / a, -2 * c, 1 - alpha / a)
    }

    fun setLowShelf(fs: Double, f0: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w0 = 2 * PI * f0 / fs
        val c = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        norm(
            a * ((a + 1) - (a - 1) * c + sa),
            2 * a * ((a - 1) - (a + 1) * c),
            a * ((a + 1) - (a - 1) * c - sa),
            (a + 1) + (a - 1) * c + sa,
            -2 * ((a - 1) + (a + 1) * c),
            (a + 1) + (a - 1) * c - sa
        )
    }

    fun setHighShelf(fs: Double, f0: Double, db: Double) {
        val a = 10.0.pow(db / 40.0)
        val w0 = 2 * PI * f0 / fs
        val c = cos(w0)
        val alpha = sin(w0) / 2 * sqrt(2.0)
        val sa = 2 * sqrt(a) * alpha
        norm(
            a * ((a + 1) + (a - 1) * c + sa),
            -2 * a * ((a - 1) + (a + 1) * c),
            a * ((a + 1) + (a - 1) * c - sa),
            (a + 1) - (a - 1) * c + sa,
            2 * ((a - 1) - (a + 1) * c),
            (a + 1) - (a - 1) * c - sa
        )
    }

    fun process(x: Double, ch: Int): Double {
        val y = b0 * x + z1[ch]
        z1[ch] = b1 * x - a1 * y + z2[ch]
        z2[ch] = b2 * x - a2 * y
        return y
    }

    fun reset() { z1.fill(0.0); z2.fill(0.0) }

    /** Đáp tuyến (dB) tại tần số f — dùng để vẽ đường cong EQ. */
    fun magDb(f: Double, fs: Double): Double {
        val w = 2 * PI * f / fs
        val cw = cos(w); val c2 = cos(2 * w); val sw = sin(w); val s2 = sin(2 * w)
        val nr = b0 + b1 * cw + b2 * c2; val ni = -(b1 * sw + b2 * s2)
        val dr = 1 + a1 * cw + a2 * c2; val di = -(a1 * sw + a2 * s2)
        return 10 * log10((nr * nr + ni * ni) / (dr * dr + di * di) + 1e-12)
    }
}

/** Thông số DSP mà luồng phát nhạc đọc. Giao diện ghi vào rồi tăng version. */
object DspParams {
    @Volatile var version = 0
    @Volatile var enabled = true
    @Volatile var preampDb = 0f
    val gains = FloatArray(10)
    @Volatile var bassDb = 0f
    @Volatile var trebleDb = 0f
    @Volatile var width = 1f
    @Volatile var balance = 0f
    @Volatile var limiter = true
    fun bump() { version++ }
}

/** Bộ đệm vòng chứa tín hiệu mono sau EQ cho phần hiệu ứng hình ảnh. */
object VizBuffer {
    const val SIZE = 32768
    private const val MASK = (SIZE - 1).toLong()
    val data = FloatArray(SIZE)
    @Volatile var written = 0L
    @Volatile var sampleRate = 44100

    fun write(src: FloatArray, n: Int) {
        var w = written
        for (i in 0 until n) { data[(w and MASK).toInt()] = src[i]; w++ }
        written = w
    }

    fun readAt(dst: FloatArray, end: Long) {
        val start = end - dst.size
        for (i in dst.indices) {
            val idx = start + i
            dst[i] = if (idx < 0) 0f else data[(idx and MASK).toInt()]
        }
    }
}

/** Bộ xử lý âm thanh gắn vào ExoPlayer: preamp → EQ 10 băng → bass/treble → stereo → limiter. */
object CodDsp : BaseAudioProcessor() {
    private var fs = 44100.0
    private var ch = 2
    private var isFloat = false
    private val bands = Array(10) { Biquad() }
    private val bandOn = BooleanArray(12)
    private val bass = Biquad()
    private val treble = Biquad()
    private var active = arrayOf<Biquad>()
    private var seenVersion = -1
    private var seenFs = 0.0
    private var enabled = true
    private var preGain = 1.0
    private var width = 1.0
    private var balL = 1.0
    private var balR = 1.0
    private var limOn = true
    private var limGain = 1.0
    private var limRelease = 0.0001
    private val frame = DoubleArray(8)
    private val viz = FloatArray(4096)

    override fun onConfigure(fmt: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (fmt.encoding != C.ENCODING_PCM_16BIT && fmt.encoding != C.ENCODING_PCM_FLOAT) return AudioProcessor.AudioFormat.NOT_SET
        if (fmt.channelCount < 1 || fmt.channelCount > 8) return AudioProcessor.AudioFormat.NOT_SET
        return fmt
    }

    override fun onFlush() {
        val f = inputAudioFormat
        if (f.sampleRate > 0) {
            fs = f.sampleRate.toDouble()
            ch = f.channelCount
            isFloat = f.encoding == C.ENCODING_PCM_FLOAT
            VizBuffer.sampleRate = f.sampleRate
        }
        resetState()
    }

    override fun onReset() { resetState() }

    private fun resetState() {
        bands.forEach { it.reset() }; bass.reset(); treble.reset()
        bandOn.fill(false)
        limGain = 1.0
        seenVersion = -1
    }

    private fun sync() {
        val v = DspParams.version
        if (v == seenVersion && fs == seenFs) return
        seenVersion = v; seenFs = fs
        enabled = DspParams.enabled
        preGain = 10.0.pow(DspParams.preampDb / 20.0)
        val list = ArrayList<Biquad>(12)
        for (i in 0 until 10) {
            val g = DspParams.gains[i].toDouble()
            val on = abs(g) > 0.05 && EQ_FREQS[i] < fs * 0.45
            if (on) {
                if (!bandOn[i]) bands[i].reset()
                bands[i].setPeaking(fs, EQ_FREQS[i].toDouble(), EQ_Q, g); list.add(bands[i])
            }
            bandOn[i] = on
        }
        val bd = DspParams.bassDb.toDouble()
        val bOn = abs(bd) > 0.05
        if (bOn) { if (!bandOn[10]) bass.reset(); bass.setLowShelf(fs, BASS_HZ, bd); list.add(bass) }
        bandOn[10] = bOn
        val td = DspParams.trebleDb.toDouble()
        val tOn = abs(td) > 0.05 && TREBLE_HZ < fs * 0.45
        if (tOn) { if (!bandOn[11]) treble.reset(); treble.setHighShelf(fs, TREBLE_HZ, td); list.add(treble) }
        bandOn[11] = tOn
        active = list.toTypedArray()
        width = DspParams.width.toDouble()
        val b = DspParams.balance.toDouble()
        balL = if (b > 0) 1 - b else 1.0
        balR = if (b < 0) 1 + b else 1.0
        limOn = DspParams.limiter
        limRelease = 1.0 - exp(-1.0 / (0.25 * fs))
    }

    override fun queueInput(input: ByteBuffer) {
        val remaining = input.remaining()
        if (remaining == 0) return
        input.order(ByteOrder.nativeOrder())
        val out = replaceOutputBuffer(remaining)
        sync()
        val c = ch
        val float = isFloat
        val bps = if (float) 4 else 2
        val frames = remaining / (bps * c)
        val dsp = enabled
        val filters = active
        var vi = 0
        for (n in 0 until frames) {
            for (k in 0 until c) frame[k] = if (float) input.float.toDouble() else input.short / 32768.0
            if (dsp) {
                for (k in 0 until c) {
                    var x = frame[k] * preGain
                    for (f in filters) x = f.process(x, k)
                    frame[k] = x
                }
                if (c == 2) {
                    if (width != 1.0) {
                        val m = (frame[0] + frame[1]) * 0.5
                        val s = (frame[0] - frame[1]) * 0.5 * width
                        frame[0] = m + s; frame[1] = m - s
                    }
                    frame[0] *= balL; frame[1] *= balR
                }
                if (limOn) {
                    var peak = 0.0
                    for (k in 0 until c) peak = max(peak, abs(frame[k]))
                    val need = if (peak > 0.97) 0.97 / peak else 1.0
                    limGain = if (need < limGain) need else min(need, limGain + (1.0 - limGain) * limRelease)
                    if (limGain < 1.0) for (k in 0 until c) frame[k] *= limGain
                }
            }
            var m = 0.0
            for (k in 0 until c) {
                var x = frame[k]
                if (x > 1.0) x = 1.0 else if (x < -1.0) x = -1.0
                m += x
                if (float) out.putFloat(x.toFloat()) else out.putShort((x * 32767.0).roundToInt().toShort())
            }
            viz[vi++] = (m / c).toFloat()
            if (vi == viz.size) { VizBuffer.write(viz, vi); vi = 0 }
        }
        if (vi > 0) VizBuffer.write(viz, vi)
        input.position(input.limit())
        out.flip()
    }
}

class EqPreset(val name: String, val gains: FloatArray, val bass: Float = 0f, val treble: Float = 0f)

private fun g(vararg v: Int) = FloatArray(v.size) { v[it].toFloat() }

val BuiltInPresets = listOf(
    EqPreset("Phẳng", g(0, 0, 0, 0, 0, 0, 0, 0, 0, 0)),
    EqPreset("Rock", g(5, 4, 3, 1, -1, -1, 1, 3, 4, 5)),
    EqPreset("Rock Ballad", g(4, 3, 2, 0, -1, 0, 2, 3, 3, 2)),
    EqPreset("Bolero", g(2, 2, 1, 0, 1, 2, 3, 2, 1, 0)),
    EqPreset("Pop", g(-1, 1, 3, 4, 3, 0, -1, -1, 1, 2)),
    EqPreset("Giọng hát", g(-2, -1, 0, 2, 4, 4, 3, 2, 0, -1)),
    EqPreset("Bass mạnh", g(8, 7, 5, 3, 1, 0, 0, 0, 0, 0)),
    EqPreset("Dance / EDM", g(6, 5, 2, 0, -1, 0, 1, 2, 4, 4)),
    EqPreset("Hip-hop", g(6, 5, 2, 3, -1, -1, 1, 0, 2, 3)),
    EqPreset("Jazz", g(3, 2, 1, 2, -1, -1, 0, 1, 2, 3)),
    EqPreset("Cổ điển", g(4, 3, 2, 1, -1, -1, 0, 2, 3, 4)),
    EqPreset("Acoustic", g(4, 3, 2, 1, 1, 1, 2, 3, 3, 2)),
    EqPreset("Tai nghe", g(4, 3, 1, 0, -1, 0, 1, 2, 3, 3)),
    EqPreset("Loa nhỏ", g(-4, -2, 0, 2, 3, 3, 2, 1, 0, -2)),
    EqPreset("Xe hơi", g(5, 4, 2, 0, -1, 0, 1, 2, 3, 3)),
    EqPreset("Ban đêm", g(-2, -1, 0, 1, 2, 2, 1, -1, -3, -4)),
)
