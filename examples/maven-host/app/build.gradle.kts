plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "org.peyilo.iread.host"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.peyilo.iread.host"
        minSdk = 21
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
}

dependencies {
    // Consume the published artifact and its dependency metadata, never the library project.
    implementation("org.peyilo.ireadview:ireadview:${providers.gradleProperty("ireadVersion").getOrElse("0.1.0-SNAPSHOT")}")
}
