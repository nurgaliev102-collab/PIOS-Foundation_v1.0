// Billing module (ADR-074: Subscription / Billing Bounded Context
// Foundation). Owns a minimal per-driver subscription state machine
// (FREE/TRIAL/ACTIVE/EXPIRED) -- the record of money for PIOS itself, kept
// separate from the still-unscaffolded Payments context (ADR-018), which
// records money for a ride.
// Responsibility and boundary: docs/ADR/ADR-074-Subscription-Billing-Bounded-Context-Foundation.md.
//
// No RabbitMQ/AMQP dependency: this module consumes no event, publishes no
// event, and calls no other module (ADR-074 Part 4) -- no outbox/message-
// broker infrastructure is added, mirroring identity's own build.gradle.kts
// (ADR-038) for the identical reason.

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
    // database (pios_billing), matching every other module's own
    // persistence style (ADR-025; PERSISTENCE_ARCHITECTURE.md Section 3) --
    // no ORM. Schema is version-controlled and applied exclusively through
    // Flyway migrations (src/main/resources/db/migration/billing).
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
