package com.protectmydevice.battery

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.max
import kotlin.math.min

/**
 * One reading of this app's own counters, as Android's [android.os.health.UidHealthStats] reports them.
 * [counters] holds only the keys the phone actually supplied: a key that is missing means "not
 * reported", which is different from 0.
 */
data class Snapshot(
    val wallMs: Long,
    /** Time since boot (SystemClock.elapsedRealtime). Going backwards means the phone restarted. */
    val elapsedMs: Long,
    val counters: Map<String, Long>,
)

/** Names used in [Snapshot.counters] and in each day's totals. Plain strings so they store easily. */
object Key {
    // From the system (cumulative, reset by Android on reboot and when it starts a new battery-stats window).
    const val CPU_MS = "cpu_ms"
    const val GPS_MS = "gps_ms"
    const val CAMERA_MS = "camera_ms"
    const val FLASH_MS = "flash_ms"
    const val AUDIO_MS = "audio_ms"
    const val WAKELOCK_MS = "wakelock_ms"
    const val MOBILE_ACTIVE_MS = "mobile_active_ms"
    const val MOBILE_BYTES = "mobile_bytes"
    const val MOBILE_MAMS = "mobile_mams"
    const val WIFI_ACTIVE_MS = "wifi_active_ms"
    const val WIFI_BYTES = "wifi_bytes"
    const val WIFI_MAMS = "wifi_mams"
    const val BT_ACTIVE_MS = "bt_active_ms"
    const val BT_SCAN_MS = "bt_scan_ms"
    const val BT_MAMS = "bt_mams"

    // Counted by the app itself, added straight to the day they happen on.
    const val FIX_HIGH = "fix_high"
    const val FIX_BALANCED = "fix_balanced"
    const val STOLEN_S = "stolen_s"
    const val LOCATE_BURSTS = "locate_bursts"
    const val PHOTOS = "photos"
    const val ALARM_S = "alarm_s"

    // The whole phone, for context only.
    const val PHONE_DROP_PCT = "phone_drop_pct"
}

/**
 * Day-by-day totals built from successive [Snapshot]s plus the app's own counts.
 *
 * The system counters only ever go up, except when Android resets them (a reboot, or the phone
 * coming off the charger after a full charge). A reset shows up as a counter going backwards; from
 * then on the new value *is* the usage since the reset. Usage between two snapshots is spread over
 * the days they span in proportion to time, so a gap across midnight is split fairly.
 */
class BatteryLedger(
    val days: MutableMap<LocalDate, MutableMap<String, Double>> = sortedMapOf(),
    var last: Snapshot? = null,
) {
    /** Folds a new snapshot in. The very first one only sets the baseline: its counters cover an unknown past. */
    fun record(s: Snapshot, zone: ZoneId) {
        val prev = last
        last = s
        if (prev == null) return
        // The clock was set back, or the same reading came twice: nothing sensible to add, just re-baseline.
        if (s.wallMs <= prev.wallMs) return

        val rebooted = s.elapsedMs < prev.elapsedMs
        val reset = rebooted || s.counters.any { (k, v) -> prev.counters[k]?.let { v < it } == true }
        // After a reboot the new counters started at boot, not at the previous snapshot.
        val from = if (rebooted) max(prev.wallMs, s.wallMs - s.elapsedMs) else prev.wallMs

        for ((k, v) in s.counters) {
            val delta = when {
                reset -> v
                else -> v - (prev.counters[k] ?: continue) // key just appeared: no baseline to compare against
            }
            if (delta > 0) spread(k, delta.toDouble(), from, s.wallMs, zone)
        }
    }

    /** Adds [amount] to the day [atMs] falls on. */
    fun add(key: String, amount: Double, atMs: Long, zone: ZoneId) {
        if (amount <= 0) return
        val m = day(dateOf(atMs, zone))
        m[key] = (m[key] ?: 0.0) + amount
    }

    /** Adds the seconds between [fromMs] and [toMs] to [key], split over the days they cover. */
    fun addSeconds(key: String, fromMs: Long, toMs: Long, zone: ZoneId) {
        if (toMs > fromMs) spread(key, (toMs - fromMs) / 1000.0, fromMs, toMs, zone)
    }

    /** Spreads [amount] over [fromMs, toMs] in proportion to how much of that span each day holds. */
    fun spread(key: String, amount: Double, fromMs: Long, toMs: Long, zone: ZoneId) {
        if (amount <= 0) return
        if (toMs <= fromMs) { add(key, amount, toMs, zone); return }
        val total = (toMs - fromMs).toDouble()
        var date = dateOf(fromMs, zone)
        val lastDate = dateOf(toMs, zone)
        while (!date.isAfter(lastDate)) {
            val start = max(fromMs, date.atStartOfDay(zone).toInstant().toEpochMilli())
            val end = min(toMs, date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli())
            if (end > start) {
                val m = day(date)
                m[key] = (m[key] ?: 0.0) + amount * (end - start) / total
            }
            date = date.plusDays(1)
        }
    }

    /** Keeps [today] and the [keep] − 1 days before it. */
    fun prune(today: LocalDate, keep: Int = KEEP_DAYS) {
        val oldest = today.minusDays((keep - 1).toLong())
        days.keys.removeAll { it.isBefore(oldest) }
    }

    fun totals(date: LocalDate): Map<String, Double> = days[date].orEmpty()

    private fun day(date: LocalDate) = days.getOrPut(date) { mutableMapOf() }

    // ---- Storage: a small plain-text format, so it needs nothing beyond SharedPreferences. ----

    fun serialize(): String = buildString {
        last?.let { s ->
            append("S|").append(s.wallMs).append('|').append(s.elapsedMs).append('|')
            append(s.counters.entries.joinToString(",") { "${it.key}=${it.value}" }).append('\n')
        }
        for ((date, m) in days) {
            append("D|").append(date).append('|')
            append(m.entries.joinToString(",") { "${it.key}=${it.value}" }).append('\n')
        }
    }

    companion object {
        const val KEEP_DAYS = 7

        fun dateOf(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

        /** Reads what [serialize] wrote. Anything unreadable is dropped rather than crashing the app. */
        fun parse(text: String?): BatteryLedger {
            val ledger = BatteryLedger()
            text?.lineSequence()?.forEach { line ->
                val parts = line.split('|')
                runCatching {
                    when (parts.firstOrNull()) {
                        "S" -> ledger.last = Snapshot(
                            parts[1].toLong(), parts[2].toLong(),
                            pairs(parts[3]).mapValues { it.value.toLong() },
                        )
                        "D" -> ledger.days[LocalDate.parse(parts[1])] =
                            pairs(parts[2]).mapValues { it.value.toDouble() }.toMutableMap()
                    }
                }
            }
            return ledger
        }

        private fun pairs(s: String): Map<String, String> = s.split(',').filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
    }
}
