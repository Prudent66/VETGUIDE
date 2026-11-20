# VETGUIDE release shrinking rules (R8 + isShrinkResources = true).
#
# Most libraries ship their own consumer ProGuard rules; the entries below are
# the defensive keep set for the pieces that are discovered reflectively at
# runtime (Hilt generated components, Room entities/DAOs, Ktor engines and
# kotlinx.serialization generated serializers). Without them a release build
# can install and then crash on first use — debug builds never exercise this.

# ---------------------------------------------------------------------------
# Generic metadata R8 needs for reflection and generated code
# ---------------------------------------------------------------------------
-keepattributes Signature,InnerClasses,EnclosingMethod
-keepattributes RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations
-keepattributes AnnotationDefault

# ---------------------------------------------------------------------------
# Hilt / Dagger (components and entry points are generated + reflectively loaded)
# ---------------------------------------------------------------------------
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { <init>(...); }
-keep @dagger.hilt.android.AndroidEntryPoint class * { <init>(...); }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { <init>(...); }
-keepclassmembers class com.prudent66.vetguide.VetguideApplication { *; }

# ---------------------------------------------------------------------------
# Room (database classes are looked up by name, entities by reflection)
# ---------------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }

# ---------------------------------------------------------------------------
# Ktor client + OkHttp (engine/platform detection is reflective)
# ---------------------------------------------------------------------------
-keep class io.ktor.client.** { *; }
-keep class io.ktor.http.** { *; }
-keep class io.ktor.utils.io.** { *; }
-keep class okhttp3.internal.platform.** { *; }
-keepclassmembers class okhttp3.internal.platform.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ---------------------------------------------------------------------------
# kotlinx.serialization (AiAnalysisData / KB seed models decode via generated
# serializers looked up from the companion object)
# ---------------------------------------------------------------------------
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.prudent66.vetguide.**$$serializer { *; }
-keepclassmembers class com.prudent66.vetguide.** {
    *** Companion;
}
-keepclasseswithmembers class com.prudent66.vetguide.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**
