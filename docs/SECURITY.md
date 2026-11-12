# Security: what someone can learn by taking this app apart

Tools such as [REA](https://github.com/morluto/rea) ("Reverse Engineer Anything") make it routine to open up a published app. Pointed at an Android app or a website, that class of tool can:

| What it does | What it would get from this project |
|---|---|
| Decompile the APK's code back to readable classes and methods (JADX), and apply a leaked `mapping.txt` to undo obfuscation | How the app works: which commands exist, how tracking is scheduled, how the SIM check works |
| Decode the manifest and resources (Apktool) | The permission list, component names, and the Firebase project identifiers from `google-services.json` |
| Read the signing certificate and check whether an APK was re-signed | Whether a copy is the original or a repack |
| Drive a phone over `adb`: list packages, pull the APK, read logs, dump state | Anything the app writes to logcat; the app's own files only on a rooted or debuggable device |
| Open the website in a real browser and record its scripts, routes and network calls | The dashboard's full source (it is plain JavaScript) and the shape of every Firestore request |

**None of that can be prevented.** Anything shipped to a phone or a browser can be read by its owner. Obfuscation slows a reader down; it does not stop one. So the design rule here is:

> Nothing in the app or the website is a secret, and nothing they do is trusted. Every decision about who may read or do what is made on the server.

## What actually protects your data

1. **Server-side rules** (`firestore.rules`, `storage.rules`). A device, its location history, its alerts and its photos can be read only by the account that owns it. Only that account can send it a command, only from a fixed list, and commands, history and alerts cannot be edited or deleted afterwards. Someone who has read every line of the app and knows the project's keys still gets nothing without the owner's password. `tests/rules.test.js` checks each of these against the emulator.
2. **No secrets in the client.** The Firebase values in the APK and in `web/firebase-config.js` identify the project; they grant nothing by themselves. There are no API secrets, admin keys or passwords in the code. The one privileged credential (sending push messages) lives only inside Cloud Functions.
3. **App Check** (`ProtectApp.kt`). With enforcement on, Firebase refuses requests that do not come from the genuine, unmodified app as installed from Google Play, or from the registered website. A repackaged APK or a script replaying the app's requests is turned away even with a valid login. Setup is in `DEPLOY.md`; it is off until you enforce it in the console.
4. **Commands are validated three times**: by the rules when written, by the Cloud Function before forwarding, and by the phone when received (`Command.parse` ignores anything unknown).
5. **Account protection.** Sign-in is by Firebase Authentication. Password reset gives the same answer whether or not an address has an account, so it cannot be used to discover who uses the app.

## What slows a reader down

- Release builds are shrunk and obfuscated with R8 (`app/proguard-rules.pro`): the app's own classes are renamed to single letters and flattened into one package, and log calls are stripped.
- `mapping.txt` reverses that obfuscation. Never publish or commit it.
- Release builds are not debuggable, do not allow backup extraction (`allowBackup="false"`), and refuse unencrypted traffic.
- The website is served with a strict Content-Security-Policy, cannot be framed by another site, and loads its one third-party script with an integrity hash.

## What a teardown of the release APK actually shows

Checked by unpacking a release build and reading it the way a decompiler would:

- Of about 6,700 classes, only 9 keep a readable name, and those are the ones Android itself has to find by name (the two screens, the services and the receivers). Everything else in the app is renamed.
- No logging calls remain.
- No password, private key or server credential is in the file. The Firebase project identifiers are, as explained above.
- The app is not debuggable, backup is off, plain HTTP is refused, and every component other apps can reach is either guarded by a system permission or only restarts protection.
- Text shown on screen is readable, as it is in every app.

## What this does not defend against

- **Someone who knows your account password.** They are you, as far as the server can tell. Use a password you use nowhere else.
- **A thief with the unlocked phone** can open the app and sign out, or remove device admin and uninstall it. Keep a screen lock on; the app only helps while the phone is locked.
- **A factory reset from recovery mode** removes the app. Android's own Factory Reset Protection (a Google account on the phone) is what makes a wiped phone hard to reuse.
- **A rooted or modified phone** can make the app report anything, and can read the app's local files.
- **Copying the idea.** The code is readable; how the features work is not protected and is not meant to be.

## Reporting a problem
Open a private security advisory on the GitHub repository rather than a public issue.
