package com.cloudposse.atmos.services

import com.cloudposse.atmos.settings.AtmosSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import java.io.File
import java.util.concurrent.CompletableFuture

/**
 * Service for executing Atmos CLI commands.
 */
@Service(Service.Level.PROJECT)
class AtmosCommandRunner(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(AtmosCommandRunner::class.java)

        @JvmStatic
        fun getInstance(project: Project): AtmosCommandRunner = project.service()
    }

    /**
     * Result of an Atmos command execution.
     */
    data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
        val output: String get() = stdout.ifBlank { stderr }
    }

    /**
     * Executes an Atmos command and returns the result.
     */
    fun executeCommand(vararg args: String): CompletableFuture<CommandResult> {
        val future = CompletableFuture<CommandResult>()

        val atmosPath = AtmosSettings.getInstance().getEffectiveAtmosPath()
        if (atmosPath == null) {
            future.completeExceptionally(
                IllegalStateException("Atmos executable not found. Please configure it in Settings > Tools > Atmos.")
            )
            return future
        }

        val workingDir = project.basePath?.let { File(it) }
        if (workingDir == null || !workingDir.exists()) {
            future.completeExceptionally(
                IllegalStateException("Project working directory not found.")
            )
            return future
        }

        try {
            val commandLine = GeneralCommandLine(atmosPath)
                .withParameters(*args)
                .withWorkDirectory(workingDir)
                .withCharset(Charsets.UTF_8)

            LOG.info("Executing Atmos command: ${commandLine.commandLineString}")

            val stdoutBuilder = StringBuilder()
            val stderrBuilder = StringBuilder()

            val handler = OSProcessHandler(commandLine)
            handler.addProcessListener(object : ProcessAdapter() {
                override fun onTextAvailable(event: ProcessEvent, outputType: Key<*>) {
                    when (outputType) {
                        ProcessOutputType.STDOUT -> stdoutBuilder.append(event.text)
                        ProcessOutputType.STDERR -> stderrBuilder.append(event.text)
                    }
                }

                override fun processTerminated(event: ProcessEvent) {
                    val result = CommandResult(
                        exitCode = event.exitCode,
                        stdout = stdoutBuilder.toString(),
                        stderr = stderrBuilder.toString()
                    )
                    LOG.info("Atmos command finished with exit code: ${result.exitCode}")
                    future.complete(result)
                }
            })

            handler.startNotify()
        } catch (e: Exception) {
            LOG.error("Failed to execute Atmos command", e)
            future.completeExceptionally(e)
        }

        return future
    }

    /**
     * Executes an Atmos command synchronously (blocking).
     * @param args the command arguments
     * @param timeoutMs timeout in milliseconds (currently unused, reserved for future use)
     */
    @Suppress("UNUSED_PARAMETER")
    fun executeCommandSync(vararg args: String, timeoutMs: Long = 30000): CommandResult {
        return executeCommand(*args).get()
    }

    /**
     * Runs 'atmos describe stacks' and returns the raw JSON output.
     */
    fun describeStacks(): CompletableFuture<CommandResult> {
        return executeCommand("describe", "stacks", "--format", "json")
    }

    /**
     * Runs 'atmos describe component' for a specific component and stack.
     */
    fun describeComponent(component: String, stack: String): CompletableFuture<CommandResult> {
        return executeCommand("describe", "component", component, "-s", stack, "--format", "json")
    }

    /**
     * Runs 'atmos validate component' for a specific component and stack.
     */
    fun validateComponent(component: String, stack: String): CompletableFuture<CommandResult> {
        return executeCommand("validate", "component", component, "-s", stack)
    }

    /**
     * Runs 'atmos terraform plan' for a component and stack.
     */
    fun terraformPlan(component: String, stack: String): CompletableFuture<CommandResult> {
        return executeCommand("terraform", "plan", component, "-s", stack)
    }

    /**
     * Runs 'atmos terraform apply' for a component and stack.
     */
    fun terraformApply(component: String, stack: String): CompletableFuture<CommandResult> {
        return executeCommand("terraform", "apply", component, "-s", stack)
    }

    /**
     * Runs 'atmos list stacks'.
     */
    fun listStacks(): CompletableFuture<CommandResult> {
        return executeCommand("list", "stacks")
    }

    /**
     * Runs 'atmos list components'.
     */
    fun listComponents(): CompletableFuture<CommandResult> {
        return executeCommand("list", "components")
    }

    /**
     * Runs 'atmos workflow' with the specified workflow name.
     */
    fun runWorkflow(workflowName: String): CompletableFuture<CommandResult> {
        return executeCommand("workflow", workflowName)
    }

    /**
     * Runs 'atmos version' to check if Atmos is properly installed.
     */
    fun version(): CompletableFuture<CommandResult> {
        return executeCommand("version")
    }
}
