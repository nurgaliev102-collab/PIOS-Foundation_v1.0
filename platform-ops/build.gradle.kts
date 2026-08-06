// Platform Operations Center's backend agent (T-3,
// ENGINEERING_EXECUTION_PLAN_SPRINT_1_PLATFORM_OPERATIONS_MVP.md). NOT a
// PIOS module: it owns no capability, models no domain, holds no data
// (ADR-046 Decision 1). It is therefore an independent Gradle project --
// its own wrapper, its own build, no entry in backend/settings.gradle.kts
// -- the same relationship `backend/` and `frontend/` already have to each
// other (frontend/README.md: "independent projects with independent
// tooling and independent run commands"). Plugin versions match
// backend/build.gradle.kts for consistency, not because this project is
// part of that build.
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
    // T-3's own Definition of Done: "no database, no event, no domain
    // dependency of any kind." ADR-046 Decision 3 (the agent never asks a
    // domain module anything) means it never needs an HTTP client for any
    // module's own API either.
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

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
