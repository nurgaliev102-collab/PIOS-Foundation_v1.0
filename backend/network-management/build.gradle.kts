// Network Management module (Sprint 7A: PIOS Network Foundation). Owns
// Person, Profile, Connection, and Invitation — the Personal Network
// foundation authorized by PRODUCT_DECISION_PERSONAL_NETWORK_MVP_TRANSITION.md
// and placed in a new module by ADR-037.
// Responsibility and boundary: docs/MODULE_STRUCTURE.md, "Network Management Module".
//
// No RabbitMQ/AMQP dependency: Sprint 7A introduces zero integration with any
// existing module (ADR-037's own boundary) — no event is published or
// consumed, so no outbox/message-broker infrastructure is added.

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

    // Plain JDBC access (JdbcTemplate) to this module's own PostgreSQL
    // database, matching every other module's own persistence style
    // (ADR-025; PERSISTENCE_ARCHITECTURE.md Section 3) — no ORM. Schema is
    // version-controlled and applied exclusively through Flyway migrations
    // (src/main/resources/db/migration).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    testImplementation(kotlin("test"))
    testImplementation("org.springframework:spring-test")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
