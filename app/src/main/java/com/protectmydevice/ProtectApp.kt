package com.protectmydevice

import android.app.Application
import com.google.firebase.appcheck.FirebaseAppCheck

class ProtectApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // App Check attaches proof that a request comes from this genuine, unmodified app on a real
        // device. Once enforcement is switched on in the Firebase console, a repackaged or scripted
        // client is refused by Firestore, Storage and Functions even if it knows the project's keys.
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProviderFactory())
    }
}
