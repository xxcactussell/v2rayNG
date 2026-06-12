# Keep DTOs and entities for GSON serialization
-keep class com.v2ray.angm.dto.** { *; }
-keep class com.v2ray.angm.dto.entities.** { *; }

# GSON general configuration
-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# JNI - Essential for native methods
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep the legacy and current service packages entirely to avoid JNI linkage issues
-keep class com.v2ray.ang.service.** { *; }
-keep class com.v2ray.angm.service.** { *; }

# MMKV
-keep class com.tencent.mmkv.** { *; }

# WorkManager
-keep class androidx.work.** { *; }
-keep class com.v2ray.angm.AngApplication { *; }

# UI Libs
-keep class es.dmoral.toasty.** { *; }

# Standard Android components
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.app.Application

# Preserve line numbers
-keepattributes SourceFile,LineNumberTable
