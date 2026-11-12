# Deploying Protect My Device

Everything below is done once. You need a Google account, Node.js 20+, JDK 17 and (for the Play Store) a Play Console account.

## 1. Firebase project
1. Create a project at <https://console.firebase.google.com>. Cloud Functions need the **Blaze** plan (pay-as-you-go; this app stays inside the free allowances at personal scale).
2. **Authentication** → Sign-in method → enable **Email/Password**. Under Settings → User actions, keep **email enumeration protection** on.
3. **Firestore** → create a database in production mode. **Storage** → get started.
4. Add an **Android app** with package `com.protectmydevice`; download `google-services.json` into `app/`.
5. Add a **Web app**; paste its config into `web/firebase-config.js`.

## 2. Backend and website
```bash
npm install -g firebase-tools
```
```bash
firebase login
```
```bash
firebase use --add
```
```bash
(cd functions && npm ci)
```
```bash
firebase deploy --only firestore:rules,storage,functions,hosting
```
The dashboard is then live at `https://<project-id>.web.app`.

## 3. Release build of the app
1. Create a signing key (keep the file and passwords somewhere safe and backed up; losing them means you can never update the app):
   ```bash
   keytool -genkeypair -v -keystore release.jks -alias protect -keyalg RSA -keysize 4096 -validity 10000
   ```
2. Copy `keystore.properties.example` to `keystore.properties` and fill it in. Both files are git-ignored.
3. Build:
   ```bash
   ./gradlew :app:assembleRelease
   ```
   The signed APK is `app/build/outputs/apk/release/app-release.apk`. For the Play Store build an app bundle instead: `./gradlew :app:bundleRelease`.
4. Keep `app/build/outputs/mapping/release/mapping.txt` for each release. It is the only way to read crash reports from an obfuscated build, and it must stay private.

## 4. Lock the backend to your real app (App Check)
This is what stops a modified copy of the app, or a script, from using your backend.
1. Firebase console → **App Check** → register the Android app with **Play Integrity** and add the SHA-256 of your signing certificate (`keytool -list -v -keystore release.jks`).
2. Optional, for the website: register it with **reCAPTCHA Enterprise** and put the site key in `web/firebase-config.js` as `appCheckSiteKey`.
3. Watch the App Check metrics for a few days, then press **Enforce** for Firestore, Storage and Authentication.

Play Integrity only vouches for apps installed from Google Play. If you hand out the APK directly, leave enforcement off for Android or those installs will be refused.

## 5. Publishing on Google Play
This app asks for permissions Google reviews by hand. Expect to fill in declarations for:
- **Background location** — a short video showing the feature, and the in-app disclosure.
- **Device admin** and **Accessibility** — a declaration of the anti-theft use. The accessibility service is the most likely reason for rejection; if it is refused, remove `PowerMenuGuard` from the manifest and resubmit.
- **Privacy policy URL** — use `https://<project-id>.web.app/privacy.html` and edit `web/privacy.html` to name you as the operator and give a contact address.
- **Data safety form** — location, photos, email address and device identifiers are collected; none are shared.

## 6. Before you rely on it
Set it up on a spare phone and, from the website, try each of: locate, alarm, lock, lost mode, take photo, stolen mode, all clear. Then switch the phone off and on again and confirm it reappears. The automated tests cover the backend and the website; they do not cover a real phone.
