# ProGuard rules for Claude Remote

# Keep data classes for serialization
-keep class com.clauderemote.data.model.** { *; }

# Keep Socket.io client
-keep class io.socket.** { *; }
-keep class org.json.** { *; }

# Keep OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }

# Keep kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers @kotlinx.serialization.Serializable class ** {
    *** Companion;
}
-keepclasseswithmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep Hilt
-keepnames @dagger.hilt.android.lifecycle.HiltViewModel class * extends androidx.lifecycle.ViewModel

# Keep Compose
-dontwarn androidx.compose.**

# General Android
-keepclassmembers class * extends android.app.Service {
    public <init>(...);
}
