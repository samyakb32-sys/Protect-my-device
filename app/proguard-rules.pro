# Release builds are shrunk and obfuscated by R8. Firebase, CameraX and Compose ship their own
# keep rules, so nothing of ours needs keeping by name.

# Flatten our own packages so the decompiled layout says nothing about how the app is organised.
-repackageclasses ''
-allowaccessmodification

# No log output in release: logs are readable over adb and are the first thing an analyst looks at.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}

# Keep line numbers so crash reports can be de-obfuscated with mapping.txt, but hide file names.
-keepattributes LineNumberTable
-renamesourcefileattribute x
