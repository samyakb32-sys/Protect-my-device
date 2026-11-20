package com.protectmydevice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.protectmydevice.R
import com.protectmydevice.battery.BatteryReport
import com.protectmydevice.battery.DayReport
import com.protectmydevice.battery.PartUse
import com.protectmydevice.ui.components.BrutalBox
import com.protectmydevice.ui.components.BrutalIconButton
import com.protectmydevice.ui.components.Outline
import com.protectmydevice.ui.components.Sticker
import com.protectmydevice.ui.theme.Hazard
import com.protectmydevice.ui.theme.Ink
import com.protectmydevice.ui.theme.Lilac
import com.protectmydevice.ui.theme.Lime
import com.protectmydevice.ui.theme.LocalBrutal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * How much battery this app used: today in detail, then the week. [week] is newest first, today at
 * index 0. [started] is false until the first reading has been taken.
 */
@Composable
fun BatteryScreen(week: List<DayReport>, capacityMah: Double?, started: Boolean, onBack: () -> Unit) {
    val today = week.firstOrNull()
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { insets ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                Modifier.widthIn(max = 560.dp).fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 18.dp, end = 14.dp,
                    top = insets.calculateTopPadding() + 12.dp,
                    bottom = insets.calculateBottomPadding() + 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Header(onBack) }
                item { TodayHero(today, capacityMah, started) }
                if (today != null && today.hasData) {
                    item { Title("01", "where it went today") }
                    item { Breakdown(today) }
                    item { Title("02", "what the app did") }
                    item { Counts(today) }
                }
                item { Title(if (today?.hasData == true) "03" else "01", "last 7 days") }
                item { Week(week) }
                item { Title(if (today?.hasData == true) "04" else "02", "measured vs. estimated") }
                item { Notes() }
            }
        }
    }
}

@Composable
private fun Header(onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BrutalIconButton(R.drawable.ic_back, "Back", onBack)
        Spacer(Modifier.width(10.dp))
        Column {
            Text("battery use", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            Text("THIS APP ONLY", style = MaterialTheme.typography.labelSmall, color = LocalBrutal.current.muted)
        }
    }
}

@Composable
private fun TodayHero(today: DayReport?, capacityMah: Double?, started: Boolean) {
    val type = MaterialTheme.typography
    val hasData = today?.hasData == true
    BrutalBox(Modifier.fillMaxWidth(), color = if (hasData) Lime else Hazard, radius = 26.dp, shadow = 6.dp) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Sticker("today", color = Ink, contentColor = if (hasData) Lime else Hazard, outline = Ink)
                if (hasData) Sticker(accuracyLabel(today!!), color = Color.White, outline = Ink, rotation = -2f)
            }
            Spacer(Modifier.height(10.dp))
            if (!hasData) {
                Text(if (started) "nothing yet." else "measuring…", style = type.displaySmall, color = Ink)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (started) "no use recorded today so far. readings come with each location check-in, every 2.5 hours."
                    else "the first reading only sets a starting point. numbers show up after the next check-in, within 2.5 hours.",
                    style = type.bodyMedium, color = Ink,
                )
                return@Column
            }
            Text(mahText(today!!.totalMah, today.estimatedMah > 0), style = type.displayMedium, color = Ink)
            Spacer(Modifier.height(8.dp))
            val share = today.shareOfBattery(capacityMah)
            Text(
                if (share != null) "${percent(share)} of a full battery, used by this app today."
                else "used by this app today. this phone doesn't say how big its battery is, so no percentage.",
                style = type.bodyMedium, color = Ink,
            )
            today.phoneDropPct?.let { drop ->
                Spacer(Modifier.height(8.dp))
                Text(
                    "for comparison, the whole phone has dropped at least ${drop.toInt()}% today — that's every app and the screen, not just this one.",
                    style = type.bodySmall, color = Ink,
                )
            }
        }
    }
}

@Composable
private fun Title(number: String, title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, style = MaterialTheme.typography.labelMedium, color = LocalBrutal.current.muted)
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun Breakdown(day: DayReport) {
    val b = LocalBrutal.current
    val parts = day.parts.sortedByDescending { it.mah }
    val top = parts.maxOf { it.mah }.takeIf { it > 0 } ?: 1.0
    BrutalBox(Modifier.fillMaxWidth()) {
        Column {
            parts.forEachIndexed { i, p ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(Outline).background(b.line))
                PartRow(p, p.mah / top)
            }
        }
    }
}

@Composable
private fun PartRow(p: PartUse, fraction: Double) {
    val b = LocalBrutal.current
    val type = MaterialTheme.typography
    Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(p.part.label, Modifier.weight(1f), style = type.titleSmall, color = b.onCard)
            ValueChip(
                mahText(p.mah, !p.measured) + if (p.measured) " · measured" else "",
                color = if (p.measured) Lime else if (p.mah > 0) Hazard else b.raised,
                contentColor = if (p.measured || p.mah > 0) Ink else b.onCard,
            )
        }
        Bar(fraction, if (p.measured) Lime else Hazard)
        Text(p.detail, style = type.bodySmall, color = b.muted)
    }
}

