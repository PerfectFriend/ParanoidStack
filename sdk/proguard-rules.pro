# PX SDK ProGuard Rules
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.paranoidx.sdk.** { *; }
-dontwarn javax.crypto.**
-dontwarn java.security.**
