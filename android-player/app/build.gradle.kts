plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.devopsdays.qoe.player"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.platform.android"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Wipe app data + permissions between tests for true isolation when
        // running under the orchestrator.
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        // Instrumented test stage filter: -PtestStage=bat|smoke|regression|all
        val testStage = (project.findProperty("testStage") as? String) ?: "all"
        when (testStage) {
            "bat"        -> testInstrumentationRunnerArguments["annotation"] =
                "com.devopsdays.qoe.player.categories.BAT"
            "smoke"      -> testInstrumentationRunnerArguments["annotation"] =
                "com.devopsdays.qoe.player.categories.Smoke"
            "regression" -> testInstrumentationRunnerArguments["annotation"] =
                "com.devopsdays.qoe.player.categories.Regression"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        // ── JVM Unit Tests — parallel JVM forks ───────────────────────────────
        unitTests.all {
            // Use ALL available CPU cores (capped at 4 for GitHub-hosted runners
            // which expose 4 vCPUs). One test class per fork → maximum CPU usage.
            it.maxParallelForks = Runtime.getRuntime().availableProcessors()
                .coerceIn(2, 4)
            // Recycle JVMs after this many test classes to keep heap stable.
            it.forkEvery = 100
            it.jvmArgs("-Xmx1g", "-XX:+UseParallelGC")
        }

        // ── Instrumented Tests — Android Test Orchestrator ────────────────────
        // Each test method runs in its OWN process (clearPackageData=true), so:
        //   • crashes in one test don't poison the next
        //   • the orchestrator can dispatch tests to worker processes
        //   • the same emulator instance is reused (no boot overhead per test)
        // This is the standard way to get parallel-style instrumentation tests
        // on a single emulator without firing up multiple devices.
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        animationsDisabled = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")

    // ExoPlayer / Media3 (HLS playback)
    implementation("androidx.media3:media3-exoplayer:1.3.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.3.1")
    implementation("androidx.media3:media3-ui:1.3.1")
    implementation("androidx.media3:media3-common:1.3.1")

    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // Networking
    implementation("com.google.code.gson:gson:2.10.1")

    // ── JVM Unit Tests ────────────────────────────────────────────────────────
    testImplementation("junit:junit:4.13.2")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    // Allure JUnit4 adapter — generates results in build/allure-results/unit/
    testImplementation("io.qameta.allure:allure-junit4:2.27.0")

    // ── Instrumented Tests (androidTest) ─────────────────────────────────────
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test.espresso:espresso-idling-resource:3.5.1")
    // AndroidX Test Orchestrator — runs each test in its own process so they
    // can be dispatched in parallel and don't share state. Required by the
    // testOptions { execution = "ANDROIDX_TEST_ORCHESTRATOR" } setting above.
    androidTestUtil("androidx.test:orchestrator:1.4.2")
}

// ── Custom Gradle tasks (mirrors backend-api convention) ──────────────────────

tasks.register("unitTest") {
    group = "verification"
    description = "Run JVM unit tests and write Allure results to build/allure-results/unit"
    dependsOn("testDebugUnitTest")
}

tasks.register("batTest") {
    group = "verification"
    description = "Run BAT instrumented tests on a connected device/emulator"
    dependsOn("connectedDebugAndroidTest")
    doFirst {
        android.defaultConfig.testInstrumentationRunnerArguments["annotation"] =
            "com.devopsdays.qoe.player.categories.BAT"
    }
}

tasks.register("smokeTest") {
    group = "verification"
    description = "Run Smoke instrumented tests on a connected device/emulator"
    dependsOn("connectedDebugAndroidTest")
    doFirst {
        android.defaultConfig.testInstrumentationRunnerArguments["annotation"] =
            "com.devopsdays.qoe.player.categories.Smoke"
    }
}

tasks.register("regressionTest") {
    group = "verification"
    description = "Run Regression instrumented tests on a connected device/emulator"
    dependsOn("connectedDebugAndroidTest")
    doFirst {
        android.defaultConfig.testInstrumentationRunnerArguments["annotation"] =
            "com.devopsdays.qoe.player.categories.Regression"
    }
}

tasks.register("e2eTest") {
    group = "verification"
    description = "Run all instrumented E2E tests (BAT + Smoke + Regression)"
    dependsOn("connectedDebugAndroidTest")
}

tasks.register("cleanAllure") {
    group = "build"
    description = "Delete Allure results and reports (keeps compiled classes)"
    doLast {
        delete("${layout.buildDirectory.get()}/allure-results")
        delete("${layout.buildDirectory.get()}/reports/allure-report")
    }
}

tasks.register("cleanAll") {
    group = "build"
    description = "Full clean: compiled classes + Allure results + reports"
    dependsOn("clean", "cleanAllure")
}
