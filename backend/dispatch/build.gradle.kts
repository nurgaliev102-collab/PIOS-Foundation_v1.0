// Dispatch module. Implements the Assignment creation capability (domain +
// application layers only — no assignment algorithm, no database model, no
// API endpoint). Responsibility and boundary: docs/MODULE_STRUCTURE.md,
// "Dispatch Module". Interaction contracts: docs/INTERFACE_CONTRACTS.md.
// Per ADR-002, the specific assignment criteria are never implemented here or
// anywhere in this documentation set.

plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("jvm")
    kotlin("plugin.spring")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
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
