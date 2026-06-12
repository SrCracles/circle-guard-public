plugins {
    id("org.springframework.boot") version "3.4.5" apply false
    id("io.spring.dependency-management") version "1.1.4" apply false
    kotlin("jvm") version "1.9.24" apply false
    kotlin("plugin.spring") version "1.9.24" apply false
    kotlin("plugin.jpa") version "1.9.24" apply false
}

allprojects {
    group = "com.circleguard"
    version = "1.0.0-SNAPSHOT"

    extra["tomcat.version"] = "10.1.55"
    extra["spring-security.version"] = "6.5.9"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "jacoco")
    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:3.4.5"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:3.4.5"))
        "compileOnly"("org.projectlombok:lombok")
        "annotationProcessor"("org.projectlombok:lombok")
        "testCompileOnly"("org.projectlombok:lombok")
        "testAnnotationProcessor"("org.projectlombok:lombok")
        "implementation"("org.jetbrains.kotlin:kotlin-reflect")
        "testImplementation"("org.springframework.boot:spring-boot-starter-test")
        "testRuntimeOnly"("com.h2database:h2")
        "runtimeOnly"("org.flywaydb:flyway-database-postgresql") // Necesario desde Flyway 10.x para soportar PostgreSQL
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjsr305=strict")
            jvmTarget = "21"
        }
    }

    tasks.withType<JacocoReport> {
        dependsOn(tasks.withType<Test>())
        reports {
            xml.required.set(true)
            html.required.set(true)
        }
        classDirectories.setFrom(
            classDirectories.files.map { dir ->
                fileTree(dir) {
                    exclude("**/*Application.class")
                    exclude("**/config/**")
                }
            }
        )
    }

    tasks.withType<JacocoCoverageVerification> {
        violationRules {
            rule {
                limit {
                    counter = "LINE"
                    minimum = "0.60".toBigDecimal()
                }
            }
        }
        classDirectories.setFrom(
            classDirectories.files.map { dir ->
                fileTree(dir) {
                    exclude("**/*Application.class")
                    exclude("**/config/**")
                }
            }
        )
    }

    tasks.withType<Test> {
        useJUnitPlatform()
        finalizedBy("jacocoTestReport", "jacocoTestCoverageVerification")
    }

    plugins.withId("org.springframework.boot") {
        dependencies {
            add("implementation", "io.micrometer:micrometer-tracing-bridge-brave")
            add("implementation", "io.zipkin.reporter2:zipkin-reporter-brave")
            add("implementation", "net.logstash.logback:logstash-logback-encoder:8.0")
        }
    }
}