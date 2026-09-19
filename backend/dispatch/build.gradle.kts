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

    // PostgreSQL Persistence Dispatch v1.0: plain JDBC access
    // (JdbcTemplate) to the Dispatch-owned PostgreSQL database (ADR-025;
    // PERSISTENCE_ARCHITECTURE.md Section 3). No ORM is added —
    // spring-boot-starter-jdbc provides JdbcTemplate and DataSource
    // autoconfiguration only, never entity mapping or annotations. Schema
    // is version-controlled and applied exclusively through Flyway
    // migrations (src/main/resources/db/migration/dispatch), consistent
    // with PostgreSQL Persistence Order/Driver Management v1.0.
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.postgresql:postgresql")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Dispatch Consumer Foundation v1.0: Spring AMQP client for RabbitMQ
    // (ADR-029, ADR-031), consuming Driver Management's own
    // driver-management.events exchange through Dispatch's own queue and
    // dead-letter queue. spring-retry + spring-boot-starter-aop back the
    // bounded retry-then-DLQ policy (RetryInterceptorBuilder), Spring
    // AMQP's own standard mechanism for this -- not a custom-built retry
    // platform.
    implementation("org.springframework.boot:spring-boot-starter-amqp")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springframework.boot:spring-boot-starter-aop")

    // ADR-083 (D-10, Driver Web Push for Open Proposal and Price
    // Confirmation): a maintained JVM Web Push library implementing RFC
    // 8291 (payload encryption) and RFC 8292 (VAPID) -- ADR-011 (Security
    // Principles) forbids hand-rolled cryptography. nl.martijndwars:web-push
    // is the maintained Java implementation of the web-push-libs/webpush-java
    // project. Its own published POM declares its BouncyCastle dependency
    // "optional" (never pulled transitively) and its synchronous send
    // response as org.apache.http.HttpResponse (from httpcore, a "runtime"-
    // scoped transitive dependency of its own httpasyncclient dependency,
    // not visible on this module's compile classpath by default) -- both
    // declared explicitly below so WebPushDriverPushNotifier compiles and
    // runs, at the exact versions web-push:5.1.2's own POM names.
    implementation("nl.martijndwars:web-push:5.1.2")
    implementation("org.bouncycastle:bcprov-jdk15on:1.70")
    implementation("org.apache.httpcomponents:httpcore:4.4.16")

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
