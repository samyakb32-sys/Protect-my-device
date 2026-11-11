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
- Remote wipe is destructive; the UI asks for confirmation in a separate "no undo" dialog.

## Design
Loud on purpose: flat acid colours, 2dp ink outlines, hard offset shadows that buttons physically drop onto when pressed, lowercase copy. No gradients, no blur.

| Sign in | Home | Setup in progress | Dark | Lost mode |
|---|---|---|---|---|
| <img src="docs/screenshots/login.png" width="170"> | <img src="docs/screenshots/home_done.png" width="170"> | <img src="docs/screenshots/home_setup.png" width="170"> | <img src="docs/screenshots/home_dark.png" width="170"> | <img src="docs/screenshots/lost.png" width="170"> |

- **Colour** (`ui/theme/Color.kt`): Ink `#111111`, Paper `#F3EFE6`, Lime `#C8FF2E` (protected / primary action), Hazard `#FFD23F` (needs attention, lost mode), Lilac `#C3B3FF` (info), Coral `#FF5A40` (destructive). Coloured blocks always carry ink text, in light and dark.
- **Type** (`ui/theme/Type.kt`): Bricolage Grotesque for everything you read, DM Mono for tags and numbers. Both are bundled in `res/font` under the SIL Open Font License (`licenses/`).
- **Building blocks** (`ui/components/Brutal.kt`): `BrutalBox`, `BrutalButton`, `BrutalPressable`, `Sticker`, `BrandMark`. Icons are 24dp stroke vectors in `res/drawable`.
- **Screens** (`ui/screens/`) are stateless; `MainActivity` wires them to Firebase and permissions, and `ui/Previews.kt` renders them in Android Studio with sample data.
- The lost-mode screen is deliberately not playful: it is hazard yellow in every theme and addressed to whoever is holding the phone.

## Tests
`./gradlew test`
