# ProGuard rules for FormulaCalculator

# Keep Room entities
-keep class com.formula.calculator.data.db.** { *; }

# Keep POI classes
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.poi.**
-dontwarn org.apache.xmlbeans.**
-dontwarn org.openxmlformats.**
-dontwarn schemasMicrosoftComVml

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
