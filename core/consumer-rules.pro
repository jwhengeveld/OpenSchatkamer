# Jsoup reflectie en resources
-keep class org.jsoup.** { *; }
-keepclassmembers class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# OkHttp optionele platformklassen
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# Core datamodellen
-keepnames class nl.openschatkamer.core.data.** { *; }

# org.json platform
-dontwarn org.json.**
