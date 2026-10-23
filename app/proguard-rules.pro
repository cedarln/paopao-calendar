# Room 与 Glance 都随 AAR 带了 consumer proguard 规则，这里只补两处 R8 看不见的反射入口。

# 数据库实现类由 PaopaoDatabase::class.java 反射加载名称时被内联引用，保留以防 minify 改名
-keep class com.paopao.calendar.data.local.** { *; }

# 桌面插件接收器由系统按 manifest 里的类名实例化
-keep class com.paopao.calendar.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }

-dontwarn org.jetbrains.annotations.**
