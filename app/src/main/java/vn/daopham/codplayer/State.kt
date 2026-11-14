package vn.daopham.codplayer

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector

enum class Tab(val title: String, val icon: ImageVector) {
    LIBRARY("THƯ VIỆN", Icons.Rounded.LibraryMusic),
    PLAYING("ĐANG PHÁT", Icons.Rounded.GraphicEq),
    EQ("CHỈNH ÂM", Icons.Rounded.Tune),
    SETTINGS("CÀI ĐẶT", Icons.Rounded.Settings),
}

enum class VizMode(val label: String) {
    RING("Lõi năng lượng"),
    BARS("Cột phổ cổ điển"),
    MIRROR("Phổ gương"),
    WAVE("Sóng âm"),
}

enum class FmtFilter(val label: String) {
    ALL("TẤT CẢ"), FLAC("FLAC"), WAV("WAV"), MP3("MP3"), OTHER("KHÁC");

    fun match(t: Track): Boolean = when (this) {
        ALL -> true
        FLAC -> t.fmt == Fmt.FLAC
        WAV -> t.fmt == Fmt.WAV
        MP3 -> t.fmt == Fmt.MP3
        OTHER -> t.fmt != Fmt.FLAC && t.fmt != Fmt.WAV && t.fmt != Fmt.MP3
    }
}

enum class SortBy(val label: String) {
    TITLE("Tên bài"), ARTIST("Ca sĩ"), FILE("Tên file"), FOLDER("Thư mục"), DURATION("Thời lượng"), DATE("Mới thêm"),
}

object Ui {
    var tab by mutableStateOf(Tab.LIBRARY)
    var theme by mutableIntStateOf(0)
    var vizMode by mutableStateOf(VizMode.RING)
    var vizDelay by mutableIntStateOf(220)
    var minDur by mutableIntStateOf(10)
    var filter by mutableStateOf(FmtFilter.ALL)
    var sort by mutableStateOf(SortBy.TITLE)
    var folder by mutableStateOf<String?>(null)
    var query by mutableStateOf("")
    var folderMode by mutableStateOf(false)
}

object EqState {
    var enabled by mutableStateOf(true)
    var preamp by mutableFloatStateOf(0f)
    val gains = mutableStateListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
    var bass by mutableFloatStateOf(0f)
    var treble by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(1f)
    var balance by mutableFloatStateOf(0f)
    var limiter by mutableStateOf(true)
    var preset by mutableStateOf("Phẳng")
    val custom = mutableStateListOf<EqPreset>()

    fun push(save: Boolean = true) {
        DspParams.enabled = enabled
        DspParams.preampDb = preamp
        for (i in 0 until 10) DspParams.gains[i] = gains[i]
        DspParams.bassDb = bass
        DspParams.trebleDb = treble
        DspParams.width = width
        DspParams.balance = balance
        DspParams.limiter = limiter
        DspParams.bump()
        if (save) Prefs.saveEq()
    }

    fun apply(p: EqPreset) {
        for (i in 0 until 10) gains[i] = p.gains.getOrElse(i) { 0f }
        bass = p.bass; treble = p.treble; preset = p.name
        push()
    }

    fun reset() {
        for (i in 0 until 10) gains[i] = 0f
        preamp = 0f; bass = 0f; treble = 0f; width = 1f; balance = 0f; preset = "Phẳng"
        push()
    }

    fun snapshot(name: String) = EqPreset(name, FloatArray(10) { gains[it] }, bass, treble)
}

object Prefs {
    private var sp: SharedPreferences? = null

    fun init(ctx: Context) {
        if (sp != null) return
        val s = ctx.applicationContext.getSharedPreferences("codplayer", Context.MODE_PRIVATE)
        sp = s
        Ui.theme = s.getInt("theme", 0)
        Ui.vizMode = VizMode.values().getOrElse(s.getInt("viz", 0)) { VizMode.RING }
        Ui.vizDelay = s.getInt("vizDelay", 220)
        Ui.minDur = s.getInt("minDur", 10)
        Ui.sort = SortBy.values().getOrElse(s.getInt("sort", 0)) { SortBy.TITLE }
        EqState.enabled = s.getBoolean("eqOn", true)
        EqState.preamp = s.getFloat("preamp", 0f)
        s.getString("gains", null)?.split(",")?.forEachIndexed { i, v ->
            if (i < 10) EqState.gains[i] = v.toFloatOrNull() ?: 0f
        }
        EqState.bass = s.getFloat("bass", 0f)
        EqState.treble = s.getFloat("treble", 0f)
        EqState.width = s.getFloat("width", 1f)
        EqState.balance = s.getFloat("balance", 0f)
        EqState.limiter = s.getBoolean("limiter", true)
        EqState.preset = s.getString("preset", "Phẳng") ?: "Phẳng"
        EqState.custom.clear()
        s.getString("custom", "")?.split("\n")?.forEach { line ->
            val name = line.substringBefore("=", "")
            val nums = line.substringAfter("=", "").split(",").mapNotNull { it.toFloatOrNull() }
            if (name.isNotBlank() && nums.size >= 12) {
                EqState.custom.add(EqPreset(name, FloatArray(10) { nums[it] }, nums[10], nums[11]))
            }
        }
        EqState.push(save = false)
    }

    fun saveUi() {
        sp?.edit()?.putInt("theme", Ui.theme)?.putInt("viz", Ui.vizMode.ordinal)?.putInt("vizDelay", Ui.vizDelay)
            ?.putInt("minDur", Ui.minDur)?.putInt("sort", Ui.sort.ordinal)?.apply()
    }

    fun saveEq() {
        val e = EqState
        sp?.edit()?.putBoolean("eqOn", e.enabled)?.putFloat("preamp", e.preamp)
            ?.putString("gains", e.gains.joinToString(","))?.putFloat("bass", e.bass)?.putFloat("treble", e.treble)
            ?.putFloat("width", e.width)?.putFloat("balance", e.balance)?.putBoolean("limiter", e.limiter)
            ?.putString("preset", e.preset)
            ?.putString("custom", e.custom.joinToString("\n") { p ->
                p.name.replace("=", "-").replace("\n", " ") + "=" + (p.gains.toList() + p.bass + p.treble).joinToString(",")
            })?.apply()
    }

    fun saveLast(id: Long, pos: Long) { sp?.edit()?.putLong("lastId", id)?.putLong("lastPos", pos)?.apply() }
    fun lastId(): Long = sp?.getLong("lastId", Long.MIN_VALUE) ?: Long.MIN_VALUE
    fun lastPos(): Long = sp?.getLong("lastPos", 0L) ?: 0L
}

fun fmtTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun fmtSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    bytes >= 1L shl 20 -> "%.0f MB".format(bytes / (1024.0 * 1024))
    else -> "%.0f KB".format(bytes / 1024.0)
}
