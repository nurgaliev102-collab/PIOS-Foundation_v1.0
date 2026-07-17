// Root build file. Declares plugin versions shared by every module's build
// tooling only — never shared business code or domain logic (MODULE_STRUCTURE.md
// Section 4). Each module applies these plugins itself in its own build.gradle.kts.
plugins {
    id("org.springframework.boot") version "3.3.4" apply false
    id("io.spring.dependency-management") version "1.1.6" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.spring") version "1.9.24" apply false
}

allprojects {
    group = "com.pios"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}
