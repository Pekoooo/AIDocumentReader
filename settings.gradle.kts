pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
        // ObjectBox repository for the Gradle plugin
        maven { url = uri("https://raw.githubusercontent.com/objectbox/objectbox-java/main") }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // ObjectBox repository for runtime dependencies
        maven { url = uri("https://raw.githubusercontent.com/objectbox/objectbox-java/main") }
    }
}

rootProject.name = "AIDocumentReader"
include(":app")
 