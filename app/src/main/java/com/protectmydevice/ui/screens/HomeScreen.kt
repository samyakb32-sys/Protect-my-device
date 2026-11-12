package com.protectmydevice.ui.screens

import androidx.annotation.DrawableRes
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.protectmydevice.R
import com.protectmydevice.data.Command
import com.protectmydevice.data.DeviceInfo
import com.protectmydevice.ui.components.BrandMark
import com.protectmydevice.ui.components.BrutalBox
import com.protectmydevice.ui.components.BrutalButton
import com.protectmydevice.ui.components.BrutalIconButton
import com.protectmydevice.ui.components.BrutalPressable
import com.protectmydevice.ui.components.Outline
import com.protectmydevice.ui.components.Sticker
import com.protectmydevice.ui.theme.Coral
import com.protectmydevice.ui.theme.Hazard
import com.protectmydevice.ui.theme.Ink
import com.protectmydevice.ui.theme.Lilac
import com.protectmydevice.ui.theme.Lime
import com.protectmydevice.ui.theme.LocalBrutal
import com.protectmydevice.ui.theme.Mono

/** What is actually switched on for this phone right now. */
data class Protection(
    val tracking: Boolean,        // location granted and the tracking service is running
    val locationGranted: Boolean, // step 2 can only be asked for once this is true
    val alwaysLocation: Boolean,
    val admin: Boolean,
) {
    val done: Int get() = listOf(tracking, alwaysLocation, admin).count { it }
    val complete: Boolean get() = done == 3
}

/** Button label for each remote command; also used in the "… sent" confirmation. */
val Command.label: String
    get() = when (this) {
        Command.LOCATE -> "locate"
        Command.ALARM -> "alarm"
        Command.STOP_ALARM -> "stop"
        Command.LOCK -> "lock"
        Command.LOST_MODE -> "lost mode"
        Command.WIPE -> "erase"
        Command.STOLEN_MODE -> "stolen mode"
        Command.SAFE -> "all clear"
        Command.PHOTO -> "photo"
    }

@Composable
fun HomeScreen(
    protection: Protection,
    devices: List<DeviceInfo>,
    thisDeviceId: String?,
    accountEmail: String,
    refreshing: Boolean,
    powerGuardOn: Boolean,
    snackbar: SnackbarHostState,
    onStartTracking: () -> Unit,
    onAllowAlways: () -> Unit,
    onEnableAdmin: () -> Unit,
    onTrustSim: () -> Unit,
    onPowerGuard: () -> Unit,
    onFindHub: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit,
    onOpenMap: (DeviceInfo) -> Unit,
    onCommand: (DeviceInfo, Command) -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbar) {
                Snackbar(
                    it,
                    shape = RoundedCornerShape(14.dp),
                    containerColor = MaterialTheme.colorScheme.inverseSurface,
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                )
            }
        },
    ) { insets ->
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
                item { TopBar(accountEmail, onSignOut) }
                item { StatusHero(protection) }

                item { SectionTitle("01", "this phone") }
                item { SetupCard(protection, onStartTracking, onAllowAlways, onEnableAdmin) }
                item { SimRow(onTrustSim) }
                item { ImeiTip() }

                item { SectionTitle("02", "extra armour") }
                item { ArmourCard(powerGuardOn, onPowerGuard, onFindHub) }

                item {
                    SectionTitle("03", "your devices") {
                        BrutalIconButton(R.drawable.ic_refresh, "Refresh devices", onRefresh, loading = refreshing)
                    }
                }
                if (devices.isEmpty()) item { NoDevices() }
                items(devices, key = { it.id }) { d ->
                    DeviceCard(d, isThisPhone = d.id == thisDeviceId, onOpenMap = { onOpenMap(d) }) { onCommand(d, it) }
                }
            }
        }
    }
}

