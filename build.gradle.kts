plugins {
    // Apply Kotlin plugins with apply false to avoid duplicate loading warnings
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.js) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // Compose compiler plugin applied directly in web module
    // Quality plugins commented out for initial setup
    // alias(libs.plugins.detekt) apply false
    // alias(libs.plugins.ktlint) apply false
    // alias(libs.plugins.kover)
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

// Quality tools commented out for initial setup
// subprojects {
//     apply(plugin = "io.gitlab.arturbosch.detekt")
//     apply(plugin = "org.jlleitschuh.gradle.ktlint")
// }

// Kover coverage reporting (commented out for now)
// kover {
//     reports {
//         xml.required.set(true)
//     }
// }

