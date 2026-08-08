# The WebView calls these by name from JavaScript, so R8 must not rename them.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Keep the enum names DataStore and the UI switch on.
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# OkHttp and Okio ship their own rules; these silence optional platform hooks.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# WorkManager stores the worker's class name in its database and instantiates it
# reflectively, so renaming or stripping it would silently break the scheduled
# update check after an upgrade.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
