package com.protectmydevice.battery

import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

/** The parts of the phone this app can make work, in the order the battery screen lists them. */
enum class Part(val key: String, val label: String) {
    LOCATION("location", "location (gps)"),
    CPU("cpu", "processor"),
    MOBILE("mobile", "mobile data"),
    WIFI("wifi", "wifi"),
    BLUETOOTH("bluetooth", "bluetooth"),
    CAMERA("camera", "camera"),
    WAKELOCKS("wakelocks", "keeping the phone awake"),
    AUDIO("audio", "sound + alarm"),
}

/**
 * One part's share of a day. [measured] is true when the phone itself reported the energy
 * (its own power counters); otherwise [mah] is a ballpark from [activeMs] × a typical current.
 */
data class PartUse(val part: Part, val mah: Double, val measured: Boolean, val activeMs: Double, val detail: String)

/** What the app did, by its own count. */
data class AppCounts(
    val fixesHigh: Int = 0,
    val fixesBalanced: Int = 0,
    val stolenMinutes: Int = 0,
    val locateBursts: Int = 0,
    val photos: Int = 0,
    val alarmSeconds: Int = 0,
)

data class DayReport(
    val date: LocalDate,
    val parts: List<PartUse>,
    val counts: AppCounts,
    /** How far the whole phone's battery fell over the day (all apps, the screen, everything). */
    val phoneDropPct: Double?,
) {
    val totalMah: Double get() = parts.sumOf { it.mah }
    val measuredMah: Double get() = parts.filter { it.measured }.sumOf { it.mah }
    val estimatedMah: Double get() = totalMah - measuredMah
    val hasData: Boolean get() = parts.any { it.activeMs > 0 || it.mah > 0 } || counts != AppCounts()

    /** This app's day as a share of a full battery, when the battery's size could be worked out. */
    fun shareOfBattery(capacityMah: Double?): Double? =
        capacityMah?.takeIf { it > 0 }?.let { totalMah / it * 100 }

    /** The daily summary sent to Firestore. Must match the `battery` rule in firestore.rules. */
    fun toUpload(): Map<String, Any> = buildMap {
        put("day", date.toString())
        put("totalMah", BatteryReport.round2(totalMah))
        put("measuredMah", BatteryReport.round2(measuredMah))
        put("parts", parts.associate { it.part.key to BatteryReport.round2(it.mah) })
        put(
            "counts",
            mapOf(
                "fixesHigh" to counts.fixesHigh, "fixesBalanced" to counts.fixesBalanced,
                "stolenMinutes" to counts.stolenMinutes, "locateBursts" to counts.locateBursts,
                "photos" to counts.photos, "alarmSeconds" to counts.alarmSeconds,
            ),
        )
        phoneDropPct?.let { put("phoneDropPct", BatteryReport.round2(it)) }
    }
}

/** Turns a day's raw totals into the per-part report, and formats numbers in plain words. */
object BatteryReport {
    /**
     * Typical currents for a mid-range phone, in mA, used only when the phone gives no energy
     * figure. Real phones differ a lot (2× either way is normal), which is why the screen calls
     * anything built from these an estimate.
     */
    const val GPS_MA = 50.0
    const val CPU_MA = 100.0
    const val MOBILE_MA = 120.0
    const val WIFI_MA = 100.0
    const val BLUETOOTH_MA = 10.0
    const val CAMERA_MA = 300.0
    const val FLASH_MA = 150.0
    const val WAKELOCK_MA = 10.0
    const val AUDIO_MA = 150.0

    private const val MS_PER_HOUR = 3_600_000.0

