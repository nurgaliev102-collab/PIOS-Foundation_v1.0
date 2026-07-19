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

    // PostgreSQL Persistence Driver Management v1.0: plain JDBC access
    // (JdbcTemplate) to the Driver Management-owned PostgreSQL database
    // (ADR-025; PERSISTENCE_ARCHITECTURE.md Section 3). No ORM is added —
    // spring-boot-starter-jdbc provides JdbcTemplate and DataSource
    // autoconfiguration only, never entity mapping or annotations. Schema
    // is version-controlled and applied exclusively through Flyway
    // migrations (src/main/resources/db/migration), consistent with
    // PostgreSQL Persistence Order Management v1.0.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Driver Management Event Publishing v1.0: Spring AMQP client for
    // RabbitMQ (ADR-029), applying the pattern already proven in Order
    // Management's RabbitMQ Event Publishing Foundation v1.0. Provides
    // RabbitTemplate (publishing, with publisher confirms per ADR-031) and
    // RabbitAdmin (declares this module's own exchange on startup). No
    // message schema or ORM-like mapping framework is added.
    implementation("org.springframework.boot:spring-boot-starter-amqp")

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
