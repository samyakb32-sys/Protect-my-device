package com.protectmydevice.battery

import android.annotation.SuppressLint
import android.content.Context
import android.os.BatteryManager
import android.os.SystemClock
import android.os.health.HealthStats
import android.os.health.SystemHealthManager
import android.os.health.UidHealthStats
import com.protectmydevice.data.DeviceRepository
import com.protectmydevice.data.StolenState
import java.time.LocalDate
import java.time.ZoneId

/**
 * Records how much battery this app uses, day by day, on the phone itself.
 *
 * It never wakes the phone or asks for anything on its own: [collect] runs when the app is already
 * awake for something else (a location check-in, a command, the phone shutting down, the battery
 * screen opening) and is skipped if the last reading is under [MIN_GAP_MS] old. Each reading is one
 * call to Android's [SystemHealthManager.takeMyUidSnapshot], which only ever describes this app's own
 * uid; nothing about other apps is read or sent.
 */
object BatteryCollector {
    private const val PREFS = "battery_log"
    private const val LEDGER = "ledger"
    private const val UPLOADED = "uploaded"
    private const val STOLEN_MARK = "stolen_mark"
    private const val LEVEL = "phone_level"
    private const val LEVEL_AT = "phone_level_at"
    private const val CAPACITY = "capacity_mah"
    const val MIN_GAP_MS = 20 * 60_000L

    private val lock = Any()

    /** Takes a reading unless one was taken in the last [MIN_GAP_MS]; [force] skips that check. */
    @SuppressLint("ApplySharedPref")
    fun collect(context: Context, force: Boolean = false) = synchronized(lock) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ledger = BatteryLedger.parse(prefs.getString(LEDGER, null))
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val lastAt = ledger.last?.wallMs
        if (!force && lastAt != null && now - lastAt in 0 until MIN_GAP_MS) return@synchronized

        read(app, now)?.let { ledger.record(it, zone) }

        val edit = prefs.edit()
        // Stolen mode: count the time since the last reading while it was on.
        if (StolenState.isOn(app)) {
            ledger.addSeconds(Key.STOLEN_S, prefs.getLong(STOLEN_MARK, now), now, zone)
            edit.putLong(STOLEN_MARK, now)
        } else edit.remove(STOLEN_MARK)

        // The whole phone, for context: how far its level fell between readings (charging hides some of it).
        val bm = app.getSystemService(BatteryManager::class.java)
        val level = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        if (level in 0..100) {
            val prevLevel = prefs.getInt(LEVEL, -1)
            val prevAt = prefs.getLong(LEVEL_AT, now)
            if (prevLevel in 0..100 && level < prevLevel) {
                ledger.spread(Key.PHONE_DROP_PCT, (prevLevel - level).toDouble(), prevAt, now, zone)
            }
            edit.putInt(LEVEL, level).putLong(LEVEL_AT, now)
            capacityMah(bm, level)?.let { edit.putFloat(CAPACITY, it.toFloat()) }
        }

