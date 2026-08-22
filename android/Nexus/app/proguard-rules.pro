# NexusChat ProGuard rules

# ── KEEP JS BRIDGE CLASSES (WebView JavascriptInterface) ──────
-keep class com.nexuschat.app.bridges.** { *; }
-keepclassmembers class com.nexuschat.app.bridges.** {
    @android.webkit.JavascriptInterface <methods>;
}

# ── SERVICES ─────────────────────────────────────────────────
-keep class com.nexuschat.app.services.** { *; }
-keep class com.nexuschat.app.receivers.** { *; }

# ── TOR ANDROID ──────────────────────────────────────────────
-keep class info.guardianproject.** { *; }
-keep class net.freehaven.tor.** { *; }
-dontwarn info.guardianproject.**
-dontwarn net.freehaven.tor.**

# ── NETCIPHER ────────────────────────────────────────────────
-keep class info.guardianproject.netcipher.** { *; }
-dontwarn info.guardianproject.netcipher.**

# ── OKHTTP ───────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ── RETROFIT ─────────────────────────────────────────────────
-keepattributes Signature, Exceptions, *Annotation*
-keep class retrofit2.** { *; }
-dontwarn retrofit2.**
-keep class com.nexuschat.app.bridges.Ts** { *; }

# ── GSON ─────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ── TINK ─────────────────────────────────────────────────────
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# ── CONSCRYPT ────────────────────────────────────────────────
-keep class org.conscrypt.** { *; }
-dontwarn org.conscrypt.**

# ── BOUNCY CASTLE ────────────────────────────────────────────
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# ── WEBRTC ───────────────────────────────────────────────────
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**

# ── COROUTINES ───────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── KOTLIN ───────────────────────────────────────────────────
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings { <fields>; }

# ── GENERAL ANDROID ──────────────────────────────────────────
-keepattributes SourceFile, LineNumberTable
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application

# ── KEEP MODEL CLASSES FOR GSON ──────────────────────────────
-keep class com.nexuschat.app.crypto.** { *; }
-keep class com.nexuschat.app.model.** { *; }

# ── REMOVE LOGGING IN RELEASE ────────────────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
-assumenosideeffects class timber.log.Timber {
    public static void v(...);
    public static void d(...);
    public static void i(...);
    public static void w(...);
}

# ── OPTIMIZATION ─────────────────────────────────────────────
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
-allowaccessmodification
