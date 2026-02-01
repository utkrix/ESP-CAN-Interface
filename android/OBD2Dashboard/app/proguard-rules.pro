# Add project specific ProGuard rules here.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.esp.obd2dashboard.**$$serializer { *; }
-keepclassmembers class com.esp.obd2dashboard.** {
    *** Companion;
}
-keepclasseswithmembers class com.esp.obd2dashboard.** {
    kotlinx.serialization.KSerializer serializer(...);
}