    fun build(date: LocalDate, t: Map<String, Double>): DayReport {
        fun v(k: String) = t[k] ?: 0.0
        fun est(ms: Double, ma: Double) = ms / MS_PER_HOUR * ma
        /** Measured energy if the phone counted any for this part today, else the estimate. */
        fun pick(mamsKey: String, estimate: Double): Pair<Double, Boolean> {
            val mams = v(mamsKey)
            return if (mams > 0) mams / MS_PER_HOUR to true else estimate to false
        }
        val counts = AppCounts(
            fixesHigh = v(Key.FIX_HIGH).roundToInt(),
            fixesBalanced = v(Key.FIX_BALANCED).roundToInt(),
            stolenMinutes = (v(Key.STOLEN_S) / 60).roundToInt(),
            locateBursts = v(Key.LOCATE_BURSTS).roundToInt(),
            photos = v(Key.PHOTOS).roundToInt(),
            alarmSeconds = v(Key.ALARM_S).roundToInt(),
        )

        val gps = v(Key.GPS_MS)
        val cpu = v(Key.CPU_MS)
        val mobile = v(Key.MOBILE_ACTIVE_MS)
        val wifi = v(Key.WIFI_ACTIVE_MS)
        val bt = v(Key.BT_ACTIVE_MS) + v(Key.BT_SCAN_MS)
        val camera = v(Key.CAMERA_MS)
        val flash = v(Key.FLASH_MS)
        val awake = v(Key.WAKELOCK_MS)
        val audio = v(Key.AUDIO_MS)
        val fixes = counts.fixesHigh + counts.fixesBalanced

        val (mobileMah, mobileMeasured) = pick(Key.MOBILE_MAMS, est(mobile, MOBILE_MA))
        val (wifiMah, wifiMeasured) = pick(Key.WIFI_MAMS, est(wifi, WIFI_MA))
        val (btMah, btMeasured) = pick(Key.BT_MAMS, est(bt, BLUETOOTH_MA))

        val parts = listOf(
            PartUse(
                Part.LOCATION, est(gps, GPS_MA), false, gps,
                "gps on ${duration(gps)} · $fixes ${plural(fixes, "fix", "fixes")}",
            ),
            PartUse(Part.CPU, est(cpu, CPU_MA), false, cpu, "busy for ${duration(cpu)}"),
            PartUse(
                Part.MOBILE, mobileMah, mobileMeasured, mobile,
                "radio on ${duration(mobile)} · ${bytes(v(Key.MOBILE_BYTES))}",
            ),
            PartUse(
                Part.WIFI, wifiMah, wifiMeasured, wifi,
                "sending/receiving ${duration(wifi)} · ${bytes(v(Key.WIFI_BYTES))}",
            ),
            PartUse(Part.BLUETOOTH, btMah, btMeasured, bt, "in use ${duration(bt)}"),
            PartUse(
                Part.CAMERA, est(camera, CAMERA_MA) + est(flash, FLASH_MA), false, camera + flash,
                "camera on ${duration(camera)} · ${counts.photos} ${plural(counts.photos, "photo", "photos")}",
            ),
            PartUse(Part.WAKELOCKS, est(awake, WAKELOCK_MA), false, awake, "held awake ${duration(awake)}"),
            PartUse(
                Part.AUDIO, est(audio, AUDIO_MA), false, audio,
                "sound playing ${duration(audio)} · alarm ${duration(counts.alarmSeconds * 1000.0)}",
            ),
        )
        return DayReport(date, parts, counts, t[Key.PHONE_DROP_PCT])
    }

    /** The last [days] days ending [today], newest first; days with nothing recorded are still listed. */
    fun week(ledger: BatteryLedger, today: LocalDate, days: Int = BatteryLedger.KEEP_DAYS): List<DayReport> =
        (0 until days).map { today.minusDays(it.toLong()) }.map { build(it, ledger.totals(it)) }

    // ---- Plain-words formatting, shared by the screen and the tests. ----

    fun duration(ms: Double): String {
        val s = (ms / 1000).roundToInt()
        return when {
            ms <= 0 -> "0 s"
            s < 1 -> "under 1 s"
            s < 60 -> "$s s"
            s < 3600 -> "${s / 60} min" + if (s < 600 && s % 60 != 0) " ${s % 60} s" else ""
            else -> "${s / 3600} h" + if (s % 3600 / 60 != 0) " ${s % 3600 / 60} min" else ""
        }
    }

    fun bytes(b: Double): String = when {
        b < 1024 -> "${b.roundToInt()} B"
        b < 1024 * 1024 -> "${(b / 1024).roundToInt()} KB"
        else -> String.format(Locale.ROOT, "%.1f MB", b / (1024 * 1024))
    }

    fun mah(v: Double): String = when {
        v <= 0 -> "0"
        v < 0.1 -> "under 0.1"
        v < 10 -> String.format(Locale.ROOT, "%.1f", v)
        else -> v.roundToInt().toString()
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
    internal fun round2(v: Double) = (v * 100).roundToInt() / 100.0
}
