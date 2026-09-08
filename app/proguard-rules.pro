# Proguard / R8 configuration for DriveStream

# Google Drive API models
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.** { *; }

# Room entities
-keep class com.drivestream.app.data.**Entity { *; }

# Kotlin serialization & Navigation Compose Type-Safe Routes
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keepclassmembers class * implements kotlinx.serialization.KSerializer {
    <fields>;
    <methods>;
}
-keepclassmembers class * {
    *** Companion;
}
-keep class com.drivestream.app.ui.navigation.** { *; }
-keepclassmembers class com.drivestream.app.ui.navigation.** { *; }
-keepnames class com.drivestream.app.ui.navigation.** { *; }

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }
-keepclassmembers class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Prevent stripping of line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Apache HTTP client transitive dependencies in Google API Client
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**

