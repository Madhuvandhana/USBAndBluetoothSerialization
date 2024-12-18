plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dagger.hilt.android.plugin")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.0"
}

android {
    namespace = "com.example.usbserialization"
    compileSdk = 34

    fun loadEnv(): Map<String, String> {
        val envFile = rootProject.file(".env")
        val envMap = mutableMapOf<String, String>()

        if (envFile.exists()) {
            envFile.readLines().forEach { line ->
                // Skip empty lines or lines starting with # (comments)
                if (line.isNotBlank() && !line.startsWith("#")) {
                    val (key, value) = line.split("=", limit = 2).map { it.trim() }
                    envMap[key] = value
                }
            }
        }

        return envMap
    }

    val keystorePassword = System.getenv("KEYSTORE_PASSWORD") ?: "default_keystore_password"
    val privateKeyPassword = System.getenv("PRIVATEKEY_PASSWORD") ?: "default_privatekey_password"
    val encryptionKey = System.getenv("ENCRYPTION_KEY") ?: "default_encryption_key"
    val iv = System.getenv("IV") ?: "default_iv"

    defaultConfig {
        applicationId = "com.example.usbserialization"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }

        buildConfigField("String", "KEYSTORE_PASSWORD", "\"$keystorePassword\"")
        buildConfigField("String", "PRIVATEKEY_PASSWORD", "\"$privateKeyPassword\"")
        buildConfigField("String", "ENCRYPTION_KEY", "\"$encryptionKey\"")
        buildConfigField("String", "IV", "\"$iv\"")
    }

    applicationVariants.all {
        val variant = this
        val outputDir = file("src/main/assets").getAbsolutePath()

        tasks.register("generateHtml_${variant.name}") {
            inputs.file(file("src/main/assets/form.html"))
            outputs.file(file("${outputDir}/form.html"))

            doLast {
                val htmlContent = file("src/main/assets/form.html").readText(Charsets.UTF_8)

                val updatedHtmlContent = htmlContent
                    .replace("<ENCRYPTION_KEY_PLACEHOLDER>", encryptionKey)
                    .replace("<IV_PLACEHOLDER>", iv)

                file("${outputDir}/form.html").writeText(updatedHtmlContent, Charsets.UTF_8)
            }
        }
        val applicationName = android.defaultConfig.applicationId?.split(".")?.last()

        variant.outputs
            .map { it as com.android.build.gradle.internal.api.BaseVariantOutputImpl }
            .forEach { output ->
                val outputFileName =
                    "$applicationName - ${variant.baseName} - ${variant.versionName} ${variant.versionCode}.apk"
                println("OutputFileName: $outputFileName")
                output.outputFileName = outputFileName
                output.processResourcesProvider.configure {
                    dependsOn("generateHtml_${variant.name}")
                }
            }
        tasks.named("merge${variant.name.capitalize()}Assets").configure {
            dependsOn("generateHtml_${variant.name}")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
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
        buildConfig = true
        viewBinding = true
        buildConfig = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    packaging {
        resources {
            excludes += "/META-INF/**"
        }
    }
    testOptions {
        packaging {
            jniLibs {
                useLegacyPackaging = true
            }
        }
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("androidx.navigation:navigation-fragment-ktx:2.8.2")
    implementation("androidx.navigation:navigation-ui-ktx:2.7.7")
    val room_version = "2.6.1"

    val coroutine = "1.7.1"

    val mockkVersion = "1.11.0"

    val hilt_version = "2.48"

    val ktor_version = "3.0.0-rc-1"

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation(platform("androidx.compose:compose-bom:2024.09.03"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout-compose:1.0.1")

    // Dagger - Hilt
    implementation("androidx.hilt:hilt-navigation-fragment:1.1.0")
    implementation("androidx.hilt:hilt-navigation-compose:1.1.0")
    implementation("com.google.dagger:hilt-android:$hilt_version")
    ksp("com.google.dagger:hilt-compiler:$hilt_version")
    ksp("androidx.hilt:hilt-compiler:1.2.0-alpha01")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.11.0")

    // Unit test
    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.test.ext:junit:1.1.5")
    testImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2023.03.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("io.mockk:mockk:$mockkVersion")
    androidTestImplementation("io.mockk:mockk-android:$mockkVersion")
    testImplementation("app.cash.turbine:turbine:1.0.0")
    androidTestImplementation("app.cash.turbine:turbine:1.0.0")
    androidTestImplementation("androidx.test:core:1.5.0")
    androidTestImplementation("org.robolectric:shadows-framework:4.11.1")
    androidTestImplementation("androidx.test:rules:1.5.0")

    // Coroutine
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutine")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutine")

    // Room
    implementation("androidx.room:room-runtime:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Kotlin Extensions and Coroutines support for Room
    implementation("androidx.room:room-ktx:$room_version")

    implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
    implementation("androidx.appcompat:appcompat:1.6.1")

    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.6.2")
    val fragment_version = "1.6.2"

    // Java language implementation
    implementation("androidx.fragment:fragment-ktx:$fragment_version")
    // Kotlin
    implementation("androidx.fragment:fragment-ktx:$fragment_version")

    // ktor
    implementation("io.ktor:ktor-server-core:$ktor_version")
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-network-tls-certificates:$ktor_version")

    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
