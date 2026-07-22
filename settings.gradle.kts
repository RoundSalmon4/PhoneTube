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
        maven("https://jitpack.io")
    }
}

rootProject.name = "SmartTube"

include(":app")

// SharedModules
include(":sharedutils")
project(":sharedutils").projectDir = file("SharedModules/sharedutils")

include(":sharedtests")
project(":sharedtests").projectDir = file("SharedModules/sharedtests")

include(":commons-io-2.8.0")
project(":commons-io-2.8.0").projectDir = file("SharedModules/commons-io-2.8.0")

include(":j2v8")
project(":j2v8").projectDir = file("SharedModules/j2v8")

// MediaServiceCore
include(":mediaserviceinterfaces")
project(":mediaserviceinterfaces").projectDir = file("MediaServiceCore/mediaserviceinterfaces")

include(":youtubeapi")
project(":youtubeapi").projectDir = file("MediaServiceCore/youtubeapi")
