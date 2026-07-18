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

    // Test-scoped only, per ADR-027 (MVP Integration Mechanism): used
    // solely by OrderAssignmentContractVerificationTest to prove that
    // this module's OrderAssignedPublisher (application layer) produces
    // a payload order-management's OrderAssignmentRecognitionHandler
    // accepts. Excluded from this module's packaged/deployed artifact,
    // so it does not affect independent buildability or deployability
    // (ADR-026). No production code in this module depends on
    // order-management.
    testImplementation(project(":order-management"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
