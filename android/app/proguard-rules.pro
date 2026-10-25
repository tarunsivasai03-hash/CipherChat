# kotlinx.serialization: keep generated serializers for our DTOs
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.cipherchat.core.network.** { *** Companion; }
-keepclasseswithmembers class com.cipherchat.core.network.** { kotlinx.serialization.KSerializer serializer(...); }

# Retrofit service interfaces
-keep,allowobfuscation interface com.cipherchat.core.network.ApiService

# Socket.IO / engine.io reflectively look up some classes
-dontwarn io.socket.**
-keep class io.socket.** { *; }
-dontwarn org.json.**
