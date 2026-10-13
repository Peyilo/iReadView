pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri(providers.gradleProperty("ireadRepository").getOrElse("../../app/ireadview/build/repository"))
            content { includeGroup("org.peyilo.ireadview") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "iReadViewMavenHost"
include(":app")


