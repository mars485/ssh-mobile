# SSHJ / BouncyCastle
-keep class net.schmizz.** { *; }
-keep class com.hierynomus.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
-dontwarn net.schmizz.**
-dontwarn com.hierynomus.**

# SSHJ references optional SLF4J implementations
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# Room entities are accessed by generated code
-keep class dev.sshmobile.core.database.entity.** { *; }
