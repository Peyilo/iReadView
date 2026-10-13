plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "org.peyilo.ireadview"
version = providers.gradleProperty("ireadVersion").getOrElse("0.1.0-SNAPSHOT")

android {
    namespace = "org.peyilo.ireadview"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.customview)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "ireadview"
            version = project.version.toString()
            pom {
                name.set("iReadView")
                description.set("Embeddable Android View reading widget with paged and continuous presentations.")
            }
            afterEvaluate { from(components["release"]) }
        }
    }
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repository"))
        }
    }
}

/** A folder repository includes the dependency metadata absent from a standalone AAR. */
tasks.register<Zip>("bundleReleaseRepository") {
    dependsOn("publishReleasePublicationToLocalRepository")
    from(layout.buildDirectory.dir("repository"))
    into("ireadview-maven")
    archiveBaseName.set("ireadview-maven")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}
