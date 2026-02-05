plugins {
    id("org.jetbrains.intellij.platform") version "2.10.5"
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Ensure the JVM toolchain is compatible with IntelliJ 2025.3 (requires Java 17+)
kotlin {
    jvmToolchain(21)
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.3")
        bundledPlugin("org.jetbrains.kotlin")
        // Dependency on the LSP4IJ plugin
        plugin("com.redhat.devtools.lsp4ij:0.14.2")
    }

    // Exclude lsp4j to avoid ClassCastException, as LSP4IJ provides its own version
    configurations.all {
        exclude(group = "org.eclipse.lsp4j")
    }
}

intellijPlatform {
    pluginConfiguration {
        id.set("org.linkki.lsp.intellij")
        name.set("Linkki Tooling Support")

        ideaVersion {
            sinceBuild.set("252.25557")
            untilBuild.set(provider { null })
        }
    }
    buildSearchableOptions.set(false)
}

// Logic to locate the server JAR
val lspPath = project.findProperty("lspPath") as? String
    ?: file("../lsp-server/target").listFiles { _, name ->
        name.startsWith("lsp-server") && name.endsWith(".jar")
    }?.firstOrNull()?.absolutePath

tasks {
    prepareSandbox {
        doLast {
            val lspJarPath =
                lspPath ?: throw GradleException("LSP Path not found. Run 'mvn package' in lsp-server first.")
            val sourceFile = file(lspJarPath)

            // Fix: Use .get() on project.name to obtain the String value
            val pluginFolderName = project.name
            val pluginFolder = destinationDir.resolve(pluginFolderName)
            val serverDir = pluginFolder.resolve("server")

            println(">>> LSP SETUP: Copying ${sourceFile.name}...")
            println("    Source: ${sourceFile.absolutePath}")
            println("    Target: ${serverDir.absolutePath}")

            copy {
                from(sourceFile)
                into(serverDir)
            }

            if (serverDir.resolve(sourceFile.name).exists()) {
                println(">>> LSP SETUP: Success! Server JAR copied.")
            } else {
                throw GradleException(">>> LSP SETUP: Failed to copy JAR to ${serverDir.absolutePath}")
            }
        }
    }
}