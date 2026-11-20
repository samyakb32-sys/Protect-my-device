package com.protectmydevice

import com.protectmydevice.security.PowerMenuGuard
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerMenuGuardTest {
    @Test fun recognisesThePowerMenuWindow() {
        assertTrue(PowerMenuGuard.isPowerMenu("com.android.systemui.globalactions.GlobalActionsDialogLite"))
        assertTrue(PowerMenuGuard.isPowerMenu("com.android.systemui.globalactions.GlobalActionsDialog\$ActionsDialog"))
    }

    @Test fun ignoresOtherSystemWindows() {
        assertFalse(PowerMenuGuard.isPowerMenu("com.android.systemui.statusbar.phone.NotificationShadeWindowView"))
        assertFalse(PowerMenuGuard.isPowerMenu(null))
    }

    @Test fun watchesTheQuickSettingsPowerButtonAndPanels() {
        assertTrue("pm_lite" in PowerMenuGuard.QUICK_SETTINGS_IDS)
        assertTrue("quick_qs_panel" in PowerMenuGuard.QUICK_SETTINGS_IDS)
    }
}
