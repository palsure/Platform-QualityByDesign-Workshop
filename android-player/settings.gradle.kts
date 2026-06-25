// Both blocks list a Google-hosted Maven Central mirror after MC. That
// CDN serves the same artifacts but isn't subject to the IP-block-level
// throttling MC applies to GitHub Actions runners (which has caused
// repeated 403 Forbidden failures during ./gradlew connectedAndroidTest
// runs). With it in the chain Gradle automatically retries against the
// mirror when MC declines — invisible on a healthy day, lifesaver on a
// throttled one.
pluginManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
    }
}

rootProject.name = "StreamApp"
include(":app")
