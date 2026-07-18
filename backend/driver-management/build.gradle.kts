// Driver Management module. Implements the Driver Availability capability
// (domain + application layers only — no database model, no API endpoint).
// Responsibility and boundary: docs/MODULE_STRUCTURE.md, "Driver Management Module".
// Interaction contracts: docs/INTERFACE_CONTRACTS.md.

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
    // solely by DriverAvailabilityContractVerificationTest to prove that
    // this module's DriverAvailabilityChangedPublisher (application
    // layer) produces a payload dispatch's
    // DriverAvailabilityNotificationHandler accepts. Excluded from this
    // module's packaged/deployed artifact, so it does not affect
    // independent buildability or deployability (ADR-026). No production
    // code in this module depends on dispatch.
    testImplementation(project(":dispatch"))
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
