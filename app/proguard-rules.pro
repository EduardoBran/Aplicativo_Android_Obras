# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ─────────────────────────────────────────────────────────────────────
# ProGuard/R8 – Regras para este app (Retrofit + OkHttp + Kotlinx Serialization)
# ─────────────────────────────────────────────────────────────────────

# 1) Preserve metadados importantes (anotações, genéricos etc.)
#    Retrofit e o serializer usam anotações em runtime.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature,MethodParameters,RuntimeVisibleAnnotations,RuntimeInvisibleAnnotations

# 2) Kotlinx Serialization
#    Evita que o R8 remova/obfusque os serializers gerados e
#    garante que as anotações @Serializable/@SerialName sejam mantidas.
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class ** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
# Mantém o pacote do runtime do serialization
-keep class kotlinx.serialization.** { *; }

# (Opcional) se quiser ser ainda mais explícito com seus DTOs do OpenAI:
# -keep class com.luizeduardobrandao.obra.di.** { *; }

# 3) Retrofit/OkHttp (normalmente já trazem consumer rules,
#    mas estas linhas silenciam warnings em builds minificados)
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# 4) Coil / Firebase
#    Em geral já possuem consumer rules nos AARs; nada extra é necessário.
#    As linhas acima bastam para não poluir o build com warnings.

# 5) (Opcional) Caso queira preservar nomes de classes públicas do seu app:
# -keep class com.luizeduardobrandao.obra.** { *; }