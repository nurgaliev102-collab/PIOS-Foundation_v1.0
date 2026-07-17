// Passenger Experience module skeleton. Architectural placeholder only — no business
// logic, no domain entities, no database model, no API endpoint.
// Responsibility and boundary: docs/MODULE_STRUCTURE.md, "Passenger Experience Module".
// Interaction contracts: docs/INTERFACE_CONTRACTS.md.
// Scope note: this milestone creates the module's structural boundary only; per
// docs/REPOSITORY_INITIALIZATION_PLAN.md Section 4, its implemented responsibility
// will initially be limited to the minimal originating-context contract Order
// Management depends on.

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
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
