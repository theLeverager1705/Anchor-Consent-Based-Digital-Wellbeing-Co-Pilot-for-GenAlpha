# kotlinx.serialization keeps generated serializers via its bundled rules.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keep,includedescriptorclasses class com.anchor.copilot.**$$serializer { *; }
-keepclassmembers class com.anchor.copilot.** {
    *** Companion;
}
-keepclasseswithmembers class com.anchor.copilot.** {
    kotlinx.serialization.KSerializer serializer(...);
}
