# Proguard rules for PulseCast
-keep class com.pulsecast.radio.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# TensorFlow Lite keep rules for R8 optimization
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**