@Composable
private fun TopBar(accountEmail: String, onSignOut: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size = 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("protect my device", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            if (accountEmail.isNotEmpty()) Text(
                accountEmail, style = MaterialTheme.typography.labelSmall, color = LocalBrutal.current.muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        BrutalIconButton(R.drawable.ic_logout, "Sign out", onSignOut)
    }
}

@Composable
private fun StatusHero(p: Protection) {
    val type = MaterialTheme.typography
    val (headline, body) = when {
        p.complete -> "locked in." to "tracking, always-on location and remote lock are all running."
        p.done == 0 -> "not safe yet." to "nothing's switched on. do the 3 steps below — it only works if it's set up before the phone goes missing."
        else -> "almost there." to "${3 - p.done} more to go. finish up so you can find, lock and erase this phone from anywhere."
    }
    BrutalBox(Modifier.fillMaxWidth(), color = if (p.complete) Lime else Hazard, radius = 26.dp, shadow = 6.dp) {
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Sticker("status", color = Ink, contentColor = if (p.complete) Lime else Hazard, outline = Ink)
                Spacer(Modifier.weight(1f))
                // The seal: sits slightly crooked on purpose.
                Box(
                    Modifier.rotate(9f).size(46.dp).clip(CircleShape).background(Ink),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painterResource(if (p.complete) R.drawable.ic_shield_check else R.drawable.ic_shield_alert),
                        null, Modifier.size(26.dp), tint = if (p.complete) Lime else Hazard,
                    )
                }
            }
            Text(headline, style = type.displayMedium, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text(body, style = type.bodyMedium, color = Ink)
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(p.tracking, p.alwaysLocation, p.admin).forEach { on ->
                    val seg = RoundedCornerShape(5.dp)
                    Box(
                        Modifier.weight(1f).height(14.dp).clip(seg)
                            .background(if (on) Ink else Color.Transparent)
                            .border(Outline, Ink, seg),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text("${p.done}/3", style = type.labelMedium, color = Ink)
            }
        }
    }
}

@Composable
private fun SectionTitle(number: String, title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(number, style = MaterialTheme.typography.labelMedium, color = LocalBrutal.current.muted)
        Spacer(Modifier.width(10.dp))
        Text(
            title,
            Modifier.weight(1f),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        trailing()
    }
}

@Composable
private fun SetupCard(p: Protection, onStartTracking: () -> Unit, onAllowAlways: () -> Unit, onEnableAdmin: () -> Unit) {
    val line = LocalBrutal.current.line
    BrutalBox(Modifier.fillMaxWidth()) {
        Column {
            StepRow(
                "01", "start tracking", "location, camera + phone access, so it can report where it is.",
                done = p.tracking, action = "turn on", onClick = onStartTracking,
            )
            Box(Modifier.fillMaxWidth().height(Outline).background(line))
            StepRow(
                "02", "always-on location", "pick “allow all the time” so tracking survives in the background.",
                done = p.alwaysLocation, action = "allow", enabled = p.locationGranted, onClick = onAllowAlways,
            )
            Box(Modifier.fillMaxWidth().height(Outline).background(line))
            StepRow(
                "03", "remote lock + erase", "device admin — lets you lock or wipe it from another phone.",
                done = p.admin, action = "enable", onClick = onEnableAdmin,
            )
        }
    }
}

/** Things beyond the basic setup: one experimental trick of ours, and the system network that works when we can't. */
@Composable
private fun ArmourCard(powerGuardOn: Boolean, onPowerGuard: () -> Unit, onFindHub: () -> Unit) {
    val b = LocalBrutal.current
    val type = MaterialTheme.typography
    BrutalBox(Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("block power-off", style = type.titleMedium, color = b.onCard)
                        Sticker(if (powerGuardOn) "on" else "experimental", color = if (powerGuardOn) Lime else Hazard)
                    }
                    Text(
                        "closes the power menu while the phone is locked, so a thief can't just switch it off. " +
                            "turn it on under accessibility. won't stop a forced restart.",
                        style = type.bodySmall, color = b.muted,
                    )
                }
                if (!powerGuardOn) {
                    Spacer(Modifier.width(10.dp))
                    BrutalButton("set up", onPowerGuard, compact = true)
                }
            }
            Box(Modifier.fillMaxWidth().height(Outline).background(b.line))
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("find it when it's off", style = type.titleMedium, color = b.onCard)
                    Text(
                        "no app can report from a dead phone — only google's find hub network can, on phones " +
                            "built for it. switch it on, set to “with network in all areas”.",
                        style = type.bodySmall, color = b.muted,
                    )
                }
                Spacer(Modifier.width(10.dp))
                BrutalButton("open", onFindHub, color = b.card, contentColor = b.onCard, compact = true)
            }
        }
    }
}

