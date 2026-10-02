-keep class com.jcraft.jsch.** { *; }
-keep class com.github.mwiede.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn com.github.mwiede.jsch.**

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.jump.lite.model.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
