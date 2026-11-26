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
