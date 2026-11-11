package com.protectmydevice.security

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.protectmydevice.data.DeviceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/** Takes one front-camera photo (no preview shown), uploads it, and logs an event for the owner. */
class ThiefPhotoService : LifecycleService() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        val reason = intent?.getStringExtra(EXTRA_REASON) ?: "UNKNOWN"
        startForegroundNow()
        takePhoto(reason)
        return START_NOT_STICKY
    }

    private fun startForegroundNow() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("photo", "Security check", NotificationManager.IMPORTANCE_LOW))
        val n: Notification = NotificationCompat.Builder(this, "photo")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentTitle("Security check")
            .build()
        startForeground(2, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
    }

    private fun takePhoto(reason: String) {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val capture = ImageCapture.Builder().build()
            val selector = when {
                provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) -> CameraSelector.DEFAULT_FRONT_CAMERA
                else -> CameraSelector.DEFAULT_BACK_CAMERA
            }
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, capture)
            } catch (e: Exception) { finish(); return@addListener }

            val file = File(cacheDir, "thief_${System.currentTimeMillis()}.jpg")
            capture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(this),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) { upload(file, reason) }
                    override fun onError(exc: ImageCaptureException) { finish() }
                },
            )
        }, ContextCompat.getMainExecutor(this))
    }

    private fun upload(file: File, reason: String) {
        val repo = DeviceRepository(this)
        val path = "thieves/${repo.deviceId}/${file.name}"
        Firebase.storage.reference.child(path).putFile(android.net.Uri.fromFile(file))
            .addOnCompleteListener {
                CoroutineScope(Dispatchers.IO).launch {
                    runCatching { repo.logEvent(reason, mapOf("photoPath" to path)) }
                    file.delete()
                    finish()
                }
            }
    }

    private fun finish() { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }

    companion object {
        private const val EXTRA_REASON = "reason"
        fun capture(context: Context, reason: String) {
            ContextCompat.startForegroundService(
                context, Intent(context, ThiefPhotoService::class.java).putExtra(EXTRA_REASON, reason)
            )
        }
    }
}
