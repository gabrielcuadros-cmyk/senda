# Senda release rules. Firebase, Room, Hilt, WorkManager, OkHttp and kotlinx-serialization ship
# their own consumer rules; these cover what the app itself reads by reflection.

# Firestore maps these classes by reflection (toObject / set): keep their no-arg constructors,
# fields and getters/setters with their original names.
-keep class com.fuerzasalvaje.senda.data.remote.dto.** { *; }
-keep class com.fuerzasalvaje.senda.data.model.** { *; }
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# Gemini REST bodies and navigation routes are @Serializable: keep their generated serializers.
-keepattributes RuntimeVisibleAnnotations, AnnotationDefault
-keepclassmembers @kotlinx.serialization.Serializable class com.fuerzasalvaje.senda.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.fuerzasalvaje.senda.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Enums are stored by name in Room and Firestore.
-keepclassmembers enum com.fuerzasalvaje.senda.** { *; }

# PdfBox-Android references optional desktop and JPEG2000 classes it never loads on Android.
-dontwarn com.gemalto.jp2.**
-dontwarn javax.xml.bind.**
-dontwarn org.bouncycastle.**

# Readable crash stack traces.
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile
