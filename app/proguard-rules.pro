# File: app/proguard-rules.pro
# MyAI Assistant ProGuard Rules

# ═══════════════════════════════════════
# GENERAL
# ═══════════════════════════════════════
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ═══════════════════════════════════════
# RETROFIT + GSON
# ═══════════════════════════════════════
-keepattributes Signature
-keepattributes Exceptions
-keep class com.google.gson.** { *; }
-keep class com.myai.assistant.ai.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ═══════════════════════════════════════
# OKHTTP
# ═══════════════════════════════════════
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ═══════════════════════════════════════
# ROOM
# ═══════════════════════════════════════
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# ═══════════════════════════════════════
# HILT
# ═══════════════════════════════════════
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# ═══════════════════════════════════════
# ML KIT
# ═══════════════════════════════════════
-keep class com.google.mlkit.** { *; }

# ═══════════════════════════════════════
# COROUTINES
# ═══════════════════════════════════════
-keepnames class kotlinx.coroutines.** { *; }
