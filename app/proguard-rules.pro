# Navigation 3 keys are serialized with kotlinx.serialization to survive process death.
-keepclassmembers @kotlinx.serialization.Serializable class com.blaze.photobooth.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.blaze.photobooth.**$$serializer { *; }
