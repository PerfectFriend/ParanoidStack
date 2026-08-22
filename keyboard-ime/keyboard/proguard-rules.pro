# ProGuard rules for ParanoidMatrix IME
-keep class com.paranoidx.keyboard.** { *; }
-keep interface com.paranoidx.keyboard.** { *; }
-keepclassmembers class com.paranoidx.keyboard.** { *; }

# Keep Compose related
-keep class androidx.compose.** { *; }
-keep interface androidx.compose.** { *; }

# Keep IME service
-keep class android.inputmethodservice.** { *; }