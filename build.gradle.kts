// Корневой build-скрипт. Подключаемые версии плагинов задаются в
// gradle/libs.versions.toml. Здесь только декларация плагинов с
// apply false — реальное применение происходит в app/build.gradle.kts.

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
