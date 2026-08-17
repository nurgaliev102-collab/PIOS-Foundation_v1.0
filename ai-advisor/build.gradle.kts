// AI Advisor for Owner Control Center (ADR-056, Accepted 2026-08-17, Rollout
// Step 2: server-side MockAIProvider, no real provider yet). NOT a PIOS
// module: it owns no capability, models no domain, holds no data (ADR-056
// Decision 2, mirroring ADR-046 Decision 1's own reasoning for
// `platform-ops`). It is therefore an independent Gradle project -- its own
// wrapper, its own build, no entry in backend/settings.gradle.kts -- the
// same relationship `platform-ops/` already has to `backend/`. Plugin
// versions match backend/build.gradle.kts and platform-ops/build.gradle.kts
// for consistency, not because this project is part of either build.
plugins {
    id("org.springframework.boot") version "3.3.4"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "1.9.24"
    kotlin("plugin.spring") version "1.9.24"
}

group = "com.pios"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Web only. No JDBC, no PostgreSQL driver, no Flyway, no RabbitMQ --
    // ADR-056 Decision 11: no database, no migration, no table. ADR-056
    // Decision 1: this component has no HTTP client for, and no
    // configuration pointing at, any of the five domain modules -- it
    // receives an already-computed PilotAnalyticsInput from the browser
    // instead of reading any module itself.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
