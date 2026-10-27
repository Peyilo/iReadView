plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "org.peyilo.ireadview"
version = providers.gradleProperty("ireadVersion").getOrElse("0.1.0-SNAPSHOT")
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11) } }
java { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11; withSourcesJar() }
dependencies { testImplementation(libs.junit) }
publishing {
    publications {
        register<MavenPublication>("release") {
            from(components["java"])
            artifactId = "pagecurl-core"
            pom { name.set("iReadView Page Curl Core"); description.set("Portable Kotlin page-curl control, gestures, layout and mesh algorithms.") }
        }
    }
    repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repository")) } }
}
