plugins {
    // AGP 9.1.1 是获得 material3 1.5.0-rc01（LinearWavyProgressIndicator）
    // 所必需的最低构建栈；AGP 9 自带内置 Kotlin 支持，因此不再声明
    // org.jetbrains.kotlin.android 插件。
    id("com.android.application") version "9.1.1" apply false
    // Compose 编译器插件仍由 Kotlin 提供，需要单独声明。
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
