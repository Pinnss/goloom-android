# gomobile-сгенерированные классы (под пакетом app.goloom.bridge)
-keep class app.goloom.bridge.** { *; }
-keep class go.** { *; }
-keep class mobile.** { *; }

# Реализация SocketProtector через JNI — рефлексия из Go
-keepclassmembers class * implements app.goloom.bridge.mobile.SocketProtector { *; }
-keepclassmembers class * implements app.goloom.bridge.mobile.LogSink { *; }

# WireGuard tunnel
-keep class com.wireguard.** { *; }
