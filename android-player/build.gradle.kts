// New Relic Mobile (Android) ships its Gradle plugin to Maven Central as
// `com.newrelic.agent.android:agent-gradle-plugin` but the plugin registers
// itself under the SHORT id `newrelic` (NOT `com.newrelic.agent.android`).
// Because the plugins-DSL resolves by registered id and there is no plugin
// marker on plugins.gradle.org, we have to use the buildscript classpath
// pattern below (matches New Relic's official documentation).
//
// Why two repositories? Maven Central rate-limits the GitHub Actions IP
// pool — runners frequently get 403 Forbidden on dependency POMs. Google
// hosts a CDN-fronted mirror of MC at storage-download.googleapis.com that
// is not subject to the same throttling. Listing it second means Gradle
// transparently falls back when MC says no, with zero behaviour change on
// the happy path. Standard hardening pattern for Android CI.
buildscript {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
    }
    dependencies {
        classpath("com.newrelic.agent.android:agent-gradle-plugin:7.5.1")
    }
}

plugins {
    id("com.android.application") version "8.5.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}
