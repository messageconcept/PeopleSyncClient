/*
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 */

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.mikepenz.aboutLibraries.android)
    alias(libs.plugins.cyclonedx)
    id("davx5.common-buildconfig")
}

tasks.cyclonedxBom {
    projectType = org.cyclonedx.model.Component.Type.APPLICATION
    jsonOutput.set(file("build/reports/cyclonedx/sbom-android-app.json"))
    xmlOutput.unsetConvention()
    includeLicenseText = false
    componentName = "PeopleSyncClient"
    componentVersion = android.defaultConfig.versionName
}

aboutLibraries {
    collect {
        // path to our custom library definitions
        configPath = file("../config")
    }
}

android {
    defaultConfig {
        applicationId = "com.messageconcept.peoplesyncclient"

        // versionCode and versionName are defined in the build-logic submodule (AppVersion)
        base.archivesName = "PeopleSyncClient-$versionName"

        /* Android prevents having two apps installed with the same provider authority name. In that case,
        Google Play just shows a generic "Can't install PeopleSync" message. So we derive the authority names
        from the package ID, so that the build variants (and clones) have their own authority names and
        can be installed beside PeopleSync. */
        val debugInfoAuthority = "${applicationId}.provider.debuginfo"
        manifestPlaceholders["debugInfoAuthority"] = debugInfoAuthority
        /* Override the default string values from the core library (core/src/main/res/values/strings.xml)
        so that code using getString(R.string.webdav_authority) etc. gets the correct authority. */
        resValue("string", "authority_debug_provider", debugInfoAuthority)

        // Currently no instrumentation tests for app-ose, so no testInstrumentationRunner
    }

    buildFeatures {
        compose = true
        resValues = true
    }

    // Java namespace for our classes (not to be confused with Android package ID)
    namespace = "com.messageconcept.peoplesyncclient.ose"

    flavorDimensions += "distribution"
    productFlavors {
        create("ose") {
            dimension = "distribution"
            versionNameSuffix = "-ps"
        }
    }

    androidResources {
        generateLocaleConfig = true
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

            // must be after signingConfigs {} block
            signingConfig = signingConfigs.findByName("bitfire")
        }
    }
}

dependencies {
    // include core subproject (manages its own dependencies itself, however from same version catalog)
    implementation(project(":core"))

    // Kotlin / Android
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.coroutines)
    coreLibraryDesugaring(libs.android.desugaring)

    // Hilt
    implementation(libs.hilt.android.base)
    ksp(libs.androidx.hilt.compiler)
    ksp(libs.hilt.android.compiler)

    // support libs
    implementation(libs.androidx.core)
    implementation(libs.androidx.hilt.work)
    implementation(libs.androidx.lifecycle.viewmodel.base)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.base)

    // Jetpack Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.compose.ui.toolingPreview)

    // own libraries
    implementation(libs.bitfire.cert4android)

    // third-party libs
    implementation(libs.guava)
    implementation(libs.okhttp.base)
    implementation(libs.openid.appauth)
}