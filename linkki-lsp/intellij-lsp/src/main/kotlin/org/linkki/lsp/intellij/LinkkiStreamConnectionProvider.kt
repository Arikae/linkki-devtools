package org.linkki.lsp.intellij

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class LinkkiStreamConnectionProvider(private val project: Project) : StreamConnectionProvider {

    private val logger = Logger.getInstance(LinkkiStreamConnectionProvider::class.java)
    private var process: Process? = null

    override fun start() {
        val pluginId = PluginId.getId("org.linkki.lsp.intellij")
        val plugin = PluginManagerCore.getPlugin(pluginId)
            ?: throw IllegalStateException("Plugin not found: $pluginId")

        val serverDir = plugin.pluginPath.resolve("server").toFile()
        if (!serverDir.exists()) {
            throw IllegalStateException("Server directory not found: ${serverDir.absolutePath}")
        }

        val jarFile = serverDir.listFiles { _, name -> name.startsWith("lsp-server") && name.endsWith(".jar") }
            ?.firstOrNull()
            ?: throw IllegalStateException("LSP server JAR not found in $serverDir")

        val javaHome = System.getProperty("java.home")
        val javaExecutable = if (System.getProperty("os.name").lowercase().contains("win")) "java.exe" else "java"
        val javaBin = File(File(javaHome, "bin"), javaExecutable).absolutePath

        val command = listOf(
            javaBin,
            "-jar",
            jarFile.absolutePath
        )

        logger.info("Starting LSP server with command: $command")

        val builder = ProcessBuilder(command)
        process = builder.start()

        if (process?.isAlive != true) {
            throw IllegalStateException("LSP server failed to start")
        }

        // Consume stderr to prevent blocking and log it
        val stderr = process!!.errorStream
        Thread {
            try {
                stderr.bufferedReader().use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        logger.warn("[LSP Server] $line")
                        line = reader.readLine()
                    }
                }
            } catch (e: Exception) {
                logger.error("Error reading LSP server stderr", e)
            }
        }.start()
    }

    override fun stop() {
        process?.destroy()
        process = null
    }

    override fun getInputStream(): InputStream {
        return process?.inputStream ?: throw IllegalStateException("Server not started")
    }

    override fun getOutputStream(): OutputStream {
        return process?.outputStream ?: throw IllegalStateException("Server not started")
    }
}