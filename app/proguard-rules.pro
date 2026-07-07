# Keep kotlinx.serialization generated classes.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Serializer
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Crush model classes
-keep class soy.iko.crush.model.** { *; }
-keep class soy.iko.crush.proto.** { *; }
