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
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            // Add these lines
            library("firebase-auth", "com.google.firebase:firebase-auth-ktx:22.3.1")
            plugin("google-services", "com.google.gms.google-services").version("4.4.2")
        }
    }
}

rootProject.name = "SnowSpotterApp2"
include(":app")
 