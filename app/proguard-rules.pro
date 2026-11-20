# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.xiaogou.serverseyes.**$$serializer { *; }
-keepclassmembers class com.xiaogou.serverseyes.** {
    *** Companion;
}
-keepclasseswithmembers class com.xiaogou.serverseyes.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# JSch (SSH)
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.ietf.jgss.**
-dontwarn java.beans.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
