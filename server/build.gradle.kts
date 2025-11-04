plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.flyway)
    application
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
    google()
}

application {
    mainClass.set("server.ApplicationKt")
}

dependencies {
    implementation(project(":shared"))
    
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.serialization.kotlinx.json)
    
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.kotlin.datetime)
    implementation(libs.postgres)
    implementation(libs.hikaricp)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    
    implementation(libs.lettuce.core)
    
    implementation(libs.slf4j.api)
    implementation(libs.logback.classic)
    
    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.ktor.server.test.host)
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    manifest {
        attributes["Main-Class"] = "server.ApplicationKt"
    }
}

flyway {
    url = System.getenv("JDBC_DATABASE_URL") ?: System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/kmp"
    user = System.getenv("DB_USER") ?: "postgres"
    password = System.getenv("DB_PASSWORD") ?: "postgres"
    locations = arrayOf("filesystem:src/main/resources/db/migration")
}
