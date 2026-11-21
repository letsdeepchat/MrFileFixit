# R8 full mode is on (gradle.properties). Library consumer rules cover Hilt, Compose, WorkManager,
# ML Kit and kotlinx.serialization. Only app-specific rules live here.

# kotlinx.serialization: keep generated serializers for navigation routes and the exam rules DTOs.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}

# pdfbox-android loads font/glyph resources and some classes reflectively.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-dontwarn com.gemalto.jp2.**
-dontwarn org.bouncycastle.**

# Strip all android.util.Log calls in release as a second line of defence (Timber has no tree in release).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# ML Kit (text recognition, selfie segmentation, document scanner). Its bundled native pipelines call back into
# Java by class and member name, and its components are found reflectively, so R8 full mode must not rename or
# strip them. The 1.0.5 mapping shows R8 renamed and repackaged visionkit pipeline classes (for example
# visionkit.pipeline.zba -> c8.a) that the native OCR and segmentation pipelines look up by name, which breaks
# text recognition and Remove background in the minified build only. Costs a few hundred KB.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-keep class com.google.android.libraries.vision.** { *; }
-keep class com.google.android.gms.vision.** { *; }
-keep class com.google.android.odml.** { *; }
-keep class com.google.research.xeno.** { *; }
-keep class com.google.mediapipe.** { *; }
-keep class com.google.android.gms.common.moduleinstall.** { *; }
-keep class com.google.android.gms.tasks.** { *; }
-keepclasseswithmembers class * { native <methods>; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.internal.mlkit_**
