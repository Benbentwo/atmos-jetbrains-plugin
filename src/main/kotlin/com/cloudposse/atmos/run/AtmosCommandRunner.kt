package com.cloudposse.atmos.run

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.settings.AtmosSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.*
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Utility class for running Atmos CLI commands.
 */
object AtmosCommandRunner {

    private val LOG = Logger.getInstance(AtmosCommandRunner::class.java)

    /**
     * Result of an Atmos command execution.
     */
    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
        val success: Boolean = exitCode == 0
    )

    /**
     * Runs an Atmos command synchronously and returns the result.
     *
     * @param project The project context
     * @param arguments The command arguments (without 'atmos' prefix)
     * @param workingDirectory Optional working directory
     * @param timeoutSeconds Timeout in seconds (default 60)
     * @return CommandResult containing exit code and output
     */
    fun runCommand(
        project: Project,
        arguments: List<String>,
        workingDirectory: String? = null,
        timeoutSeconds: Long = 60
    ): CommandResult {
        val atmosPath = AtmosSettings.getInstance().getEffectiveAtmosPath()
            ?: return CommandResult(-1, "", AtmosBundle.message("run.configuration.error.atmos.not.found"), false)

        val commandLine = GeneralCommandLine(atmosPath)
            .withParameters(arguments)
            .withWorkDirectory(workingDirectory ?: project.basePath)
            .withCharset(Charsets.UTF_8)

        LOG.info("Running Atmos command: ${commandLine.commandLineString}")

        return try {
            val process = commandLine.createProcess()
            val stdout = StringBuilder()
            val stderr = StringBuilder()

            val stdoutReader = Thread {
                process.inputStream.bufferedReader().forEachLine { stdout.appendLine(it) }
            }
            val stderrReader = Thread {
                process.errorStream.bufferedReader().forEachLine { stderr.appendLine(it) }
            }

            stdoutReader.start()
            stderrReader.start()

            val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)

            if (!completed) {
                process.destroyForcibly()
                return CommandResult(-1, stdout.toString(), "Command timed out after $timeoutSeconds seconds", false)
            }

            stdoutReader.join(1000)
            stderrReader.join(1000)

            CommandResult(
                exitCode = process.exitValue(),
                stdout = stdout.toString(),
                stderr = stderr.toString()
            )
        } catch (e: Exception) {
            LOG.error("Failed to run Atmos command", e)
            CommandResult(-1, "", "Failed to execute command: ${e.message}", false)
        }
    }

    /**
     * Runs an Atmos command asynchronously.
     *
     * @param project The project context
     * @param arguments The command arguments (without 'atmos' prefix)
     * @param workingDirectory Optional working directory
     * @param timeoutSeconds Timeout in seconds (default 60)
     * @return CompletableFuture with CommandResult
     */
    fun runCommandAsync(
        project: Project,
        arguments: List<String>,
        workingDirectory: String? = null,
        timeoutSeconds: Long = 60
    ): CompletableFuture<CommandResult> {
        return CompletableFuture.supplyAsync {
            runCommand(project, arguments, workingDirectory, timeoutSeconds)
        }
    }

    /**
     * Runs an Atmos command with output streamed to a console view.
     *
     * @param project The project context
     * @param arguments The command arguments (without 'atmos' prefix)
     * @param console The console view to write output to
     * @param workingDirectory Optional working directory
     * @return ProcessHandler for the running process
     */
    fun runCommandWithConsole(
        project: Project,
        arguments: List<String>,
        console: ConsoleView,
        workingDirectory: String? = null
    ): ProcessHandler? {
        val atmosPath = AtmosSettings.getInstance().getEffectiveAtmosPath()
        if (atmosPath == null) {
            console.print(
                AtmosBundle.message("run.configuration.error.atmos.not.found") + "\n",
                ConsoleViewContentType.ERROR_OUTPUT
            )
            return null
        }

        val commandLine = GeneralCommandLine(atmosPath)
            .withParameters(arguments)
            .withWorkDirectory(workingDirectory ?: project.basePath)
            .withCharset(Charsets.UTF_8)

        console.print("Running: ${commandLine.commandLineString}\n\n", ConsoleViewContentType.SYSTEM_OUTPUT)

        return try {
            val handler = ColoredProcessHandler(commandLine)
            ProcessTerminatedListener.attach(handler)
            console.attachToProcess(handler)
            handler.startNotify()
            handler
        } catch (e: Exception) {
            LOG.error("Failed to start Atmos command", e)
            console.print("Error: ${e.message}\n", ConsoleViewContentType.ERROR_OUTPUT)
            null
        }
    }

    /**
     * Runs `atmos describe component` and returns the output.
     */
    fun describeComponent(
        project: Project,
        component: String,
        stack: String
    ): CommandResult {
        return runCommand(project, listOf("describe", "component", component, "-s", stack))
    }

    /**
     * Runs `atmos describe stacks` and returns the output.
     */
    fun describeStacks(
        project: Project,
        stack: String? = null
    ): CommandResult {
        val args = mutableListOf("describe", "stacks")
        if (stack != null) {
            args.addAll(listOf("--stack", stack))
        }
        return runCommand(project, args)
    }

    /**
     * Runs `atmos validate component` and returns the output.
     */
    fun validateComponent(
        project: Project,
        component: String,
        stack: String
    ): CommandResult {
        return runCommand(project, listOf("validate", "component", component, "-s", stack))
    }

    /**
     * Runs `atmos validate stacks` and returns the output.
     */
    fun validateStacks(project: Project): CommandResult {
        return runCommand(project, listOf("validate", "stacks"))
    }

    /**
     * Runs `atmos list stacks` and returns the output.
     */
    fun listStacks(project: Project): CommandResult {
        return runCommand(project, listOf("list", "stacks"))
    }

    /**
     * Runs `atmos list components` and returns the output.
     */
    fun listComponents(project: Project): CommandResult {
        return runCommand(project, listOf("list", "components"))
    }

    /**
     * Runs `atmos describe config` and returns the output.
     */
    fun describeConfig(project: Project): CommandResult {
        return runCommand(project, listOf("describe", "config"))
    }

    /**
     * Gets the atmos version.
     */
    fun getVersion(project: Project): String? {
        val result = runCommand(project, listOf("version"), timeoutSeconds = 10)
        return if (result.success) result.stdout.trim() else null
    }
}
