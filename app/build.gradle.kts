import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

/**
 * Читаем семвер из ../version.properties — единый источник истины для APK
 * versionName, versionCode и тегов в GitHub Releases. Не меняем ничего
 * в этом файле для bump версии — правим только version.properties.
 */
val semver: Triple<Int, Int, Int> = run {
    val file = rootProject.file("version.properties")
    val props = Properties().apply {
        if (file.exists()) file.inputStream().use { load(it) }
    }
    val raw = props.getProperty("VERSION", "0.0.0").trim()
    val parts = raw.split("-").first().split(".") // отбрасываем pre-release suffix для versionCode
    val major = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    Triple(major, minor, patch)
}
val rawVersion = rootProject.file("version.properties")
    .takeIf { it.exists() }
    ?.let { Properties().apply { it.inputStream().use { s -> load(s) } }.getProperty("VERSION", "0.0.0") }
    ?.trim()
    ?: "0.0.0"

android {
    namespace = "app.goloom.client"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.goloom.client"
        minSdk = 26
        targetSdk = 35
        versionCode = semver.first * 10000 + semver.second * 100 + semver.third
        versionName = rawVersion

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Buildconfig поля для UpdateChecker и About-экрана
        buildConfigField("String", "GITHUB_REPO", "\"Pinnss/goloom-android\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    /**
     * Release-подпись через ../keystore.properties (не коммитим в git):
     *   storeFile=/path/to/release.jks
     *   storePassword=...
     *   keyAlias=goloom
     *   keyPassword=...
     * Если файла нет (CI или fresh checkout) — release собирается debug-
     * подписанным, но всё равно работает; UpdateChecker такой APK не
     * сможет atomically обновить из-за смены подписи.
     */
    signingConfigs {
        create("release") {
            val ksPropsFile = rootProject.file("keystore.properties")
            if (ksPropsFile.exists()) {
                val ksProps = Properties().apply { ksPropsFile.inputStream().use { load(it) } }
                storeFile = file(ksProps.getProperty("storeFile"))
                storePassword = ksProps.getProperty("storePassword")
                keyAlias = ksProps.getProperty("keyAlias")
                keyPassword = ksProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            // Включить когда наработаются keep-rules для wireguard/gomobile.
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ksPropsFile = rootProject.file("keystore.properties")
            signingConfig = if (ksPropsFile.exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            // Goloom .aar содержит нативные .so; если оставить compressed,
            // GoBackend от wireguard-android не сможет dlopen() их на старых
            // устройствах.
            useLegacyPackaging = true
        }
        resources.excludes += setOf(
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
            "META-INF/AL2.0",
            "META-INF/LGPL2.1",
        )
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // Goloom mobile bridge (.aar, не коммитится в git — см. HANDOFF.md).
    // Перед сборкой положить файл в app/libs/goloom.aar.
    implementation(files("libs/goloom.aar"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.wireguard.tunnel)

    // QR
    implementation(libs.zxing.core)
    implementation(libs.zxing.android.embedded)
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)
    implementation(libs.mlkit.barcode)
    implementation(libs.mlkit.vision.common)

    // Tests
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    // Robolectric тесты используют JUnit 4 runner — нужен vintage-engine,
    // чтобы Gradle JUnit Platform его подхватил.
    testRuntimeOnly(libs.junit.vintage.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.assertj.core)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.androidx.test.junit)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
