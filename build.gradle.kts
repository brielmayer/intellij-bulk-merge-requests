import com.diffplug.spotless.LineEnding
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.intellij.platform)
    alias(libs.plugins.spotless)
}

group = "ch.brielmayer"
version = "0.1.0"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // Since 2025.3 IDEA ships as a single distribution (no separate IC artifact).
        intellijIdea(libs.versions.intellijIdea.get())
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)
    }

    implementation(libs.kotlinx.serialization.json)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockwebserver)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// The platform ships the Kotlin runtime, and a second copy inside a plugin is a documented source of
// class loading conflicts. `kotlin.stdlib.default.dependency=false` stops the Kotlin plugin from
// adding it, but kotlinx-serialization pulls it in transitively. An exclude on that dependency does
// not help: the serialization BOM creates further edges to the same module, and Gradle only drops a
// module when every path to it excludes it. The runtime classpath is also exactly what ends up in
// the plugin's lib folder, so this is the narrowest place that works.
configurations.runtimeClasspath {
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Stay within the stdlib API the target platform bundles.
        apiVersion = KotlinVersion.KOTLIN_2_1
        languageVersion = KotlinVersion.KOTLIN_2_1
    }
}

intellijPlatform {
    pluginConfiguration {
        // Shown on the "What's new" tab of the Marketplace listing and in the IDE's update dialog.
        // Describe this version only; the Marketplace keeps the history of earlier ones.
        changeNotes =
            """
            <h4>${project.version}</h4>
            <ul>
              <li>Create merge requests for all open projects in one dialog</li>
              <li>GitLab, GitHub, Gitea and Forgejo, hosted and self managed</li>
              <li>Filter, bulk branch selection and per repository overrides</li>
              <li>A failing repository never aborts the batch; failures can be retried</li>
            </ul>
            """.trimIndent()

        ideaVersion {
            sinceBuild = "253"
            // No upper bound: the plugin only uses long-term stable platform API.
            untilBuild = provider { null }
        }
    }

    pluginVerification {
        ides {
            recommended()
        }
    }

    // Optional. Without a key the Marketplace signs the plugin with its own certificate; signing it
    // yourself proves the upload came from you. Everything comes from the environment so no key
    // material can end up in the repository.
    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    // Only works once the plugin exists on the Marketplace; the first version has to be uploaded
    // through the website and pass moderation.
    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
        channels = listOf("default")
    }
}

spotless {
    // Without this Spotless uses the platform default, which contradicts end_of_line=lf in
    // .editorconfig and makes the check fail depending on who ran it.
    lineEndings = LineEnding.UNIX

    // Rules live in .editorconfig so the IDE and ktlint agree.
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint(libs.versions.ktlint.get())
    }
    format("misc") {
        target(
            "*.md",
            "*.properties",
            "src/**/*.properties",
            "src/**/*.xml",
            "gradle/*.toml",
            ".gitignore",
            "testenv/*.md",
        )
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.test {
    useJUnitPlatform()
}
