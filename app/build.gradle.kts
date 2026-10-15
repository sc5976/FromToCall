import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    kotlin("kapt")
}

/**
 * Версия растёт сама на каждой release-сборке: номер сборки хранится в
 * version.properties (1.0, 1.1, 1.2, …), а versionCode берётся из эпохи,
 * чтобы всегда увеличиваться и проходить проверку обновления на устройстве.
 *
 * Для debug-сборок ничего не меняется: чтобы при каждом запуске отладки
 * телефон не требовал удаления старой версии.
 */
val versionFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
    if (versionFile.exists()) {
        versionFile.inputStream().use { load(it) }
    } else {
        setProperty("major", "1")
        setProperty("minor", "0")
    }
}
val currentMajor = (versionProps.getProperty("major") ?: "1").toInt()
val currentMinor = (versionProps.getProperty("minor") ?: "0").toInt()
val releaseVersionName = "$currentMajor.$currentMinor"

android {
    namespace = "com.fromtocall"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.fromtocall"
        minSdk = 26
        targetSdk = 34
        // versionCode всегда растёт от сборки к сборке (эпоха в секундах),
        // versionName — человекочитаемый, из version.properties.
        versionCode = (System.currentTimeMillis() / 1000L).toInt()
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            storeFile = rootProject.file("fromtocall-release.jks")
            storePassword = "fromtocall"
            keyAlias = "fromtocall"
            keyPassword = "fromtocall"
        }
    }

    buildTypes {
        debug {
            // Маркер отладочной сборки в имени, чтобы случайно не отдать
            // debug-APK вместо release. versionCode остаётся тот же, что
            // у defaultConfig, чтобы переустановка поверх не требовала
            // удаления приложения.
            versionNameSuffix = "-debug"
        }
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.activity:activity-compose:1.8.0")

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")

    implementation("com.google.dagger:hilt-android:2.48")
    kapt("com.google.dagger:hilt-android-compiler:2.48")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")

    implementation("androidx.datastore:datastore-preferences:1.0.0")

    implementation("androidx.navigation:navigation-compose:2.7.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

kapt {
    correctErrorTypes = true
}

/**
 * Готовый APK кладём в dist уже под человеческим именем: «app-release.apk»
 * ни о чём не говорит. Имя берёт версию, которую выше выставил build-скрипт.
 */
fun registerDistTask(name: String, buildTask: String, apkDir: String, suffix: String) =
    tasks.register<Copy>(name) {
        dependsOn(buildTask)
        from(layout.buildDirectory.dir("outputs/apk/$apkDir")) {
            include("*.apk")
            rename { "FromToCall-$suffix-$releaseVersionName.apk" }
        }
        into(rootProject.layout.projectDirectory.dir("dist"))
    }

registerDistTask("distRelease", "assembleRelease", "release", "release")
registerDistTask("distDebug", "assembleDebug", "debug", "debug")

// Ба-бамп minor только если release-сборка дошла до успешной упаковки.
// Если сборка упадёт — номер не сдвинется, и следующий запуск попробует
// тот же 1.x заново.
afterEvaluate {
    tasks.named("assembleRelease").configure {
        doLast {
            val nextMinor = currentMinor + 1
            versionProps.setProperty("major", currentMajor.toString())
            versionProps.setProperty("minor", nextMinor.toString())
            versionFile.outputStream().use {
                versionProps.store(it, "auto-bumped by assembleRelease")
            }
        }
    }
}
