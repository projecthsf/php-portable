import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.18.0"
}

group = "io.genai"
version = "0.1.3"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // IntelliJ IDEA Community — PHP support isn't bundled here, which is the
        // whole point of this plugin.
        intellijIdeaCommunity("2024.1")
        // instrumentationTools() removed in plugin 2.x — code instrumentation deps are
        // now added automatically (intellijPlatform.instrumentCode defaults to true).
    }
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(17) }
}

kotlin {
    jvmToolchain(17)
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "233"
            untilBuild = provider { null }
        }
    }

    // `./gradlew publishPlugin` reads the JetBrains Marketplace token from the PUBLISH_TOKEN
    // env var (set as a GitHub Actions secret). No signing configured, so uploads are unsigned.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    // `./gradlew verifyPlugin` runs the JetBrains Plugin Verifier (same tool Marketplace uses).
    // Pinned to one recent IDE to keep downloads bounded; `recommended()` would pull every
    // major since sinceBuild (233) with no untilBuild cap.
    pluginVerification {
        ides {
            // Verify against the newest *released* unified IDEA (IntelliJ IDEA Community is
            // no longer a separate distribution since 2025.3). One download, and enough to
            // surface forward-compat deprecations. Bump/pin later if you want a wider matrix.
            latest {
                types.set(listOf(IntelliJPlatformType.IntellijIdea))
            }
        }
    }
}

// Indexing settings via a headless IDE is slow and clashes with a running runIde
// sandbox ("Only one instance of IDEA can be run at a time"); not needed for a dev build.
tasks.named("buildSearchableOptions") { enabled = false }
