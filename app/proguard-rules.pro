# gomobile bindings are called from native code by name.
-keep class go.** { *; }
-keep class libv2ray.** { *; }
-keep class * implements libv2ray.CoreCallbackHandler { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers @kotlinx.serialization.Serializable class dev.waveless.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
