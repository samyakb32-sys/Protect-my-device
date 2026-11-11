package com.protectmydevice.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Full-screen "this phone is lost" message with the owner's contact text, shown over the lock screen. */
class LostModeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val msg = intent.getStringExtra(EXTRA_MESSAGE).orEmpty().ifBlank { "This phone is lost. Please contact the owner." }
        setContent {
            MaterialTheme {
                Column(Modifier.fillMaxSize().padding(32.dp)) {
                    Text("LOST PHONE", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.error)
                    Text(msg, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
    }

    companion object { const val EXTRA_MESSAGE = "message" }
}
