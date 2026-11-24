plugins {
    java
    id("org.springframework.boot") version "4.2.0"
}

group = "com.oldmermaidman.gomoku"
version = "0.4.0"

repositories {
    mavenCentral()
}

// Java 21 LTS is the target; the build also runs on newer JDKs because the compiler
// is pinned with `options.release` (see tasks below).
java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    // Versions of everything from Spring Boot itself come from this BOM.
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.2.0"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-websocket")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Schema is owned by Flyway. Since Spring Boot 4 the auto-configuration lives in
    // its own module — `flyway-core` alone would be on the classpath but never run —
    // and since Flyway 10 the PostgreSQL support is a separate artifact as well.
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // JWT: issue and verify with the same small library.
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")

    // API documentation for local development.
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.2.1")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.testcontainers:junit-jupiter:1.21.4")
    testImplementation("org.testcontainers:postgresql:1.21.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}