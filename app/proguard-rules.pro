# kotlinx.serialization: keep generated serializers of our DTOs.
-keepclassmembers @kotlinx.serialization.Serializable class ru.feskolech.libriatv.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class ru.feskolech.libriatv.**$$serializer { *; }

# Retrofit service interfaces are created reflectively.
-keep,allowobfuscation interface ru.feskolech.libriatv.data.api.AniLibriaApi
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault

# Keep stack traces readable in crash reports.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
