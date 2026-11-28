# EasyMandarin ProGuard / R8 rules

# Preserve line numbers for readable crash stacktraces
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization (all catalog and grammar note DTOs)
-keepattributes *Annotation*,InnerClasses
-dontnote kotlinx.serialization.SerializationKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.chinoisfacile.mandarin.data.model.** {
    <fields>;
    <methods>;
}
-keepclassmembers class com.chinoisfacile.mandarin.data.model.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.chinoisfacile.mandarin.data.model.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.chinoisfacile.mandarin.data.model.**$$serializer { *; }

-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static final <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1>$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room Database, Entities, DAOs and generated _Impl
-keep class * extends androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase_Impl
-keep class com.chinoisfacile.mandarin.data.local.AppDatabase* { *; }
-keep @androidx.room.Entity class * { *; }
-keep interface com.chinoisfacile.mandarin.data.local.dao.** { *; }
-keep class com.chinoisfacile.mandarin.data.local.dao.**_Impl { *; }

# DataStore Preferences Protobuf
-keepclassmembers class * extends androidx.datastore.preferences.protobuf.GeneratedMessageLite {
    <fields>;
}

# Kotlin Coroutines (fast ServiceLoader / MainDispatcherFactory)
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.debug.**

# Supabase-kt, Ktor & OkHttp (optional Cloud Backup)
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keep class io.github.jan.supabase.** { *; }
-keep class com.chinoisfacile.mandarin.data.cloud.** { *; }
-keepclassmembers class com.chinoisfacile.mandarin.data.cloud.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.chinoisfacile.mandarin.data.cloud.**$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class com.chinoisfacile.mandarin.data.cloud.**$$serializer { *; }

-keep class io.ktor.client.** { *; }
-keep class io.ktor.utils.** { *; }
-keep class io.ktor.serialization.** { *; }
-keep class io.ktor.client.engine.okhttp.OkHttpEngineContainer { *; }
-if class io.ktor.client.engine.okhttp.OkHttpEngineContainer
-keep class io.ktor.client.engine.okhttp.** { *; }
-keep class * implements io.ktor.client.engine.HttpClientEngineContainer { *; }
-keep class * implements io.ktor.client.plugins.api.ClientPlugin { *; }

-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn sun.misc.Unsafe
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }
