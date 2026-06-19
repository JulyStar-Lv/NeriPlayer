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
        maven { url = uri("https://jitpack.io") }
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "NeriPlayer"
include(":app")
include(":ksp-annotations")
include(":ksp-processor")
include(":meta-data")
include(":lyrics-core")
include(":lyrics-ui")
includeBuild("build-logic")

project(":meta-data").projectDir = file("np-submodule/meta-data")
project(":lyrics-core").projectDir = file("np-submodule/lyrics-core")
project(":lyrics-ui").projectDir = file("np-submodule/lyrics-ui")
