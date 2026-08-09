import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.serialization") version "2.3.21"
    id("org.jetbrains.intellij.platform") version "2.18.1"
    id("com.diffplug.spotless") version "8.9.0"
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
        intellijIdea("2025.3.6.1")
        bundledPlugin("Git4Idea")
        testFramework(TestFrameworkType.Platform)
    }

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0") {
        // The platform provides the Kotlin runtime.
        exclude(group = "org.jetbrains.kotlin")
    }

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        // Stay within the stdlib API the target platform bundles.
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
    }
}

intellijPlatform {
    pluginConfiguration {
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
}

spotless {
    // Without this Spotless uses the platform default, which contradicts end_of_line=lf
    // in .editorconfig and makes the check fail depending on who ran it.
    lineEndings = com.diffplug.spotless.LineEnding.UNIX

    // Rules live in .editorconfig so the IDE and ktlint agree.
    kotlin {
        target("src/**/*.kt")
        ktlint("1.8.0")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        target("*.gradle.kts")
        ktlint("1.8.0")
    }
    format("misc") {
        target("*.md", "*.properties", "src/**/*.properties", "src/**/*.xml", ".gitignore", "testenv/*.md")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.test {
    useJUnitPlatform()
}
