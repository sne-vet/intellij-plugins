plugins {
    kotlin("jvm") version "2.1.20" apply false
    id("org.jetbrains.intellij.platform") version "2.13.1" apply false
}

allprojects {
    group = "com.snevet.tools"
    version = "0.1.0"

    repositories {
        mavenCentral()
    }
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }
    }
}
