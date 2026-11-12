package com.protectmydevice.ui

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.protectmydevice.admin.AdminReceiver
import com.protectmydevice.data.Command
import com.protectmydevice.data.DeviceInfo
import com.protectmydevice.data.DeviceRepository
import com.protectmydevice.location.TrackingService
import com.protectmydevice.security.PowerMenuGuard
import com.protectmydevice.security.SimMonitor
import com.protectmydevice.ui.screens.HomeScreen
import com.protectmydevice.ui.screens.LoginBusy
import com.protectmydevice.ui.screens.LoginScreen
import com.protectmydevice.ui.screens.Protection
import com.protectmydevice.ui.screens.label
import com.protectmydevice.ui.theme.ProtectTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Wires Firebase, permissions and device admin to the stateless screens in `ui/screens`. */
class MainActivity : ComponentActivity() {
    /** Bumped whenever we come back to the foreground, so permission/admin state is re-read. */
    private var resumes by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ProtectTheme { App() } }
    }

    override fun onResume() {
        super.onResume()
        resumes++
    }

    @Composable
    private fun App() {
        var loggedIn by remember { mutableStateOf(Firebase.auth.currentUser != null) }
        if (!loggedIn) Login { loggedIn = true } else Home { Firebase.auth.signOut(); loggedIn = false }
    }

    @Composable
    private fun Login(onDone: () -> Unit) {
        var error by remember { mutableStateOf("") }
        var notice by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(LoginBusy.NONE) }
        val scope = rememberCoroutineScope()
        fun attempt(kind: LoginBusy, email: String, pass: String) {
            busy = kind; error = ""; notice = ""
            scope.launch {
                runCatching {
                    if (kind == LoginBusy.CREATE) {
                        val created = Firebase.auth.createUserWithEmailAndPassword(email, pass).await()
                        // Best effort: a confirmed address is what makes password reset trustworthy later.
                        runCatching { created.user?.sendEmailVerification()?.await() }
                    } else Firebase.auth.signInWithEmailAndPassword(email, pass).await()
                }.onSuccess { onDone() }.onFailure { error = it.message.orEmpty(); busy = LoginBusy.NONE }
            }
        }
        LoginScreen(
            error = error, notice = notice, busy = busy,
            onForgotPassword = { email ->
                error = ""; notice = ""
                if (email.isBlank()) error = "type your email above, then tap this again."
                else scope.launch {
                    // Same message whether or not the address has an account, so this can't be used to probe for one.
                    runCatching { Firebase.auth.sendPasswordResetEmail(email).await() }
                    notice = "if $email has an account, a reset link is on its way."
                }
            },
            onSignIn = { e, p -> attempt(LoginBusy.SIGN_IN, e, p) },
            onCreateAccount = { e, p -> attempt(LoginBusy.CREATE, e, p) },
        )
    }

    private fun granted(permission: String) =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    @Composable
    private fun Home(onSignOut: () -> Unit) {
        val repo = remember { DeviceRepository(this) }
        val scope = rememberCoroutineScope()
        val snackbar = remember { SnackbarHostState() }
        fun say(message: String) {
            if (message.isNotEmpty()) scope.launch {
                snackbar.currentSnackbarData?.dismiss()
                snackbar.showSnackbar(message)
            }
        }

        var devices by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
        var refreshing by remember { mutableStateOf(false) }
        var permissionResults by remember { mutableIntStateOf(0) }
        val trackingRunning by TrackingService.running.collectAsState()
        val protection = remember(resumes, permissionResults, trackingRunning) {
            val location = granted(Manifest.permission.ACCESS_FINE_LOCATION)
            Protection(
                tracking = location && trackingRunning,
                locationGranted = location,
                alwaysLocation = location &&
                    (Build.VERSION.SDK_INT < 29 || granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)),
                admin = AdminReceiver.isActive(this),
            )
        }

        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.CAMERA)
            add(Manifest.permission.READ_PHONE_STATE)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
        val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            SimMonitor.saveBaseline(this)
            TrackingService.start(this)
            permissionResults++
            say(
                if (granted(Manifest.permission.ACCESS_FINE_LOCATION)) "tracking is on"
                else "can't track without location access"
            )
        }
        val bgLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            permissionResults++
        }

        fun refresh() = scope.launch {
            refreshing = true
            devices = runCatching { repo.myDevices() }.getOrDefault(devices)
            refreshing = false
        }
        LaunchedEffect(Unit) {
            runCatching { repo.register() }
            refresh()
        }

        HomeScreen(
            protection = protection,
            devices = devices,
            thisDeviceId = repo.deviceId,
            accountEmail = Firebase.auth.currentUser?.email.orEmpty(),
            refreshing = refreshing,
            powerGuardOn = remember(resumes) { PowerMenuGuard.isEnabled(this) },
            snackbar = snackbar,
            onStartTracking = { permLauncher.launch(perms) },
            onAllowAlways = {
                if (Build.VERSION.SDK_INT >= 29) bgLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            },
            onEnableAdmin = {
                startActivity(
                    Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, AdminReceiver.component(this))
                        .putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            getString(com.protectmydevice.R.string.admin_explanation),
                        )
                )
            },
            onTrustSim = { SimMonitor.saveBaseline(this); say("got it — this SIM is trusted now") },
            onPowerGuard = {
                runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                say("find “block power-off while locked” in the list and switch it on")
            },
            onFindHub = {
                // No public deep link exists for Find Hub; try Google's own settings action, else Security.
                runCatching { startActivity(Intent("com.google.android.gms.settings.FIND_MY_DEVICE_SETTINGS")) }
                    .recoverCatching { startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) }
            },
            onRefresh = { refresh() },
            onSignOut = onSignOut,
            onOpenMap = { d ->
                runCatching {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${d.lat},${d.lng}?q=${d.lat},${d.lng}(${d.name})")))
                }.onFailure { say("no maps app on this phone") }
            },
            onCommand = { d, c ->
                val message = when (c) {
                    Command.LOST_MODE, Command.STOLEN_MODE -> "This phone is lost. Please call the owner."
                    else -> ""
                }
                scope.launch {
                    runCatching { repo.sendCommand(d.id, c, message) }
                        .onSuccess {
                            say("${c.label} sent to ${d.name}")
                            if (c == Command.STOLEN_MODE || c == Command.SAFE) refresh()
                        }
                        .onFailure { say(it.message.orEmpty()) }
                }
            },
        )
    }
}
