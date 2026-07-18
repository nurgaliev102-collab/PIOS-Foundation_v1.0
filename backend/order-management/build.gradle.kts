// Order Management module. Implements the Order Lifecycle capability
// (domain + application layers only — no database model, no API endpoint).
// Responsibility and boundary: docs/MODULE_STRUCTURE.md, "Order Management Module".
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

    // PostgreSQL Persistence Order Management v1.0: plain JDBC access
    // (JdbcTemplate) to the Order Management-owned PostgreSQL database
    // (ADR-025; PERSISTENCE_ARCHITECTURE.md Section 3). No ORM is added —
    // spring-boot-starter-jdbc provides JdbcTemplate and DataSource
    // autoconfiguration only, never entity mapping or annotations. Schema
    // is version-controlled and applied exclusively through Flyway
    // migrations (src/main/resources/db/migration) — never a hand-run or
    // auto-generated schema.sql — so it is reproducible from a clean
    // database in one deterministic step, in production and in tests.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation(kotlin("test"))

    // Test-scoped only, per ADR-027 (MVP Integration Mechanism): used
    // solely by MvpVerticalSliceScenarioTest, which hosts the first
    // complete MVP vertical-slice verification here since Order
    // Management participates in the most steps of the six (Create
    // Order, Complete Order) and already holds two of the three ADR-027
    // consumer handlers exercised by that scenario. Excluded from this
    // module's packaged/deployed artifact, so it does not affect
    // independent buildability or deployability (ADR-026). No production
    // code in this module depends on any of the three.
    testImplementation(project(":passenger-experience"))
    testImplementation(project(":driver-management"))
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
