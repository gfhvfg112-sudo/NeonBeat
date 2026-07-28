-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable
-dontwarn org.jetbrains.annotations.**

# Room / Hilt generated code is already handled by their consumer rules.
# Keep kotlinx.serialization metadata for backup & restore payloads.
-keepclassmembers class ** {
    @kotlinx.serialization.SerialName <fields>;
}
-keep,includedescriptorclasses class com.neonbeat.**$$serializer { *; }
-keepclassmembers class com.neonbeat.** {
    *** Companion;
}
