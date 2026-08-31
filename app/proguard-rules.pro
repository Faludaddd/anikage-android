# Keep Kotlin metadata + Config class
-keep class com.anikage.app.Config { *; }
-keep class com.anikage.app.**$$serializer { *; }
-keepclassmembers class com.anikage.app.** {
    *** Companion;
    <fields>;
}
-keep @kotlinx.serialization.Serializable class ** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
    <fields>;
}
-keep,includedescriptorclasses class com.anikage.app.**$$serializer { *; }
-keepclassmembers class com.anikage.app.** {
    *** Companion;
    <fields>;
}

# Retrofit / OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keepattributes Signature
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeInvisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
-keepattributes RuntimeInvisibleParameterAnnotations
-keepattributes AnnotationDefault
-keep class kotlin.coroutines.Continuation
-keep class kotlin.coroutines.intrinsics.** { *; }

# Compose
-dontwarn androidx.compose.**

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Coil
-dontwarn coil.**

# Models — keep all fields so kotlinx.serialization works
-keep class com.anikage.app.core.data.model.** { *; }
