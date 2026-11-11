package com.protectmydevice.ui

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.protectmydevice.ui.screens.LostModeScreen
import com.protectmydevice.ui.theme.ProtectTheme

/** Full-screen "this phone is lost" message with the owner's contact text, shown over the lock screen. */
class LostModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // The screen is yellow in every theme, so the system bar icons must always be dark.
        val bars = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
        super.onCreate(savedInstanceState)
        val msg = intent.getStringExtra(EXTRA_MESSAGE).orEmpty().ifBlank { "This phone is lost. Please contact the owner." }
        setContent { ProtectTheme(darkTheme = false) { LostModeScreen(msg) } }
    }

    companion object { const val EXTRA_MESSAGE = "message" }
}
