plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21"
    id("org.jetbrains.compose") version "1.7.0"
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    js(IR) {
        browser() {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
        }
        binaries.executable()
    }
    
    sourceSets {
        val jsMain by getting {
            dependencies {
                implementation(project(":shared"))
                // Compose Web deps (match the version catalog or use compose.* from the plugin)
                implementation(libs.compose.runtime)
                implementation(libs.compose.html.core)
                implementation(libs.compose.html.svg)
                // Ktor client for JS
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
                implementation(libs.kotlinx.coroutines.js)
            }
        }
    }
}
