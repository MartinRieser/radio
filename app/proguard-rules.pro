# Proguard rules for PulseCast
-keep class com.pulsecast.radio.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
