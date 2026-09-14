pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // Project-level repository declarations are a build error: every dependency must resolve
    // through the repositories declared here, so there is one place to audit what this build
    // downloads from where.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "block-instagram-reels"

include(":app")