@Composable
private fun StepRow(
    number: String,
    title: String,
    detail: String,
    done: Boolean,
    action: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val b = LocalBrutal.current
    val type = MaterialTheme.typography
    val badge = RoundedCornerShape(10.dp)
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(badge).background(if (done) Lime else Color.Transparent).border(Outline, b.line, badge),
            contentAlignment = Alignment.Center,
        ) {
            if (done) Icon(painterResource(R.drawable.ic_check), "Done", Modifier.size(20.dp), tint = Ink)
            else Text(number, style = type.labelMedium, color = b.onCard)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.titleMedium, color = b.onCard)
            Text(detail, style = type.bodySmall, color = b.muted)
        }
        if (!done) {
            Spacer(Modifier.width(10.dp))
            BrutalButton(action, onClick, compact = true, enabled = enabled)
        }
    }
}

@Composable
private fun SimRow(onTrustSim: () -> Unit) {
    val b = LocalBrutal.current
    val type = MaterialTheme.typography
    BrutalBox(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(painterResource(R.drawable.ic_sim), null, Modifier.size(26.dp), tint = b.onCard)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("swapped your SIM?", style = type.titleMedium, color = b.onCard)
                Text("tell the app it's yours, or it keeps flagging the swap.", style = type.bodySmall, color = b.muted)
            }
            Spacer(Modifier.width(10.dp))
            BrutalButton("it's mine", onTrustSim, color = b.card, contentColor = b.onCard, compact = true)
        }
    }
}

@Composable
private fun ImeiTip() {
    val type = MaterialTheme.typography
    BrutalBox(Modifier.fillMaxWidth(), color = Lilac) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Sticker("do this now", color = Color.White, rotation = -2f, outline = Ink)
            Text(
                buildAnnotatedString {
                    append("dial ")
                    withStyle(SpanStyle(fontFamily = Mono, background = Ink, color = Lilac)) { append(" *#06# ") }
                    append(" and screenshot your IMEI. you'll need it for a police or CEIR report.")
                },
                style = type.bodyMedium, color = Ink,
            )
        }
    }
}

@Composable
private fun NoDevices() {
    val b = LocalBrutal.current
    BrutalBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("nothing here yet.", style = MaterialTheme.typography.titleMedium, color = b.onCard)
            Text(
                "every phone you sign in on shows up here, so you can reach it from any of the others.",
                style = MaterialTheme.typography.bodySmall, color = b.muted,
            )
        }
    }
}

@Composable
private fun DeviceCard(d: DeviceInfo, isThisPhone: Boolean, onOpenMap: () -> Unit, onCommand: (Command) -> Unit) {
    val b = LocalBrutal.current
    val type = MaterialTheme.typography
    var confirmErase by remember { mutableStateOf(false) }
    val hasLocation = d.lat != null && d.lng != null

    BrutalBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val tile = RoundedCornerShape(12.dp)
                Box(
                    Modifier.size(46.dp).clip(tile).background(Lilac).border(Outline, b.line, tile),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_phone), null, Modifier.size(24.dp), tint = Ink) }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(d.name, style = type.titleLarge, color = b.onCard, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (isThisPhone) "THIS PHONE · " else "") + lastSeen(d, hasLocation),
                        style = type.labelSmall, color = b.muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                BatteryChip(d.battery)
            }

            if (hasLocation) {
                Spacer(Modifier.height(14.dp))
                BrutalButton(
                    "open last spot on map", onOpenMap, Modifier.fillMaxWidth(),
                    color = b.card, contentColor = b.onCard, icon = R.drawable.ic_map_pin,
                )
            }

            Spacer(Modifier.height(14.dp))
            if (d.stolen) {
                val shape = RoundedCornerShape(14.dp)
                Column(
                    Modifier.fillMaxWidth().clip(shape).background(Hazard).border(Outline, b.line, shape).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Sticker("stolen mode", color = Ink, contentColor = Hazard, outline = Ink, icon = R.drawable.ic_shield_alert)
                        Text("pinging every 10 min", style = type.titleSmall, color = Ink)
                    }
                    BrutalButton(
                        "got it back — all clear", { onCommand(Command.SAFE) }, Modifier.fillMaxWidth(),
                        color = Lime, icon = R.drawable.ic_shield_check,
                    )
                }
            } else {
                BrutalButton(
                    "it's been stolen", { onCommand(Command.STOLEN_MODE) }, Modifier.fillMaxWidth(),
                    color = Hazard, icon = R.drawable.ic_shield_alert,
                )
                Text(
                    "locks it, shows the lost screen, takes a photo and reports its location every 10 minutes.",
                    Modifier.padding(top = 6.dp, end = 4.dp), style = type.bodySmall, color = b.muted,
                )
            }

            Spacer(Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionTile(Command.LOCATE, R.drawable.ic_locate, Lime, Modifier.weight(1f), onCommand)
                    ActionTile(Command.ALARM, R.drawable.ic_alarm, Hazard, Modifier.weight(1f), onCommand)
                    ActionTile(Command.STOP_ALARM, R.drawable.ic_stop, b.card, Modifier.weight(1f), onCommand)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionTile(Command.LOCK, R.drawable.ic_lock, Lilac, Modifier.weight(1f), onCommand)
                    ActionTile(Command.LOST_MODE, R.drawable.ic_lost, b.card, Modifier.weight(1f), onCommand)
                    ActionTile(Command.PHOTO, R.drawable.ic_camera, b.card, Modifier.weight(1f), onCommand)
                }
                BrutalButton(
                    "erase everything", { confirmErase = true }, Modifier.fillMaxWidth().padding(top = 6.dp),
                    color = Coral, icon = R.drawable.ic_erase,
                )
            }
        }
    }

    if (confirmErase) {
        EraseDialog(d.name, onDismiss = { confirmErase = false }) {
            confirmErase = false
            onCommand(Command.WIPE)
        }
    }
}

