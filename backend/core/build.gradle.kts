// PIOS Core module — Slice 01 (Participant History Projection), ratified in
// ADR-067. A new bounded context beside Taxi, not a rewrite of it. Its entire
// Slice 01 responsibility is a single read-only projection: for each
// participant, the ordered history of platform events concerning them, derived
// exclusively by consuming events Taxi modules already publish.
//
// Consumer-only (ADR-067 Event Boundary): no event is published, no outbox is
// wired. The AMQP dependency below is present for @RabbitListener consumption
// and the bounded retry-then-DLQ policy only — mirroring dispatch's own
// Consumer Foundation, never a producer.
//
// Reads and writes exactly one database, pios_core (ADR-067 Write Boundary):
// no other module's datasource URL, no cross-database FK, no shared table.

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

    // Plain JDBC access (JdbcTemplate) to Core's own PostgreSQL database
    // (pios_core), matching every other module's persistence style
    // (ADR-025; PERSISTENCE_ARCHITECTURE.md Section 3) — no ORM.
    // Schema is version-controlled and applied exclusively through Flyway
    // migrations (src/main/resources/db/migration/core).
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Core Consumer Foundation: Spring AMQP client for RabbitMQ (ADR-029,
    // ADR-031), consuming events order-management / dispatch /
    // passenger-experience already publish, through Core's own queues and
    // dead-letter queues. spring-retry + spring-boot-starter-aop back the
    // bounded retry-then-DLQ policy (RetryInterceptorBuilder), Spring
    // AMQP's own standard mechanism — mirroring dispatch's own
    // RabbitMQListenerContainerConfiguration exactly, never a custom-built
    // retry platform, and never a producer.
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    testImplementation(kotlin("test"))
    // No test-scoped dependency on any other module: Core's consumer tests
    // simulate each upstream publisher's envelope shape directly (mirroring
    // dispatch's own OrderCancelledMessagePublisher), never importing an
    // order-management / dispatch / passenger-experience type.
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()

    // Forward test-only QA endpoint configuration from the Gradle
    // invocation (-Dpios.core.qa.postgres.url=… etc.) into the test JVM.
    // Nothing is defaulted here: absence, ambiguity, or any production
    // value is rejected fail-closed by com.pios.core.qa.CoreQaSafetyGate
    // before any integration test opens a connection (ADR-067 QA /
    // Production Gate item 4). A run with no such property executes the
    // unit tests and fails every integration test closed.
    System.getProperties().stringPropertyNames()
        .filter { it.startsWith("pios.core.qa.") }
        .forEach { name -> System.getProperty(name)?.let { value -> systemProperty(name, value) } }
}
