pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri(providers.gradleProperty("ireadRepository").getOrElse("../../app/ireadview/build/repository"))
            content {
                includeModule("org.peyilo.ireadview", "ireadview")
                includeModule("org.peyilo.ireadview", "ioscurl")
            }
        }
        maven {
            url = uri(providers.gradleProperty("ireadEpubRepository").getOrElse("../../app/ireadview-epub/build/repository"))
            content { includeModule("org.peyilo.ireadview", "ireadview-epub") }
        }
        google(); mavenCentral()
    }
}
rootProject.name = "iReadViewEpubMavenHost"
include(":app")
