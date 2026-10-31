-keep class com.ks.attendance.data.** { *; }
-keep class com.ks.attendance.domain.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.**

# Retrofit & OkHttp
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# DataStore
-dontwarn androidx.datastore.**

