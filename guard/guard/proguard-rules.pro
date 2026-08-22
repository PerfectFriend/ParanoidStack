# ProGuard rules for ParanoidGuard
-keep class com.paranoidx.guard.** { *; }
-keep interface com.paranoidx.guard.** { *; }
-keepclassmembers class com.paranoidx.guard.** { *; }

# Keep Parcelable implementations
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}