# 保留注解、泛型签名、内部类、源文件名与行号
# - 注解：Hilt/Room/Serialization 依赖
# - 泛型签名 Signature：kotlinx.serialization 反序列化泛型必需
# - InnerClasses/EnclosingMethod：序列化 $$serializer 定位 Companion 必需
# - SourceFile/LineNumberTable：崩溃栈可读
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable

# ===== Room =====
-keep class win.iqwqi.xiangece.data.local.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ===== Hilt / Dagger（依赖注入，保留注入点）=====
-keep class dagger.hilt.** { *; }
-keep,allowobfuscation @dagger.hilt.android.HiltAndroidApp class *
-keep,allowobfuscation @javax.inject.Inject class *
-keepclassmembers class * {
    @javax.inject.Inject <init>(...);
}

# ===== kotlinx.serialization =====
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
-keep,includedescriptorclasses class win.iqwqi.xiangece.**$$serializer { *; }
-keepclassmembers class win.iqwqi.xiangece.** {
    *** Companion;
}
-keepclasseswithmembers class win.iqwqi.xiangece.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ===== PDFBox Android（com.tom-roush，反射加载字体/CMap，必须保留）=====
-keep class org.apache.pdfbox.** { *; }
-keep class com.tom_roush.** { *; }
-dontwarn org.apache.pdfbox.**
-dontwarn com.tom_roush.**

# ===== OkHttp / OkHttp3 =====
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ===== Kotlin Metadata（反射框架运行时依赖）=====
-keep,allowobfuscation,allowshrinking class kotlin.Metadata { *; }

# ===== ML Kit OCR（bundled，内部反射；自带 consumer rules，这里仅保险）=====
-dontwarn com.google.mlkit.**
