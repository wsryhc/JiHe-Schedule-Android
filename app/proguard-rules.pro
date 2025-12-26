# 1. Gson (JSON 解析防混淆)
# 保护实体类模型 (Course, Todo, ScheduleInfo 等)
-keep class com.example.jihe_schedule.model.** { *; }
-keep class com.google.gson.** { *; }

# 🔥🔥🔥 核心修复：保护 ViewModel 中定义的数据传输对象 (DTO) 🔥🔥🔥
# 这些类用于 JSON 导入导出，必须保持原样，否则 Release 包无法识别
-keep class com.example.jihe_schedule.viewmodel.ScheduleExportData { *; }
-keep class com.example.jihe_schedule.viewmodel.AiImportData { *; }
-keep class com.example.jihe_schedule.viewmodel.TodoImportData { *; }
# 如果 EditableCourseWrapper 也参与了序列化，最好也加上
-keep class com.example.jihe_schedule.viewmodel.EditableCourseWrapper { *; }

# 2. Room (数据库防混淆)
-keep class androidx.room.** { *; }
-dontwarn androidx.room.paging.**

# 3. Glance / Compose (小组件防混淆)
-keep class androidx.glance.** { *; }

# 4. 保持通用签名和注解
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses