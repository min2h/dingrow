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

# Play Billing / Credential Manager / Google ID (consumer rules ship with the libs; keep the entry points we touch reflectively-safe).
-keep class com.android.billingclient.api.** { *; }
-keep class androidx.credentials.playservices.** { *; }
-keep class com.google.android.libraries.identity.googleid.** { *; }
-dontwarn com.google.android.libraries.identity.googleid.**

# App entry points referenced from the manifest / by name.
-keep class app.mongle.alarm.** extends android.content.BroadcastReceiver { *; }
-keep class app.mongle.wallpaper.** extends android.service.wallpaper.WallpaperService { *; }
-keep class app.mongle.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

# Backup JSON (kotlinx.serialization) models.
-keep @kotlinx.serialization.Serializable class app.mongle.model.** { *; }
