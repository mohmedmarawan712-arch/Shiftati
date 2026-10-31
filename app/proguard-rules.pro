-keep class com.ks.attendance.data.** { *; }
-keep class com.ks.attendance.domain.** { *; }
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.google.gson.** { *; }
