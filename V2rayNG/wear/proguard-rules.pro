# JNI - Keep all native methods and their classes
-keepclasseswithmembers class * {
    native <methods>;
}

# Keep the legacy service package and all its methods for JNI linkage
-keep class com.v2ray.ang.service.** {
    <methods>;
    <fields>;
}

# Keep the TProxyService specifically
-keep class com.v2ray.ang.service.TProxyService {
    native <methods>;
    public static native void TProxyStartService(java.lang.String, int);
    public static native void TProxyStopService();
    public static native long[] TProxyGetStats();
}

# Keep the current service package
-keep class com.v2ray.angm.service.** {
    <methods>;
    <fields>;
}

# MMKV
-keep class com.tencent.mmkv.** { *; }

# Preserve line numbers for debugging
-keepattributes SourceFile,LineNumberTable
