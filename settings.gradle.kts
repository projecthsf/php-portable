plugins {
    // Lets Gradle auto-download a matching JDK for the daemon toolchain when one
    // isn't already installed — keeps the build portable across machines/CI.
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "php-portable"
