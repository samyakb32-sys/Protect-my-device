# Protect My Device

Android anti-theft app (Kotlin + Firebase). Tracks, locks, sounds an alarm on, and (if needed) wipes **your own** phone **without needing a SIM or IMEI** — it only needs internet (WiFi or mobile data) and has to be set up *before* the phone is lost.

## Features
- **Live location** (GPS + WiFi) via a foreground service, restarted after reboot. A fix every 2.5 hours day to day, every 10 minutes in stolen mode, every 15 seconds for two minutes after "locate".
- **Remote commands** from a second phone or the web dashboard: Locate, Alarm, Lock, Lost-mode message, Take photo, Stolen mode, Erase.
- **Stolen mode**: one button that locks the phone, puts up the lost screen, takes a front-camera photo and reports location every 10 minutes. It survives reboots and stays on until you mark the phone safe.
- **Thief detection**: SIM swap → alert + location + front-camera photo; wrong unlock password → photo.
- **Last-known position**: when the phone is switched off, the battery runs low or airplane mode goes on, it records where it was. If it can't send that right away, it goes out the next time the phone is online.
- **Block power-off (experimental)**: an accessibility service that, while the phone is locked, closes the power menu and the Quick Settings panel (its power button and airplane-mode tile). See the limits below.
- **Accounts**: email + password, in the app and on the website. Create an account, sign in, reset a forgotten password; one account covers all your phones.
- **Web dashboard** (`web/`): sign in from any browser to see every phone on the account, its battery, map, location history, alerts and thief photos, and send it commands.
- **Offline finding (BLE)**: rotating-ID beacon + scanner (`ble/`). Coverage depends on nearby app users.
- **Battery report**: a "battery use" screen (home → extra armour → battery use) showing how much battery *this app* used today and on each of the last 7 days, and which part of the phone it went on. See [Battery report](#battery-report).

### What it can't do
- **Find a phone that is switched off.** No third-party app can transmit from a powered-down phone. Only Google's Find Hub network can, and only on phones built for it (Pixel 8 and later, and some others). Turn it on in Settings and set it to "With network in all areas"; the app's "extra armour" card links there.
- **Stop a forced shutdown.** The power-off blocker only closes the on-screen power menu and Quick Settings. Holding the hardware keys for a forced restart still works, and so does waiting for the battery to die. It also depends on internal system class and view names that differ between manufacturers, so test it on your own phone. Sideloaded apps on Android 13+ need "Allow restricted settings" (App info > ⋮) before Accessibility will let you enable it.
- **Report without internet.** Location only reaches you over WiFi or mobile data.

## Battery report
The battery screen answers "how much does this app cost me, and on what?". It shows:
- **Today's total** in mAh, and as a share of a full battery when the phone reports its battery size.
- **Where it went**: location (GPS), processor, mobile data, wifi, bluetooth, camera, keeping the phone awake (wakelocks), and sound/alarm. Each part shows how long it was in use, plus its mAh.
- **What the app did**: location fixes (GPS-grade vs. balanced), minutes in stolen mode, quick-locate bursts, photos taken, and seconds of alarm.
- **The last 7 days**, one bar per day. Each day also shows how far the whole phone's battery fell, for comparison. That number covers every app and the screen, so it is never presented as this app's number.

**Measured vs. estimated.** Each number is labelled with where it comes from:
- *Measured*: the phone's own power counters reported the energy (`MEASUREMENT_*_POWER_MAMS`). Android splits these per app only for mobile data, wifi and Bluetooth, and many phones report 0.
- *Estimated (~)*: everything else. It's the time a part was in use multiplied by a typical current for a mid-range phone (`BatteryReport.kt`). A real phone can draw half or double that. Use these numbers to see which part costs most, not as exact figures.

**How it's collected** (`app/…/battery/`). Android's public `SystemHealthManager.takeMyUidSnapshot()` returns this app's own counters: CPU time, GPS time, camera, audio, wakelocks, radio time and bytes. The app reads it when it is already awake for another reason: a location check-in, a stolen-mode or "all clear" command, the phone shutting down, or the battery screen opening. It takes at most one reading per 20 minutes. That means no new alarms, no wakelocks and no extra location requests. Each reading is stored on the phone as day-by-day differences:
- When a counter goes backwards, Android has reset the counters (a reboot, or coming off the charger after a full charge). The new value is then counted as fresh use.
- Use that spans midnight is split between the two days by time.
- A day's totals are uploaded once to `devices/{id}/battery/{yyyy-mm-dd}` the day after, so the web dashboard can show the week. Only the owner can read them, and they can't be changed afterwards (`firestore.rules`).

**Limits.**
- The system's own per-app battery screen (BatteryStats) and `PowerProfile` are closed to normal apps, so neither is used.
- `MEASUREMENT_CPU_POWER_MAMS` has been deprecated since Android 8 ("not reliable"), so processor use is always an estimate.
- Location fixes made from wifi and cell towers are computed by Google Play services. Android may count that power under Play services rather than this app, so the location line can read low.
- Not counted: the screen, the BLE beacon's advertising, and any use between the last reading and a crash or forced restart. A normal shutdown takes a reading first.
- The whole-phone drop is sampled at readings, so charging in between hides some of it (shown as "at least").
- The first reading after install only sets a starting point. Numbers appear after the next check-in, within 2.5 hours.

**Exact numbers for developers.** `scripts/battery-report.sh` asks a phone connected over USB (adb) for Android's own estimate, `dumpsys batterystats --charged`, and prints this app's mAh per part since the last full charge. Options:
- `--reset`: zero the stats before a test run.
- `--bugreport`: save a file for [Battery Historian](https://github.com/google/battery-historian).
- `--raw`: print Android's line for the app.

## Setup
Full step-by-step instructions, including the signed release build, App Check and the Play Store, are in **[DEPLOY.md](DEPLOY.md)**. The short version for trying it out:
1. Create a Firebase project; add an Android app `com.protectmydevice`; download `google-services.json` into `app/` (git-ignored).
2. Enable Auth (Email/Password), Firestore, Storage, Cloud Messaging.
3. `firebase deploy --only firestore:rules,storage,functions,hosting`
4. `./gradlew assembleDebug`, install on the phone, then follow the 3 steps on the home screen.

## Security
What a reverse-engineering tool can pull out of the app and the website, and what protects your data anyway, is written up in **[docs/SECURITY.md](docs/SECURITY.md)**. In short: nothing in the client is secret or trusted, access is decided by server-side rules, release builds are obfuscated, and App Check can lock the backend to the genuine app.

## Important notes
- Write down your IMEI (`*#06#`) and file a police complaint + block it via India's CEIR / Sanchar Saathi portal.
- Also keep Google *Find Hub* (Find My Device) / Samsung Find enabled — they work with no SIM.
- Remote wipe is destructive; the UI asks for confirmation in a separate "no undo" dialog.

## Web dashboard
A static site in `web/` with the same look as the app. Sign in with the same account as the app.

| Dashboard | Phone | Sign in |
|---|---|---|
| <img src="docs/screenshots/web_dashboard.png" width="330"> | <img src="docs/screenshots/web_phone.png" width="150"> | <img src="docs/screenshots/web_login.png" width="190"> |

1. In the Firebase console add a **Web app** to the project and paste its config into `web/firebase-config.js`.
2. `firebase deploy --only hosting` — the site is then at `https://<project-id>.web.app`.
3. New here? Press "i'm new — make an account" on the sign-in page, or use the account you made in the app.
4. To look around without Firebase, serve `web/` locally and open it with `?demo` for made-up sample data: `python3 -m http.server -d web 8000`, then `http://localhost:8000/?demo`.

Only the account that owns a device can read its data or photos (`firestore.rules`, `storage.rules`). The map uses OpenStreetMap tiles.

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
- **App**: `./gradlew :app:testDebugUnitTest :app:lintDebug`. This includes `BatteryLedgerTest`, which covers counter resets, reboots, missing or zero energy fields, splitting at midnight, the 7-day window and storage.
- **Backend and website** (`tests/`, needs Node 20+ and Java): `cd tests && npm ci && npm test`. This starts the Firebase emulators and runs:
  - `rules.test.js` — who can and cannot read devices, history, alerts and photos, or send commands, and that a daily battery summary is owner-only, well-formed and can't be rewritten;
  - `dashboard.test.js` — the real website in a real browser: sign-up, sign-in, password reset, a phone appearing live, every action button, stolen mode, erase confirmation, sign-out;
  - `csp.test.js` — the site still works under its production security headers;
  - `functions.test.js` — Cloud Function logic, and that the app, rules, function and website agree on the command list.
- CI (`.github/workflows/ci.yml`) runs all of the above on every pull request.

Not covered by automation: anything that needs a physical phone (push delivery, camera, device admin, the accessibility service, and the real values `takeMyUidSnapshot()` returns on a given phone). See the checklist at the end of `DEPLOY.md`.
