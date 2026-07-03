# === Disable R8 optimization (breaks Moshi generic type resolution) ===
-dontoptimize

# === Moshi JSON serialization ===
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# Keep ALL model data classes and their members
-keep class com.skypulse.weather.model.** { *; }

# Keep ALL Moshi classes (required for codegen adapter discovery via @JsonClass annotation)
-keep class com.squareup.moshi.** { *; }
-dontwarn com.squareup.moshi.**

# === Kotlin ===
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.jvm.internal.** { *; }
-dontwarn kotlin.reflect.jvm.internal.**
-dontwarn kotlin.**

# === Retrofit ===
-keepattributes Exceptions
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
# Keep API interface with ALL members and signatures (R8 strips Signature from interfaces)
-keep,allowobfuscation class com.skypulse.weather.data.remote.** { *; }
-keepclassmembers,allowobfuscation class com.skypulse.weather.data.remote.** { *; }
-dontwarn retrofit2.**

# === OkHttp ===
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# === Accompanist ===
-keep class com.google.accompanist.** { *; }
-dontwarn com.google.accompanist.**

# === Google Play Services ===
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# === Kotlin Coroutines ===
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**

# === AndroidX ===
-keep class androidx.lifecycle.** { *; }
-keep class androidx.activity.** { *; }
-keep class androidx.start.** { *; }
-dontwarn androidx.lifecycle.**
-dontwarn androidx.activity.**

# === Amap (高德) SDK ===
-keep class com.amap.api.** { *; }
-keep class com.amap.api.services.** { *; }
-dontwarn com.amap.api.**


# === Keep Log calls for debugging ===
-keep class android.util.Log { *; }

# === Hilt / Dagger ===
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.inject.**
