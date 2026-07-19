import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.18.0"
}

group = "io.genai"
version = "0.2.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Compile against 2024.2 (242): LSP4IJ requires build 242+, and the optional
        // semantic-support module compiles against its API. Core sinceBuild stays 233 —
        // the LSP layer is an OPTIONAL dependency, active only where LSP4IJ can install.
        intellijIdeaCommunity("2024.2")
        // instrumentationTools() removed in plugin 2.x — code instrumentation deps are
        // now added automatically (intellijPlatform.instrumentCode defaults to true).

        // Optional PHP code-intelligence layer (completion / navigation / errors) via
        // LSP4IJ + Phpactor. Compile-time dep here; runtime it's optional (see plugin.xml).
        plugin("com.redhat.devtools.lsp4ij", "0.20.1")
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
    // This is a publish gate in CI (see .github/workflows/publish.yml).
    pluginVerification {
        // Fail only on genuine breakage — including INTERNAL_API_USAGES, the class of problem
        // that slipped into 0.1.2. Plain deprecated / scheduled-for-removal usages are reported
        // but do NOT fail the build: two deprecations are unavoidable at sinceBuild 233
        // (SdkType.suggestHomePath is abstract; getPopupStep can't change without breaking the
        // floor), and forward-compat churn on the latest IDE shouldn't block a release.
        failureLevel.set(listOf(
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.INTERNAL_API_USAGES,
            FailureLevel.MISSING_DEPENDENCIES,
            FailureLevel.INVALID_PLUGIN,
        ))
        ides {
            // Verify against the newest *released* unified IDEA (IntelliJ IDEA Community is
            // no longer a separate distribution since 2025.3). One download, enough to catch
            // forward-compat problems. Bump/pin later if you want a wider matrix.
            latest {
                types.set(listOf(IntelliJPlatformType.IntellijIdea))
            }
        }
    }
}

// Indexing settings via a headless IDE is slow and clashes with a running runIde
// sandbox ("Only one instance of IDEA can be run at a time"); not needed for a dev build.
tasks.named("buildSearchableOptions") { enabled = false }
