# Keep core app classes
-keep class com.example.** { *; }
-keep class com.example.data.** { *; }
-keep class com.example.security.** { *; }
-keep class com.example.service.** { *; }
-keep class com.example.protocols.** { *; }
-keep class com.example.protocols.storage.** { *; }
-keep class com.example.protocols.mesh.** { *; }

# Keep Room entities and DAOs
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keepclassmembers class * {
    @androidx.room.* <fields>;
    @androidx.room.* <methods>;
}

# Keep crypto classes
-keep class com.example.data.NaClCrypto { *; }
-keep class com.example.data.SimpleXCrypto { *; }
-keep class com.example.data.DoubleRatchet { *; }

# Keep Moshi
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.Json <fields>;
}
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}
-keep @com.squareup.moshi.JsonClass class *
-keepclassmembers @com.squareup.moshi.JsonClass class * { *; }

# Keep Gson/JSON
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken

# Keep Compose navigation
-keep class * implements androidx.navigation.NavType { *; }

# Keep WebDav
-keep class com.example.data.WebDavBackup { *; }

# Keep OkHttp
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-dontwarn okhttp3.**
-keep class com.squareup.okhttp.** { *; }

# Keep Retrofit
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keep class kotlinx.coroutines.** { *; }

# Keep Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }

# Keep navigation
-keep class androidx.navigation.** { *; }

# Optimization
-optimizationpasses 7
-allowaccessmodification
-repackageclasses 'com.example.a'
-mergeinterfacesaggressively
-overloadaggressively
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Output
-printmapping mapping.txt
-printusage unused.txt
-printseeds seeds.txt

# Remove logging in release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Remove Kotlin null checks in release
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    static void checkNotNullParameter(java.lang.Object, java.lang.String);
    static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    static void checkNotNull(java.lang.Object);
    static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
}

# Shrink resources
-android
-dontusemixedcaseclassnames
-verbose

# Keep annotations
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes Exceptions

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }
-keepclasseswithmembers class * {
    @kotlin.Metadata <fields>;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep Parcelable implementations
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator CREATOR;
}

# Keep Serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep custom app classes
-keep class com.example.MyApplication { *; }

# Keep Tor
-keep class org.torproject.** { *; }

# Keep Firebase
-keep class com.google.firebase.** { *; }

# Keep WorkManager
-keep class androidx.work.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }

# Keep MLKit
-keep class com.google.mlkit.** { *; }

# Keep ZXing
-keep class com.google.zxing.** { *; }

# Keep ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Net/JNIBack
-keepclassmembers class * {
    native <methods>;
}

# Dontwarn for known warnings
-dontwarn com.sun.**
-dontwarn javax.xml.**
-dontwarn javax.naming.**
-dontwarn com.ibm.**
-dontwarn org.w3c.dom.**
-dontwarn org.apache.**
-dontwarn org.bouncycastle.**
-dontwarn com.android.**
-dontwarn dalvik.**
-dontwarn com.google.errorprone.**
-dontwarn org.codehaus.mojo.**
-dontwarn org.jetbrains.annotations.**
-dontwarn javax.lang.**
-dontwarn java.lang.management.**
