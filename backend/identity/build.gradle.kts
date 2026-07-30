// Identity module (ADR-038). Owns Identity — a phone-anchored id — and, as
// unpersisted domain shapes only, the future Credential/Device/
// VerificationChallenge/Session concepts a later, separately-authorized
// ADR would wire to a real authentication mechanism.
// Responsibility and boundary: docs/ADR/ADR-038-Identity-Module-Bounded-Context-Foundation.md.
//
// No RabbitMQ/AMQP dependency: this module introduces zero integration with
// any existing module (ADR-038's own boundary, mirroring ADR-037's for
// network-management) — no event is published or consumed, so no
// outbox/message-broker infrastructure is added.

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
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
