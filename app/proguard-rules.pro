# Face Unlock ProGuard Rules

# Keep ML Kit classes
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Keep CameraX classes  
-keep class androidx.camera.** { *; }

# Keep Compose classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep Kotlin Coroutines
-dontwarn kotlinx.coroutines.**

# Keep app classes
-keep class me.gustyxpower.faceunlock.** { *; }

# Keep view binding
-keep class * implements androidx.viewbinding.ViewBinding {
    public static * inflate(...);
    public static * bind(android.view.View);
}

# Keep accessibility service
-keep class * extends android.accessibilityservice.AccessibilityService { *; }

# Keep lifecycle service
-keep class * extends androidx.lifecycle.LifecycleService { *; }