/** Like a Sticker, but keeps its case: "mAh" must not turn into "MAH". */
@Composable
private fun ValueChip(text: String, color: Color, contentColor: Color) {
    val shape = RoundedCornerShape(7.dp)
    Text(
        text,
        Modifier.clip(shape).background(color).border(1.5.dp, LocalBrutal.current.line, shape)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium, color = contentColor, maxLines = 1,
    )
}

/** A flat outlined track, filled to [fraction]. */
@Composable
private fun Bar(fraction: Double, color: Color) {
    val shape = RoundedCornerShape(4.dp)
    Box(Modifier.fillMaxWidth().height(10.dp).clip(shape).border(1.5.dp, LocalBrutal.current.line, shape)) {
        val f = fraction.coerceIn(0.0, 1.0).toFloat()
        if (f > 0f) Box(Modifier.fillMaxWidth(f.coerceAtLeast(0.02f)).height(10.dp).background(color))
    }
}

@Composable
private fun Counts(day: DayReport) {
    val c = day.counts
    val type = MaterialTheme.typography
    val rows = listOf(
        "location fixes, gps-grade" to "${c.fixesHigh}",
        "location fixes, balanced" to "${c.fixesBalanced}",
        "stolen mode" to "${c.stolenMinutes} min",
        "quick-locate bursts" to "${c.locateBursts}",
        "photos taken" to "${c.photos}",
        "alarm sounding" to BatteryReport.duration(c.alarmSeconds * 1000.0),
    )
    BrutalBox(Modifier.fillMaxWidth(), color = Lilac) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (label, value) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, Modifier.weight(1f), style = type.bodyMedium, color = Ink)
                    Text(value, style = type.labelMedium, color = Ink)
                }
            }
            Text(
                "gps-grade fixes are used in stolen mode and for two minutes after “locate now”, lost mode or a low battery " +
                    "(each of those is one quick-locate burst). day to day it's the cheap balanced setting.",
                style = type.bodySmall, color = Ink,
            )
        }
    }
}

@Composable
private fun Week(week: List<DayReport>) {
    val b = LocalBrutal.current
    val type = MaterialTheme.typography
    val top = week.maxOfOrNull { it.totalMah }?.takeIf { it > 0 } ?: 1.0
    BrutalBox(Modifier.fillMaxWidth()) {
        Column {
            week.forEachIndexed { i, d ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(Outline).background(b.line))
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(dayLabel(d.date, i), Modifier.weight(1f), style = type.titleSmall, color = b.onCard)
                        Text(
                            if (d.hasData) mahText(d.totalMah, d.estimatedMah > 0) else "no data",
                            style = type.labelMedium, color = if (d.hasData) b.onCard else b.muted,
                        )
                    }
                    if (d.hasData) {
                        Bar(d.totalMah / top, if (d.estimatedMah > 0) Hazard else Lime)
                        d.phoneDropPct?.let {
                            Text("whole phone: down at least ${it.toInt()}%", style = type.bodySmall, color = b.muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Notes() {
    val b = LocalBrutal.current
    val type = MaterialTheme.typography
    BrutalBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NOTES.forEach { (title, why) ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("→", style = type.bodySmall, color = b.onCard)
                    Column {
                        Text(title, style = type.titleSmall, color = b.onCard)
                        Text(why, style = type.bodySmall, color = b.muted)
                    }
                }
            }
        }
    }
}

private val NOTES = listOf(
    "measured" to "the phone's own power meter counted it. only some phones have one, and android only splits it per app " +
        "for mobile data, wifi and bluetooth.",
    "estimate (~)" to "time in use × what that part draws on a typical phone. yours could be half or double. good for " +
        "spotting what costs the most, not for exact numbers.",
    "location can read low" to "fixes from wifi and cell towers are worked out by google play services, and android may " +
        "count that under play services instead of this app.",
    "not counted" to "the screen, the bluetooth beacon's broadcasts, and anything after a crash or forced restart until " +
        "the next reading. the counters reset when the phone restarts; the app saves a reading just before a normal shutdown.",
    "exact numbers" to "settings → battery shows this app's share as android sees it. a developer can get a full " +
        "per-part breakdown over usb with scripts/battery-report.sh.",
    "only this app" to "android only lets the app read its own use, and that's all it looks at — never other apps. " +
        "each finished day's totals are saved to your account so the website can show them.",
)

private fun accuracyLabel(d: DayReport) = when {
    d.measuredMah <= 0 -> "estimate"
    d.estimatedMah > 0 -> "partly estimated"
    else -> "measured"
}

private fun mahText(v: Double, estimated: Boolean) = (if (estimated && v > 0) "~" else "") + BatteryReport.mah(v) + " mAh"

private fun percent(v: Double) = when {
    v < 0.1 -> "under 0.1%"
    v < 10 -> String.format(Locale.ROOT, "about %.1f%%", v)
    else -> "about ${v.toInt()}%"
}

private fun dayLabel(date: LocalDate, index: Int) = when (index) {
    0 -> "today"
    1 -> "yesterday"
    else -> Locale.getDefault().let { date.format(DateTimeFormatter.ofPattern("EEE d MMM", it)).lowercase(it) }
}
