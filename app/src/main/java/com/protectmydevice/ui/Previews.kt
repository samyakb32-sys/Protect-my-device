package com.protectmydevice.ui

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.protectmydevice.data.DeviceInfo
import com.protectmydevice.ui.screens.HomeScreen
import com.protectmydevice.ui.screens.LoginBusy
import com.protectmydevice.ui.screens.LoginScreen
import com.protectmydevice.ui.screens.LostModeScreen
import com.protectmydevice.ui.screens.Protection
import com.protectmydevice.ui.theme.ProtectTheme

// Android Studio previews with made-up data. Nothing here ships behaviour.

internal val SampleDevices = listOf(
    DeviceInfo("a", "Nothing Phone (2a)", 19.076, 72.8777, 82),
    DeviceInfo("b", "Samsung Galaxy A54", null, null, 12),
)

@Composable
internal fun HomePreview(dark: Boolean, done: Int) = ProtectTheme(darkTheme = dark) {
    HomeScreen(
        protection = Protection(tracking = done >= 1, locationGranted = done >= 1, alwaysLocation = done >= 2, admin = done >= 3),
        devices = SampleDevices, thisDeviceId = "a", refreshing = false,
        snackbar = remember { SnackbarHostState() },
        onStartTracking = {}, onAllowAlways = {}, onEnableAdmin = {}, onTrustSim = {},
        onRefresh = {}, onSignOut = {}, onOpenMap = {}, onCommand = { _, _ -> },
    )
}

@Preview(name = "Home · locked in", heightDp = 1400) @Composable private fun HomeDone() = HomePreview(dark = false, done = 3)
@Preview(name = "Home · setup", heightDp = 1400) @Composable private fun HomeSetup() = HomePreview(dark = false, done = 1)
@Preview(name = "Home · dark", heightDp = 1400) @Composable private fun HomeDark() = HomePreview(dark = true, done = 3)

@Preview(name = "Login") @Composable
private fun Login() = ProtectTheme(darkTheme = false) { LoginScreen("", LoginBusy.NONE, { _, _ -> }, { _, _ -> }) }

@Preview(name = "Lost mode") @Composable
private fun Lost() = ProtectTheme(darkTheme = false) { LostModeScreen("This phone is lost. Please call the owner.") }
