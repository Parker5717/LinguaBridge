# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.linguabridge.app.**$$serializer { *; }
-keepclassmembers class com.linguabridge.app.** { *** Companion; }
-keepclasseswithmembers class com.linguabridge.app.** { kotlinx.serialization.KSerializer serializer(...); }
