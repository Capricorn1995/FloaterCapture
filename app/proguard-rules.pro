# FloaterCapture ProGuard Rules

# ==========================================
# General Android Rules
# ==========================================
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes SourceFile,LineNumberTable

# ==========================================
# Keep data classes used for serialization
# ==========================================
-keepclassmembers class com.floatercapture.model.** {
    <fields>;
    <init>(...);
}

# ==========================================
# AndroidX & Material Components
# ==========================================
-keep class androidx.** { *; }
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**
-keepclassmembers class com.google.android.material.** { *; }

# ==========================================
# Accessibility Service
# ==========================================
-keep class com.floatercapture.service.** { *; }
-keepclassmembers class com.floatercapture.service.AccessibilityCaptureService {
    public *;
    protected *;
}

# ==========================================
# Floating Window Service
# ==========================================
-keep class com.floatercapture.service.FloatingWindowService {
    public *;
    protected *;
}

# ==========================================
# Coroutines
# ==========================================
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ==========================================
# OkHttp / Network
# ==========================================
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# ==========================================
# Gson (if used for JSON)
# ==========================================
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * extends com.google.gson.TypeAdapter
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ==========================================
# Glide / Image Loading (if used)
# ==========================================
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ==========================================
# ViewBinding
# ==========================================
-keepclassmembers class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(android.view.LayoutInflater);
    public static * bind(android.view.View);
}

# ==========================================
# R8 specific
# ==========================================
# Keep the resource IDs
-keepclassmembers class **.R$* {
    public static <fields>;
}

# Remove logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
}
