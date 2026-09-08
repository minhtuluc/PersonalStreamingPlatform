# Proguard / R8 configuration for DriveStream

# Google Drive API models
-keep class com.google.api.services.drive.model.** { *; }
-keep class com.google.api.client.** { *; }

# Room entities
-keep class com.drivestream.app.data.**Entity { *; }

# Kotlin serialization
-keepattributes *Annotation*
-keep class kotlinx.serialization.** { *; }

# ExoPlayer / Media3
-keep class androidx.media3.** { *; }

# Prevent stripping of line numbers for crash reporting
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Apache HTTP client transitive dependencies in Google API Client
-dontwarn javax.naming.**
-dontwarn org.ietf.jgss.**
