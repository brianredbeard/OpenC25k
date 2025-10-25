plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-parcelize")
    id("io.gitlab.arturbosch.detekt") version "1.23.4"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.0"
    id("org.owasp.dependencycheck") version "9.0.9"
    id("jacoco")
}

android {
    namespace = "se.wmuth.openc25k"
    compileSdk = 34

    defaultConfig {
        applicationId = "se.wmuth.openc25k"
        minSdk = 31
        targetSdk = 33
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    flavorDimensions += "distribution"

    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_FDROID", "true")
            buildConfigField("String", "DISTRIBUTION", "\"fdroid\"")
        }

        create("googlePlay") {
            dimension = "distribution"
            buildConfigField("Boolean", "IS_FDROID", "false")
            buildConfigField("String", "DISTRIBUTION", "\"googleplay\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.1")
    implementation("androidx.core:core-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation("androidx.cardview:cardview:1.0.0")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Unit testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    testImplementation("com.google.truth:truth:1.1.5")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test:core-ktx:1.5.0")

    // Android instrumentation testing
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation("androidx.test:runner:1.5.2")
    androidTestImplementation("androidx.test:rules:1.5.0")
    androidTestImplementation("org.mockito:mockito-android:5.7.0")
    androidTestImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
    androidTestImplementation("com.google.truth:truth:1.1.5")

    // Static analysis
    detektPlugins("io.gitlab.arturbosch.detekt:detekt-formatting:1.23.4")
}

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$rootDir/detekt.yml")
    baseline = file("detekt-baseline.xml")
}

// Fix Detekt JVM target and configure reports
tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "11"
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(true)
        sarif.required.set(true)
        md.required.set(true)
    }
}

// KtLint configuration
ktlint {
    android.set(true)
    ignoreFailures.set(false)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.PLAIN)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
    }
    filter {
        exclude("**/generated/**")
        include("**/kotlin/**")
    }
}

// JaCoCo configuration for code coverage
jacoco {
    toolVersion = "0.8.11"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testFdroidDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }

    val fileFilter = listOf(
        "**/R.class",
        "**/R$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        "**/databinding/**/*.*",
        "**/data/model/*.*"
    )

    val debugTree = fileTree("${project.buildDir}/tmp/kotlin-classes/fdroidDebug") {
        exclude(fileFilter)
    }

    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(files(mainSrc))
    classDirectories.setFrom(files(debugTree))
    executionData.setFrom(fileTree(project.buildDir) {
        include("jacoco/testFdroidDebugUnitTest.exec")
    })
}

// OWASP Dependency Check configuration
dependencyCheck {
    formats = listOf("HTML", "JSON", "SARIF")
    failBuildOnCVSS = 7.0f
    suppressionFile = "$rootDir/owasp-suppression.xml"
    analyzers.apply {
        assemblyEnabled = false
        nuspecEnabled = false
        nodeEnabled = false
    }
}

// License checking placeholder task
tasks.register("checkLicense") {
    doLast {
        println("License compliance check - would verify all dependencies have acceptable licenses")
        // In a real implementation, you'd use a plugin like:
        // https://github.com/jk1/Gradle-License-Report
    }
}