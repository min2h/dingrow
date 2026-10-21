# Glance action callbacks are looked up by class name.
-keep class * extends androidx.glance.appwidget.action.ActionCallback { *; }

# WorkManager instantiates this by name.
-keep class app.mongle.worker.PetTickWorker { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keep,includedescriptorclasses class app.mongle.**$$serializer { *; }
-keepclassmembers class app.mongle.** {
    *** Companion;
}
-keepclasseswithmembers class app.mongle.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
