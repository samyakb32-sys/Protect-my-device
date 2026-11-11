package com.protectmydevice.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.protectmydevice.admin.AdminReceiver
import com.protectmydevice.data.Command
import com.protectmydevice.data.DeviceInfo
import com.protectmydevice.data.DeviceRepository
import com.protectmydevice.location.TrackingService
import com.protectmydevice.security.SimMonitor
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { App() } }
    }

    @Composable
    private fun App() {
        var loggedIn by remember { mutableStateOf(Firebase.auth.currentUser != null) }
        if (!loggedIn) LoginScreen { loggedIn = true } else HomeScreen { Firebase.auth.signOut(); loggedIn = false }
    }

    @Composable
    private fun LoginScreen(onDone: () -> Unit) {
        var email by remember { mutableStateOf("") }
        var pass by remember { mutableStateOf("") }
        var error by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Protect My Device", style = MaterialTheme.typography.headlineMedium)
            OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true)
            OutlinedTextField(pass, { pass = it }, label = { Text("Password (min 6)") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    scope.launch {
                        runCatching { Firebase.auth.signInWithEmailAndPassword(email, pass).await() }
                            .onSuccess { onDone() }.onFailure { error = it.message.orEmpty() }
                    }
                }) { Text("Sign in") }
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching { Firebase.auth.createUserWithEmailAndPassword(email, pass).await() }
                            .onSuccess { onDone() }.onFailure { error = it.message.orEmpty() }
                    }
                }) { Text("Create account") }
            }
            if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }

    @Composable
    private fun HomeScreen(onSignOut: () -> Unit) {
        val repo = remember { DeviceRepository(this) }
        val scope = rememberCoroutineScope()
        var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
        var adminOn by remember { mutableStateOf(AdminReceiver.isActive(this)) }
        var status by remember { mutableStateOf("") }

        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.CAMERA)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            SimMonitor.saveBaseline(this)
            TrackingService.start(this)
            status = "Protection started"
        }
        val bgLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

        LaunchedEffect(Unit) {
            runCatching { repo.register() }
            devices = runCatching { repo.myDevices() }.getOrDefault(emptyList())
        }

        Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("This phone", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { permLauncher.launch(perms) }) { Text("1. Grant permissions & start tracking") }
            OutlinedButton(onClick = {
                if (Build.VERSION.SDK_INT >= 29) bgLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }) { Text("2. Allow location 'all the time'") }
            Button(enabled = !adminOn, onClick = {
                startActivity(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                    .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(this@MainActivity))
                    .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, getString(com.protectmydevice.R.string.admin_explanation)))
                adminOn = AdminReceiver.isActive(this@MainActivity)
            }) { Text(if (adminOn) "3. Lock/Wipe enabled ✓" else "3. Enable remote lock/wipe") }
            OutlinedButton(onClick = { SimMonitor.saveBaseline(this@MainActivity); status = "SIM baseline saved" }) {
                Text("I changed my SIM — trust current SIM")
            }
            Text("Tip: write down your IMEI (dial *#06#) and keep it safe for a police/CEIR complaint.")
            if (status.isNotEmpty()) Text(status)

            Text("My devices", style = MaterialTheme.typography.titleLarge)
            devices.forEach { d -> DeviceCard(d, repo) { status = it } }
            OutlinedButton(onClick = {
                scope.launch { devices = runCatching { repo.myDevices() }.getOrDefault(devices) }
            }) { Text("Refresh") }
            OutlinedButton(onClick = onSignOut) { Text("Sign out") }
        }
    }

    @Composable
    private fun DeviceCard(d: DeviceInfo, repo: DeviceRepository, say: (String) -> Unit) {
        val scope = rememberCoroutineScope()
        fun send(c: Command, msg: String = "") = scope.launch {
            runCatching { repo.sendCommand(d.id, c, msg) }
                .onSuccess { say("${c.name} sent to ${d.name}") }.onFailure { say(it.message.orEmpty()) }
        }
        var confirmWipe by remember { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${d.name}  ·  battery ${d.battery ?: "?"}%")
            if (d.lat != null && d.lng != null) {
                OutlinedButton(onClick = {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${d.lat},${d.lng}?q=${d.lat},${d.lng}(${d.name})")))
                }) { Text("Open last location on map") }
            } else Text("No location yet")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { send(Command.LOCATE) }) { Text("Locate") }
                OutlinedButton(onClick = { send(Command.ALARM) }) { Text("Alarm") }
                OutlinedButton(onClick = { send(Command.STOP_ALARM) }) { Text("Stop") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { send(Command.LOCK) }) { Text("Lock") }
                OutlinedButton(onClick = { send(Command.LOST_MODE, "This phone is lost. Please call the owner.") }) { Text("Lost mode") }
                OutlinedButton(onClick = { if (confirmWipe) { send(Command.WIPE); confirmWipe = false } else confirmWipe = true }) {
                    Text(if (confirmWipe) "Tap again: ERASE" else "Wipe")
                }
            }
        }
    }
}
