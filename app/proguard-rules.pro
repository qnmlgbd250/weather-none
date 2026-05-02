# Moshi - keep all model classes and their generic signatures
-keepattributes Signature
-keep class com.skypulse.weather.model.** { *; }
-keepclassmembers class com.skypulse.weather.model.** { *; }
-dontwarn com.skypulse.weather.model.**

# Moshi reflection adapter
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.reflect.jvm.internal.**

# Retrofit
-keepattributes *Annotation*
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
