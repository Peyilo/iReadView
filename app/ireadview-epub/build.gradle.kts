plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

group = "org.peyilo.ireadview"
version = providers.gradleProperty("ireadVersion").getOrElse("0.1.0-SNAPSHOT")

android {
    namespace = "org.peyilo.ireadview.epub"
    compileSdk = 36
    defaultConfig { minSdk = 21 }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
    publishing { singleVariant("release") { withSourcesJar() } }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = project.group.toString()
            artifactId = "ireadview-epub"
            version = project.version.toString()
            pom {
                name.set("iReadView EPUB")
                description.set("Optional API 21 EPUB body engine for the iReadView widget.")
            }
            afterEvaluate { from(components["release"]) }
        }
    }
    repositories {
        maven { name = "local"; url = uri(layout.buildDirectory.dir("repository")) }
    }
}

tasks.register<Zip>("bundleReleaseRepository") {
    dependsOn("publishReleasePublicationToLocalRepository")
    from(layout.buildDirectory.dir("repository"))
    into("ireadview-epub-maven")
    archiveBaseName.set("ireadview-epub-maven")
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

dependencies {
    api(project(":app:ireadview"))
    implementation(libs.jsoup)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.nio)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