private fun lastSeen(d: DeviceInfo, hasLocation: Boolean): String {
    val seen = d.lastSeenMs ?: return if (hasLocation) "LAST SPOT SAVED" else "NO LOCATION YET"
    val minutes = ((System.currentTimeMillis() - seen) / 60_000).coerceAtLeast(0)
    return "SEEN " + when {
        minutes < 1 -> "JUST NOW"
        minutes < 60 -> "$minutes MIN AGO"
        minutes < 48 * 60 -> "${minutes / 60} H AGO"
        else -> "${minutes / (24 * 60)} D AGO"
    }
}

@Composable
private fun BatteryChip(battery: Int?) {
    val color = when {
        battery == null -> LocalBrutal.current.raised
        battery <= 15 -> Coral
        battery <= 35 -> Hazard
        else -> Lime
    }
    val content = if (battery == null) LocalBrutal.current.onCard else Ink
    Sticker(battery?.let { "$it%" } ?: "--", color = color, contentColor = content, icon = R.drawable.ic_battery)
}

@Composable
private fun ActionTile(
    command: Command,
    @DrawableRes icon: Int,
    color: Color,
    modifier: Modifier,
    onClick: (Command) -> Unit,
) {
    val b = LocalBrutal.current
    val content = if (color == b.card) b.onCard else Ink
    BrutalPressable({ onClick(command) }, modifier, color = color, radius = 14.dp, lift = 3.dp) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 13.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(painterResource(icon), null, Modifier.size(24.dp), tint = content)
            Text(command.label, style = MaterialTheme.typography.titleSmall, color = content, maxLines = 1)
        }
    }
}

@Composable
private fun EraseDialog(deviceName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) { EraseCard(deviceName, onDismiss, onConfirm) }
}

@Composable
internal fun EraseCard(deviceName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val b = LocalBrutal.current
    val type = MaterialTheme.typography
    BrutalBox(radius = 24.dp, shadow = 6.dp) {
        Column {
            Row(
                Modifier.fillMaxWidth().background(Coral).padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(18.dp), tint = Ink)
                Text("NO UNDO", style = type.labelMedium, color = Ink)
            }
            Box(Modifier.fillMaxWidth().height(Outline).background(b.line))
            Column(Modifier.padding(18.dp)) {
                Text("erase $deviceName?", style = type.headlineMedium, color = b.onCard)
                Spacer(Modifier.height(8.dp))
                Text(
                    "this factory-resets the phone. everything on it is gone for good, and you won't be able to track or lock it afterwards.",
                    style = type.bodyMedium, color = b.muted,
                )
                Spacer(Modifier.height(18.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BrutalButton("keep it", onDismiss, Modifier.weight(1f), color = b.card, contentColor = b.onCard)
                    BrutalButton("erase it", onConfirm, Modifier.weight(1f), color = Coral, icon = R.drawable.ic_erase)
                }
            }
        }
    }
}
