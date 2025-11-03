/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)

    alias(libs.plugins.mikepenz.aboutLibraries.android)
    alias(libs.plugins.cyclonedx)
}

tasks.cyclonedxBom {
    projectType = org.cyclonedx.model.Component.Type.APPLICATION
    jsonOutput.set(file("build/reports/cyclonedx/sbom-android-app.json"))
    xmlOutput.unsetConvention()
    includeLicenseText = false
    componentName = "PeopleSyncClient"
    componentVersion = android.defaultConfig.versionName
}

// Android configuration
android {
    compileSdk = 36

    defaultConfig {
        applicationId = "com.messageconcept.peoplesyncclient"

        //noinspection HighAppVersionCode
        versionCode = 2070040502
        versionName = "4.5-2"

        base.archivesName = "PeopleSyncClient-$versionName"

        minSdk = 24        // Android 7.0
        targetSdk = 36     // Android 16

        // whether the build supports and allows to use custom certificates
        buildConfigField("boolean", "allowCustomCerts", "true")

        testInstrumentationRunner = "com.messageconcept.peoplesyncclient.HiltTestRunner"
    }

    java {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    compileOptions {
        // required for
        // - dnsjava 3.x: java.nio.file.Path
        // - ical4android: time API
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    // Java namespace for our classes (not to be confused with Android package ID)
    namespace = "com.messageconcept.peoplesyncclient"

    flavorDimensions += "distribution"
    productFlavors {
        create("ose") {
            dimension = "distribution"
            versionNameSuffix = "-ps"
        }
    }

    sourceSets {
        getByName("androidTest") {
            assets.srcDir("$projectDir/schemas")
        }
    }

    signingConfigs {
        create("bitfire") {
            storeFile = file(System.getenv("ANDROID_KEYSTORE") ?: "/dev/null")
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules-release.pro")

            isShrinkResources = true

            signingConfig = signingConfigs.findByName("bitfire")
        }
    }

    lint {
        disable += arrayOf("GoogleAppIndexingWarning", "ImpliedQuantity", "MissingQuantity", "MissingTranslation", "ExtraTranslation", "RtlEnabled", "RtlHardcoded", "Typos")
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        resources {
            // multiple (test) dependencies have LICENSE files at same location
            merges += arrayOf("META-INF/LICENSE*")
        }
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        managedDevices {
            localDevices {
                create("virtual") {
                    device = "Pixel 3"
                    // TBD: API level 35 and higher causes network tests to fail sometimes, see https://github.com/bitfireAT/davx5-ose/issues/1525
                    // Suspected reason: https://developer.android.com/about/versions/15/behavior-changes-all#background-network-access
                    apiLevel = 34
                    systemImageSource = "aosp-atd"
                }
            }
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

aboutLibraries {
    collect {
        // path to our custom library definitions
        configPath = file("../config")
    }

    export {
        // exclude timestamps for reproducible builds [https://github.com/bitfireAT/davx5-ose/issues/994]
        excludeFields.add("generated")
    }
}

dependencies {
    // core
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    coreLibraryDesugaring(libs.android.desugaring)

    // Hilt
    implementation(libs.hilt.android.base)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.android.compiler)

    // support libs
    implementation(libs.androidx.activityCompose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.base)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.paging)
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.security)
    implementation(libs.androidx.work.base)

    // Jetpack Compose
    implementation(libs.compose.accompanist.permissions)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.compose.materialIconsExtended)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.compose.ui.toolingPreview)
    implementation(libs.compose.ui.text.google.fonts)

    // Glance Widgets
    implementation(libs.glance.base)
    implementation(libs.glance.material)

    // Jetpack Room
    implementation(libs.room.runtime)
    implementation(libs.room.base)
    implementation(libs.room.paging)
    ksp(libs.room.compiler)

    // own libraries
    implementation(libs.bitfire.cert4android)
    implementation(libs.bitfire.dav4jvm) {
        exclude(group="junit")
        exclude(group="org.ogce", module="xpp3")    // Android has its own XmlPullParser implementation
    }
    implementation(libs.bitfire.synctools) {
        exclude(group="androidx.test")              // synctools declares test rules, but we don't want them in non-test code
        exclude(group = "junit")
    }

    // third-party libs
    implementation(libs.conscrypt)
    implementation(libs.dnsjava)
    implementation(libs.guava)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.mikepenz.aboutLibraries.m3)
    implementation(libs.okhttp.base)
    implementation(libs.okhttp.brotli)
    implementation(libs.okhttp.logging)
    implementation(libs.openid.appauth)

    // for tests
    androidTestImplementation(libs.androidx.arch.core.testing)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.work.testing)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.room.testing)

    testImplementation(libs.bitfire.dav4jvm)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
}
