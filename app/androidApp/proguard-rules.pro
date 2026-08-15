-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keepattributes SourceFile, LineNumberTable

-keep,includedescriptorclasses class com.martdev.flickq.**$$serializer { *; }
-keepclassmembers class com.martdev.flickq.** { *** Companion; }
-keepclasseswithmembers class com.martdev.flickq.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Escalation path — only uncomment if the step-9 smoke test shows a route
# argument or a network DTO failing to (de)serialize in the release build
# and the targeted rules above aren't enough. Keeps every member on every
# app class, at the cost of a larger, less-shrunk APK.
# -keep @kotlinx.serialization.Serializable class com.martdev.flickq.** { *; }

-keep class com.google.crypto.tink.** { *; }
-keep interface com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**