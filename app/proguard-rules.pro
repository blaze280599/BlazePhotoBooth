# R8 rules for the release build (README §15.5). The libraries ship their own (kotlinx.serialization, Room, CameraX,
# Coil, DataStore); M9 checked the release build on the phone, process death included, and needed nothing more.

# Navigation 3 keys are serialized with kotlinx.serialization to survive process death.
-keepclassmembers @kotlinx.serialization.Serializable class com.blaze.photobooth.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.blaze.photobooth.**$$serializer { *; }
