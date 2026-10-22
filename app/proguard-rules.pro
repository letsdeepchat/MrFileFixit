# R8 full mode is on (gradle.properties). Library consumer rules cover Hilt, Compose, WorkManager,
# ML Kit, Play services ads and kotlinx.serialization. Only app-specific rules live here.

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
