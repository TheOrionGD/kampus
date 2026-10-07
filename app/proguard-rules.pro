# MongoDB Java Driver Keep Rules
-keep class com.mongodb.** { *; }
-keep class org.bson.** { *; }
-dontwarn com.mongodb.**
-dontwarn org.bson.**
-dontwarn javax.security.**
-dontwarn javax.naming.**
-dontwarn java.lang.management.**

# Retrofit / Gson / Coil
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.example.kampus.models.** { *; }
