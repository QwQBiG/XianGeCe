plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.serialization)
}



android {
    namespace = "win.iqwqi.xiangece"
    compileSdk = 37

    defaultConfig {
        applicationId = "win.iqwqi.xiangece"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        ndk {
            abiFilters += setOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            val storePath = providers.environmentVariable("XIANGECE_STORE_FILE").orNull
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = providers.environmentVariable("XIANGECE_STORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("XIANGECE_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("XIANGECE_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (!providers.environmentVariable("XIANGECE_STORE_FILE").orNull.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }



    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.pickFirsts += "**/libonnxruntime.so"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

hilt {
    enableAggregatingTask = true
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)
    implementation(libs.pdfbox.android)
    implementation(libs.accompanist.swiperefresh)
    implementation(libs.onnxruntime.android)
    implementation(files("libs/opencv-4.10.0.aar"))
    implementation(files("libs/sherpa-onnx-1.13.4-no-ort.aar"))

    testImplementation(libs.junit)
    testImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
