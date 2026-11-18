import groovy.json.JsonSlurper
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

fun gitShortSha(): String = runCatching {
    providers.exec { commandLine("git", "rev-parse", "--short=7", "HEAD") }
        .standardOutput
        .asText
        .get()
        .trim()
}.getOrDefault("nogit")

val buildId: String = "v0.2.0-${gitShortSha()}-" + LocalDateTime.now(ZoneOffset.UTC)
    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))

android {
    namespace = "com.empathie.energyaccounting"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.empathie.energyaccounting"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "0.2.0"

        buildConfigField("String", "BUILD_ID", "\"$buildId\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        create("isolatedTest") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".uitest"
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testBuildType = "isolatedTest"

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets {
        getByName("main") {
            java.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            java.srcDirs("src/test/kotlin")
        }
        getByName("androidTest") {
            java.srcDirs("src/androidTest/kotlin")
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    add("isolatedTestImplementation", libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    // Espresso 3.5's reflective InputManager lookup fails on Android 17.
    androidTestImplementation(libs.androidx.test.espresso.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.runner)
}

// ---- Single-file translation catalog -----------------------------------------
// `app/translations.json` is the source of truth for every user-facing string.
// This task regenerates the Android resource files and the assets copy on every build,
// failing if a key is missing either language.

val translationsFile = layout.projectDirectory.file("translations.json")
val valuesStrings = layout.projectDirectory.file("src/main/res/values/strings.xml")
val valuesItStrings = layout.projectDirectory.file("src/main/res/values-it/strings.xml")
val assetsTranslations = layout.projectDirectory.file("src/main/assets/translations.json")

val syncTranslations by tasks.registering {
    group = "build"
    description = "Generates res/values(-it)/strings.xml from translations.json"

    inputs.file(translationsFile)
    outputs.files(valuesStrings, valuesItStrings, assetsTranslations)

    doLast {
        @Suppress("UNCHECKED_CAST")
        val catalog = JsonSlurper().parse(translationsFile.asFile) as Map<String, Map<String, String>>

        fun escape(value: String): String = value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("'", "\\'")
            .replace("\"", "\\\"")

        fun write(language: String, target: File) {
            target.parentFile.mkdirs()
            val xml = buildString {
                appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
                appendLine("<resources>")
                catalog.forEach { (key, translations) ->
                    val value = translations[language]?.takeIf { it.isNotBlank() }
                        ?: throw GradleException("Missing '$language' translation for '$key'")
                    appendLine("    <string name=\"$key\">${escape(value)}</string>")
                }
                appendLine("</resources>")
            }
            target.writeText(xml)
        }

        write("en", valuesStrings.asFile)
        write("it", valuesItStrings.asFile)

        assetsTranslations.asFile.parentFile.mkdirs()
        assetsTranslations.asFile.writeText(translationsFile.asFile.readText())
    }
}

tasks.named("preBuild") {
    dependsOn(syncTranslations)
}
