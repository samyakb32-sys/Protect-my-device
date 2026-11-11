# Protect My Device

Android anti-theft app (Kotlin + Firebase). Tracks, locks, sounds an alarm on, and (if needed) wipes **your own** phone **without needing a SIM or IMEI** — it only needs internet (WiFi or mobile data) and has to be set up *before* the phone is lost.

## Features
- **Live location** (GPS + WiFi) via a foreground service, restarted after reboot, urgent push on low battery.
- **Remote commands** from a second phone (same app, "My devices"): Locate, Alarm, Lock, Lost-mode message, Wipe.
- **Thief detection**: SIM swap → alert + location + front-camera photo; wrong unlock password → photo.
- **Offline finding (BLE)**: rotating-ID beacon + scanner (`ble/`). Coverage depends on nearby app users.

## Setup
1. Create a Firebase project; add an Android app `com.protectmydevice`; download `google-services.json` into `app/` (git-ignored).
2. Enable Auth (Email/Password), Firestore, Storage, Cloud Messaging.
3. `firebase deploy --only firestore:rules,storage,functions` (see `firestore.rules`, `storage.rules`, `functions/`).
4. `./gradlew assembleDebug`, install on the phone, then follow the 3 steps on the home screen.

## Important notes
- Write down your IMEI (`*#06#`) and file a police complaint + block it via India's CEIR / Sanchar Saathi portal.
- Also keep Google *Find Hub* (Find My Device) / Samsung Find enabled — they work with no SIM.
- Remote wipe is destructive; the UI requires a second tap to confirm.

## Tests
`./gradlew test`
