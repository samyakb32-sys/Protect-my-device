package com.protectmydevice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.protectmydevice.R
import com.protectmydevice.ui.components.BrandMark
import com.protectmydevice.ui.components.BrutalButton
import com.protectmydevice.ui.components.Outline
import com.protectmydevice.ui.components.Sticker
import com.protectmydevice.ui.components.hardShadow
import com.protectmydevice.ui.theme.Coral
import com.protectmydevice.ui.theme.Hazard
import com.protectmydevice.ui.theme.Ink
import com.protectmydevice.ui.theme.Lime
import com.protectmydevice.ui.theme.LocalBrutal

enum class LoginBusy { NONE, SIGN_IN, CREATE }

@Composable
fun LoginScreen(
    error: String,
    notice: String,
    busy: LoginBusy,
    onSignIn: (email: String, password: String) -> Unit,
    onCreateAccount: (email: String, password: String) -> Unit,
    onForgotPassword: (email: String) -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var nudge by rememberSaveable { mutableStateOf(false) }
    val shownError = if (nudge && (email.isBlank() || pass.isEmpty())) "type your email and a password first." else error
    fun submit(action: (String, String) -> Unit) {
        nudge = email.isBlank() || pass.isEmpty()
        if (!nudge) action(email.trim(), pass)
    }
    val b = LocalBrutal.current
    val type = MaterialTheme.typography

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = 520.dp)
                .fillMaxSize()
                .systemBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BrandMark(size = 40.dp)
                Text("protect my device", style = type.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            }

            Spacer(Modifier.height(36.dp))
            Text(
                buildAnnotatedString {
                    append("find it.\n")
                    withStyle(SpanStyle(background = Lime, color = Ink)) { append("lock it.") }
                    append("\nwipe it.")
                },
                style = type.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Sticker("no sim needed", color = Hazard, rotation = -3f)
                Text("just wifi or data.", style = type.bodyMedium, color = b.muted)
            }

            Spacer(Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                BrutalField(
                    value = email, onValueChange = { email = it },
                    label = "email", placeholder = "you@example.com",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                )
                BrutalField(
                    value = pass, onValueChange = { pass = it },
                    label = "password", placeholder = "6+ characters",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit(onSignIn) }),
                    visualTransformation = PasswordVisualTransformation(),
                )
            }

            if (shownError.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val shape = RoundedCornerShape(14.dp)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(Coral)
                        .border(Outline, b.line, shape)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(painterResource(R.drawable.ic_warning), null, Modifier.size(20.dp), tint = Ink)
                    Text(shownError, style = type.bodyMedium, color = Ink)
                }
            }

            if (notice.isNotEmpty() && shownError.isEmpty()) {
                Spacer(Modifier.height(14.dp))
                val shape = RoundedCornerShape(14.dp)
                Text(
                    notice,
                    Modifier.fillMaxWidth().clip(shape).background(Lime).border(Outline, b.line, shape).padding(12.dp),
                    style = type.bodyMedium, color = Ink,
                )
            }

            Spacer(Modifier.height(22.dp))
            BrutalButton(
                "sign in", onClick = { submit(onSignIn) },
                modifier = Modifier.fillMaxWidth(),
                enabled = busy != LoginBusy.CREATE, loading = busy == LoginBusy.SIGN_IN,
            )
            Spacer(Modifier.height(10.dp))
            BrutalButton(
                "i'm new — make an account", onClick = { submit(onCreateAccount) },
                modifier = Modifier.fillMaxWidth(), color = b.card, contentColor = b.onCard,
                enabled = busy != LoginBusy.SIGN_IN, loading = busy == LoginBusy.CREATE,
            )

            Spacer(Modifier.height(18.dp))
            Text(
                "forgot your password?",
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(role = Role.Button) { nudge = false; onForgotPassword(email.trim()) }
                    .padding(vertical = 8.dp),
                style = type.titleSmall.copy(textDecoration = TextDecoration.Underline),
                color = MaterialTheme.colorScheme.onBackground,
            )

            Spacer(Modifier.height(12.dp))
            Text(
                "set it up before the phone goes missing. it can't help after.",
                style = type.bodySmall, color = b.muted,
            )
        }
    }
}

/** Text field as a plain outlined slab; it picks up a hard shadow while focused. */
@Composable
private fun BrutalField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val b = LocalBrutal.current
    val type = MaterialTheme.typography
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label.uppercase(), style = type.labelSmall, color = b.muted)
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            singleLine = true,
            textStyle = type.bodyLarge.copy(color = b.onCard),
            cursorBrush = SolidColor(b.onCard),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                Box(
                    Modifier
                        .padding(end = 4.dp, bottom = 4.dp)
                        .then(if (focused) Modifier.hardShadow(b.shadow, 4.dp, 14.dp) else Modifier)
                        .clip(shape)
                        .background(b.card)
                        .border(Outline, b.line, shape)
                        .padding(horizontal = 16.dp, vertical = 15.dp),
                ) {
                    if (value.isEmpty()) Text(placeholder, style = type.bodyLarge, color = b.muted.copy(alpha = 0.7f))
                    inner()
                }
            },
        )
    }
}
