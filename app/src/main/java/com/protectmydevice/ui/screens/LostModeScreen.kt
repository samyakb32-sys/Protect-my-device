package com.protectmydevice.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.protectmydevice.R
import com.protectmydevice.ui.components.BrutalBox
import com.protectmydevice.ui.components.Sticker
import com.protectmydevice.ui.theme.Hazard
import com.protectmydevice.ui.theme.Ink

/**
 * Shown over the lock screen to whoever is holding the phone, so it drops the playful tone:
 * hazard yellow, tape stripes, and the owner's message as the only thing to read.
 * Wrap in `ProtectTheme(darkTheme = false)` — it is the same in light and dark.
 */
@Composable
fun LostModeScreen(message: String) {
    val type = MaterialTheme.typography
    val headline = type.displayLarge.copy(fontSize = 92.sp, lineHeight = 82.sp)
    Column(Modifier.fillMaxSize().background(Hazard).systemBarsPadding()) {
        HazardTape()
        BoxWithConstraints(Modifier.weight(1f)) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Sticker("lost mode is on", color = Ink, contentColor = Hazard, icon = R.drawable.ic_shield_alert, outline = Ink)
                    Spacer(Modifier.height(18.dp))
                    Text("LOST\nPHONE", style = headline, color = Ink)
                    Spacer(Modifier.height(24.dp))
                    BrutalBox(Modifier.fillMaxWidth(), radius = 22.dp, shadow = 6.dp) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("MESSAGE FROM THE OWNER", style = type.labelSmall, color = Ink.copy(alpha = 0.6f))
                            Text(message, style = type.headlineMedium, color = Ink)
                        }
                    }
                }
                Row(
                    Modifier.padding(top = 28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.ic_locate), null, Modifier.size(24.dp), tint = Hazard)
                    }
                    Text("This phone is reporting its location to its owner.", style = type.titleMedium, color = Ink)
                }
            }
        }
        HazardTape()
    }
}

/** Diagonal black/yellow warning tape. */
@Composable
private fun HazardTape() {
    Canvas(Modifier.fillMaxWidth().height(22.dp)) {
        val stripe = 18.dp.toPx()
        val h = size.height
        clipRect {
            var x = -h
            while (x < size.width) {
                drawPath(
                    Path().apply {
                        moveTo(x, h); lineTo(x + h, 0f); lineTo(x + h + stripe, 0f); lineTo(x + stripe, h); close()
                    },
                    Ink,
                )
                x += stripe * 2
            }
        }
    }
}