        val today = LocalDate.now(zone)
        ledger.prune(today)
        edit.putString(LEDGER, ledger.serialize())
        edit.putStringSet(UPLOADED, upload(app, ledger, today, prefs.getStringSet(UPLOADED, emptySet()).orEmpty()))
        // Written straight away, not queued: the reading taken as the phone shuts down must reach the disk.
        // Callers are background threads, except that shutdown broadcast, where waiting is the point.
        edit.commit()
    }

    /** Adds one of the app's own counts (fixes, photos, bursts) to today. */
    fun note(context: Context, key: String, amount: Double = 1.0) = update(context) { ledger, now, zone ->
        ledger.add(key, amount, now, zone)
    }

    /** Adds a stretch of time (alarm sounding) to the day or days it covered. */
    fun noteSeconds(context: Context, key: String, fromMs: Long) = update(context) { ledger, now, zone ->
        ledger.addSeconds(key, fromMs, now, zone)
    }

    private fun update(context: Context, change: (BatteryLedger, Long, ZoneId) -> Unit) = synchronized(lock) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ledger = BatteryLedger.parse(prefs.getString(LEDGER, null))
        change(ledger, System.currentTimeMillis(), ZoneId.systemDefault())
        prefs.edit().putString(LEDGER, ledger.serialize()).apply()
    }

    /** What the battery screen shows: the last 7 days, newest first. */
    fun week(context: Context): List<DayReport> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val ledger = synchronized(lock) { BatteryLedger.parse(prefs.getString(LEDGER, null)) }
        return BatteryReport.week(ledger, LocalDate.now(ZoneId.systemDefault()))
    }

    /** The battery's full size in mAh as last worked out, if this phone reports enough to tell. */
    fun capacityMah(context: Context): Double? = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(CAPACITY, 0f).toDouble().takeIf { it > 0 }

    /** True once at least one reading has been taken (the first one only sets the starting point). */
    fun started(context: Context): Boolean = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(LEDGER)

    /** charge left (µAh) ÷ level: the battery's full size. Many phones don't report it; then null. */
    private fun capacityMah(bm: BatteryManager, level: Int): Double? {
        if (level < 10) return null
        val microAh = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
        if (microAh <= 0 || microAh == Int.MIN_VALUE) return null
        return (microAh / 1000.0 / (level / 100.0)).takeIf { it in 500.0..20_000.0 }
    }

    /**
     * Sends each finished day (yesterday and earlier, within the week) once. Firestore keeps the write
     * on disk until it is online, so a day is marked sent as soon as it is queued.
     */
    private fun upload(context: Context, ledger: BatteryLedger, today: LocalDate, sent: Set<String>): Set<String> {
        val oldest = today.minusDays((BatteryLedger.KEEP_DAYS - 1).toLong())
        val kept = sent.filter { runCatching { !LocalDate.parse(it).isBefore(oldest) }.getOrDefault(false) }.toMutableSet()
        val repo = runCatching { DeviceRepository(context) }.getOrNull() ?: return kept
        for (date in ledger.days.keys) {
            if (!date.isBefore(today) || date.isBefore(oldest) || date.toString() in kept) continue
            val report = BatteryReport.build(date, ledger.totals(date))
            if (!report.hasData) continue
            if (runCatching { repo.uploadBatteryDay(report) }.getOrDefault(false)) kept += date.toString()
        }
        return kept
    }

    /** One reading of this app's own counters. Keys the phone doesn't report are left out, not zeroed. */
    private fun read(context: Context, now: Long): Snapshot? {
        val shm = context.getSystemService(SystemHealthManager::class.java) ?: return null
        val h = runCatching { shm.takeMyUidSnapshot() }.getOrNull() ?: return null
        val c = mutableMapOf<String, Long>()
        fun put(key: String, vararg values: Long?) {
            val present = values.filterNotNull()
            if (present.isNotEmpty()) c[key] = present.sum()
        }
        put(Key.CPU_MS, h.m(UidHealthStats.MEASUREMENT_USER_CPU_TIME_MS), h.m(UidHealthStats.MEASUREMENT_SYSTEM_CPU_TIME_MS))
        put(Key.GPS_MS, h.t(UidHealthStats.TIMER_GPS_SENSOR))
        put(Key.CAMERA_MS, h.t(UidHealthStats.TIMER_CAMERA))
        put(Key.FLASH_MS, h.t(UidHealthStats.TIMER_FLASHLIGHT))
        put(Key.AUDIO_MS, h.t(UidHealthStats.TIMER_AUDIO))
        put(Key.WAKELOCK_MS, h.timers(UidHealthStats.TIMERS_WAKELOCKS_PARTIAL))
        // Radio-active time includes the tail the modem stays powered for after a transfer, which is what costs battery.
        put(
            Key.MOBILE_ACTIVE_MS,
            h.t(UidHealthStats.TIMER_MOBILE_RADIO_ACTIVE)
                ?: sumOrNull(h.m(UidHealthStats.MEASUREMENT_MOBILE_RX_MS), h.m(UidHealthStats.MEASUREMENT_MOBILE_TX_MS)),
        )
        put(Key.MOBILE_BYTES, h.m(UidHealthStats.MEASUREMENT_MOBILE_RX_BYTES), h.m(UidHealthStats.MEASUREMENT_MOBILE_TX_BYTES))
        put(Key.MOBILE_MAMS, h.m(UidHealthStats.MEASUREMENT_MOBILE_POWER_MAMS))
        put(Key.WIFI_ACTIVE_MS, h.m(UidHealthStats.MEASUREMENT_WIFI_RX_MS), h.m(UidHealthStats.MEASUREMENT_WIFI_TX_MS))
        put(Key.WIFI_BYTES, h.m(UidHealthStats.MEASUREMENT_WIFI_RX_BYTES), h.m(UidHealthStats.MEASUREMENT_WIFI_TX_BYTES))
        put(Key.WIFI_MAMS, h.m(UidHealthStats.MEASUREMENT_WIFI_POWER_MAMS))
        put(Key.BT_ACTIVE_MS, h.m(UidHealthStats.MEASUREMENT_BLUETOOTH_RX_MS), h.m(UidHealthStats.MEASUREMENT_BLUETOOTH_TX_MS))
        put(Key.BT_SCAN_MS, h.t(UidHealthStats.TIMER_BLUETOOTH_SCAN))
        put(Key.BT_MAMS, h.m(UidHealthStats.MEASUREMENT_BLUETOOTH_POWER_MAMS))
        // MEASUREMENT_CPU_POWER_MAMS is deprecated since Android 8 as "vendor-dependent and not reliable": not used.
        return Snapshot(now, SystemClock.elapsedRealtime(), c)
    }

    private fun sumOrNull(vararg v: Long?): Long? = v.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

    // HealthStats getters throw for keys the phone didn't fill in, so every read checks first.
    private fun HealthStats.m(key: Int) = if (hasMeasurement(key)) getMeasurement(key) else null
    private fun HealthStats.t(key: Int) = if (hasTimer(key)) getTimerTime(key) else null
    private fun HealthStats.timers(key: Int) = if (hasTimers(key)) getTimers(key).values.sumOf { it.time } else null
}
