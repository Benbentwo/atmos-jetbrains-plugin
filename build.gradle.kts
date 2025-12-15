import org.jetbrains.changelog.Changelog
import org.jetbrains.changelog.markdownToHTML

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.intellijPlatform)
    alias(libs.plugins.changelog)
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    // Note: kotlinx-coroutines is already provided by IntelliJ Platform
    implementation(libs.kotlinxSerializationJson)

    testImplementation(libs.junitJupiter)
    testImplementation(libs.junit4)
    testImplementation(libs.mockk)

    intellijPlatform {
        create(
            providers.gradleProperty("platformType"),
            providers.gradleProperty("platformVersion")
        )

        // Required plugin dependencies
        bundledPlugin("org.jetbrains.plugins.yaml")

        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)

        pluginVerifier()
    }
}

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")

        description = providers.provider {
            """
            First-class IDE support for <a href="https://atmos.tools/">Atmos</a> -
            the ultimate DevOps framework for Terraform, OpenTofu, Packer, and Helmfile orchestration.

            <h2>Features</h2>
            <ul>
                <li>Syntax highlighting for YAML functions (!env, !exec, !terraform.output, etc.)</li>
                <li>Navigation to imports, components, and variable definitions (Cmd+Click)</li>
                <li>Code completion for imports, components, and variables</li>
                <li>Inspections for missing imports, unknown components, and more</li>
                <li>Gutter icons for inheritance relationships</li>
                <li>Tool window for browsing stacks and components</li>
                <li>Run configurations for Atmos CLI commands</li>
            </ul>
            """.trimIndent()
        }

        changeNotes = providers.provider {
            changelog.renderItem(
                changelog.getOrNull(providers.gradleProperty("pluginVersion").get())
                    ?: changelog.getUnreleased(),
                Changelog.OutputType.HTML
            )
        }

        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey = providers.environmentVariable("PRIVATE_KEY")
        password = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }

    pluginVerification {
        ides {
            recommended()
        }
    }
}

changelog {
    groups.empty()
    repositoryUrl = providers.gradleProperty("pluginRepositoryUrl")
}

tasks {
    wrapper {
        gradleVersion = "8.13"
    }

    test {
        useJUnitPlatform()
    }
}
