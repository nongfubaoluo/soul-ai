# libxposed API 102
-keep class io.github.libxposed.api.** {*;}
-keep class io.github.libxposed.api.annotations.** {*;}
-keepattributes *Annotation*
-keepclassmembers class * implements io.github.libxposed.api.XposedModule {
    <init>(...);
}
# 保留你的模块入口类，把下面类名改成你自己的Module类名
# -keep class com.example.soulai.MyModuleEntry
