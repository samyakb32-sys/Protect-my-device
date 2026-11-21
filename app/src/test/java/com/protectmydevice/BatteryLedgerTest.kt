package com.protectmydevice

import com.protectmydevice.battery.BatteryLedger
import com.protectmydevice.battery.BatteryReport
import com.protectmydevice.battery.Key
import com.protectmydevice.battery.Part
import com.protectmydevice.battery.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BatteryLedgerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val day1 = LocalDate.of(2026, 11, 18)
    private val day2 = day1.plusDays(1)

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) =
        LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private fun snap(wall: Long, elapsed: Long, vararg c: Pair<String, Long>) = Snapshot(wall, elapsed, mapOf(*c))

    private val hour = 3_600_000L

    @Test fun firstReadingOnlySetsTheBaseline() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 10), 50 * hour, Key.CPU_MS to 999_999), zone)
        assertTrue(l.days.isEmpty())
    }

    @Test fun addsTheDifferenceBetweenReadings() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 10), 10 * hour, Key.CPU_MS to 1_000, Key.GPS_MS to 500), zone)
        l.record(snap(at(day1, 12), 12 * hour, Key.CPU_MS to 4_000, Key.GPS_MS to 500), zone)
        assertEquals(3_000.0, l.totals(day1)[Key.CPU_MS]!!, 1e-9)
        assertNull("nothing new means nothing added", l.totals(day1)[Key.GPS_MS])
    }

    @Test fun counterGoingBackwardsMeansAResetAndTheNewValueIsTheUse() {
        // Unplugged after a full charge: Android starts counting again from zero, the phone kept running.
        val l = BatteryLedger()
        l.record(snap(at(day1, 10), 10 * hour, Key.CPU_MS to 50_000, Key.WIFI_BYTES to 9_000), zone)
        l.record(snap(at(day1, 12), 12 * hour, Key.CPU_MS to 2_000, Key.WIFI_BYTES to 9_500), zone)
        // One counter went back, so all of them restarted: wifi's 9,500 is all new, not +500.
        assertEquals(2_000.0, l.totals(day1)[Key.CPU_MS]!!, 1e-9)
        assertEquals(9_500.0, l.totals(day1)[Key.WIFI_BYTES]!!, 1e-9)
    }

    @Test fun rebootCountsOnlySinceBoot() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 22), 30 * hour, Key.CPU_MS to 80_000), zone)
        // Rebooted; read an hour after boot, which was 01:00 the next day.
        l.record(snap(at(day2, 2), 1 * hour, Key.CPU_MS to 7_000), zone)
        assertNull(l.totals(day1)[Key.CPU_MS])
        assertEquals(7_000.0, l.totals(day2)[Key.CPU_MS]!!, 1e-9)
    }

    @Test fun rebootEvenWhenCountersHappenToBeHigher() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 10), 30 * hour, Key.CPU_MS to 1_000), zone)
        l.record(snap(at(day1, 14), 2 * hour, Key.CPU_MS to 5_000), zone)
        assertEquals("not 4,000: the counter restarted at boot", 5_000.0, l.totals(day1)[Key.CPU_MS]!!, 1e-9)
    }

    @Test fun missingEnergyFieldsStayMissing() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 10), 10 * hour, Key.WIFI_ACTIVE_MS to 0), zone)
        l.record(snap(at(day1, 12), 12 * hour, Key.WIFI_ACTIVE_MS to 60_000), zone)
        assertNull(l.totals(day1)[Key.WIFI_MAMS])
        val wifi = BatteryReport.build(day1, l.totals(day1)).parts.first { it.part == Part.WIFI }
        assertFalse("no energy counter: it's an estimate", wifi.measured)
        assertEquals(60_000.0 / 3_600_000 * BatteryReport.WIFI_MA, wifi.mah, 1e-9)
    }

    @Test fun energyFieldsThatStayZeroFallBackToTime() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 10), 10 * hour, Key.MOBILE_MAMS to 0, Key.MOBILE_ACTIVE_MS to 0), zone)
        l.record(snap(at(day1, 12), 12 * hour, Key.MOBILE_MAMS to 0, Key.MOBILE_ACTIVE_MS to 30_000), zone)
        val mobile = BatteryReport.build(day1, l.totals(day1)).parts.first { it.part == Part.MOBILE }
        assertFalse(mobile.measured)
        assertTrue(mobile.mah > 0)
    }

    @Test fun reportedEnergyIsUsedAsMeasured() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 10), 10 * hour, Key.WIFI_MAMS to 0, Key.WIFI_ACTIVE_MS to 0), zone)
        l.record(snap(at(day1, 12), 12 * hour, Key.WIFI_MAMS to 7_200_000, Key.WIFI_ACTIVE_MS to 1_000), zone)
        val report = BatteryReport.build(day1, l.totals(day1))
        val wifi = report.parts.first { it.part == Part.WIFI }
        assertTrue(wifi.measured)
        assertEquals("7,200,000 mA·ms = 2 mAh", 2.0, wifi.mah, 1e-9)
        assertEquals(2.0, report.measuredMah, 1e-9)
    }

    @Test fun keyAppearingLaterIsNotCountedFromZero() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 10), 10 * hour, Key.CPU_MS to 100), zone)
        l.record(snap(at(day1, 12), 12 * hour, Key.CPU_MS to 200, Key.GPS_MS to 9_000_000), zone)
        assertNull("no earlier value to subtract, so no claim about today", l.totals(day1)[Key.GPS_MS])
    }

    @Test fun useAcrossMidnightIsSplitByTime() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 23), 10 * hour, Key.CPU_MS to 0), zone)
        l.record(snap(at(day2, 2), 13 * hour, Key.CPU_MS to 3_000), zone)
        assertEquals(1_000.0, l.totals(day1)[Key.CPU_MS]!!, 1e-6)
        assertEquals(2_000.0, l.totals(day2)[Key.CPU_MS]!!, 1e-6)
    }

    @Test fun aLongGapIsSpreadOverEveryDayInIt() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 0), 10 * hour, Key.CPU_MS to 0), zone)
        l.record(snap(at(day1.plusDays(3), 0), 82 * hour, Key.CPU_MS to 3_000), zone)
        for (i in 0..2) assertEquals(1_000.0, l.totals(day1.plusDays(i.toLong()))[Key.CPU_MS]!!, 1e-6)
        assertNull(l.totals(day1.plusDays(3))[Key.CPU_MS])
    }

    @Test fun clockSetBackIsIgnored() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 12), 10 * hour, Key.CPU_MS to 0), zone)
        l.record(snap(at(day1, 11), 11 * hour, Key.CPU_MS to 5_000), zone)
        assertTrue(l.days.isEmpty())
        // ...but it becomes the new starting point.
        l.record(snap(at(day1, 13), 13 * hour, Key.CPU_MS to 6_000), zone)
        assertEquals(1_000.0, l.totals(day1)[Key.CPU_MS]!!, 1e-9)
    }

    @Test fun ownCountsAndStretchesOfTime() {
        val l = BatteryLedger()
        l.add(Key.FIX_HIGH, 3.0, at(day1, 9), zone)
        l.add(Key.FIX_HIGH, 2.0, at(day1, 10), zone)
        l.addSeconds(Key.STOLEN_S, at(day1, 23, 30), at(day2, 0, 30), zone)
        assertEquals(5.0, l.totals(day1)[Key.FIX_HIGH]!!, 1e-9)
        assertEquals(1_800.0, l.totals(day1)[Key.STOLEN_S]!!, 1e-6)
        assertEquals(1_800.0, l.totals(day2)[Key.STOLEN_S]!!, 1e-6)
        val counts = BatteryReport.build(day1, l.totals(day1)).counts
        assertEquals(5, counts.fixesHigh)
        assertEquals(30, counts.stolenMinutes)
    }

    @Test fun keepsOnlyTheLastSevenDays() {
        val l = BatteryLedger()
        for (i in 0..9) l.add(Key.PHOTOS, 1.0, at(day1.minusDays(i.toLong()), 12), zone)
        l.prune(day1)
        assertEquals(7, l.days.size)
        assertEquals(day1.minusDays(6), l.days.keys.minOrNull())
        assertEquals(7, BatteryReport.week(l, day1).size)
    }

    @Test fun survivesSavingAndLoading() {
        val l = BatteryLedger()
        l.record(snap(at(day1, 10), 10 * hour, Key.CPU_MS to 1_000, Key.WIFI_MAMS to 0), zone)
        l.record(snap(at(day1, 12), 12 * hour, Key.CPU_MS to 2_500, Key.WIFI_MAMS to 0), zone)
        l.add(Key.PHOTOS, 1.0, at(day1, 13), zone)
        val back = BatteryLedger.parse(l.serialize())
        assertEquals(l.last, back.last)
        assertEquals(l.days, back.days)
    }

    @Test fun unreadableStorageStartsFresh() {
        assertTrue(BatteryLedger.parse(null).days.isEmpty())
        val l = BatteryLedger.parse("garbage\nD|not-a-date|x=1\nS|1|oops|\nD|2026-11-18|cpu_ms=5.0")
        assertNull(l.last)
        assertEquals(5.0, l.totals(day1)[Key.CPU_MS]!!, 1e-9)
    }

    @Test fun emptyDayHasNoData() {
        val report = BatteryReport.build(day1, emptyMap())
        assertFalse(report.hasData)
        assertEquals(0.0, report.totalMah, 0.0)
        assertNull(report.shareOfBattery(null))
    }

    @Test fun totalsAndShareOfBattery() {
        val t = mapOf(Key.GPS_MS to 3_600_000.0, Key.CPU_MS to 360_000.0) // 1 h gps, 6 min cpu
        val r = BatteryReport.build(day1, t)
        assertEquals(BatteryReport.GPS_MA + BatteryReport.CPU_MA / 10, r.totalMah, 1e-9)
        assertEquals(r.totalMah, r.estimatedMah, 1e-9)
        assertEquals(r.totalMah / 5_000 * 100, r.shareOfBattery(5_000.0)!!, 1e-9)
    }

    @Test fun uploadSummaryHasOnlyTheAllowedFields() {
        val r = BatteryReport.build(day1, mapOf(Key.CPU_MS to 36_000.0, Key.PHONE_DROP_PCT to 12.0))
        val up = r.toUpload()
        assertEquals(setOf("day", "totalMah", "measuredMah", "parts", "counts", "phoneDropPct"), up.keys)
        assertEquals("2026-11-18", up["day"])
        assertEquals(Part.entries.map { it.key }.toSet(), (up["parts"] as Map<*, *>).keys)
        assertFalse("unknown phone drop is left out", "phoneDropPct" in BatteryReport.build(day1, emptyMap()).toUpload())
    }

    @Test fun plainWords() {
        assertEquals("0 s", BatteryReport.duration(0.0))
        assertEquals("under 1 s", BatteryReport.duration(300.0))
        assertEquals("42 s", BatteryReport.duration(42_000.0))
        assertEquals("2 min 5 s", BatteryReport.duration(125_000.0))
        assertEquals("15 min", BatteryReport.duration(15 * 60_000.0))
        assertEquals("1 h 20 min", BatteryReport.duration(80 * 60_000.0))
        assertEquals("2 h", BatteryReport.duration(2 * 3_600_000.0))
        assertEquals("512 B", BatteryReport.bytes(512.0))
        assertEquals("3 KB", BatteryReport.bytes(3_000.0))
        assertEquals("1.5 MB", BatteryReport.bytes(1.5 * 1024 * 1024))
        assertEquals("under 0.1", BatteryReport.mah(0.04))
        assertEquals("2.5", BatteryReport.mah(2.5))
        assertEquals("14", BatteryReport.mah(13.6))
    }
}
