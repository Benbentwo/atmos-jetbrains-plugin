package com.cloudposse.atmos.run

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.settings.AtmosSettings
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project

/**
 * Run configuration for Atmos CLI commands.
 */
class AtmosRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<AtmosRunConfigurationOptions>(project, factory, name) {

    override fun getOptions(): AtmosRunConfigurationOptions {
        return super.getOptions() as AtmosRunConfigurationOptions
    }

    var commandType: AtmosCommandType
        get() = options.commandType
        set(value) {
            options.commandType = value
        }

    var component: String
        get() = options.component
        set(value) {
            options.component = value
        }

    var stack: String
        get() = options.stack
        set(value) {
            options.stack = value
        }

    var workflowName: String
        get() = options.workflowName
        set(value) {
            options.workflowName = value
        }

    var additionalArguments: String
        get() = options.additionalArguments
        set(value) {
            options.additionalArguments = value
        }

    var customCommand: String
        get() = options.customCommand
        set(value) {
            options.customCommand = value
        }

    var workingDirectory: String
        get() = options.workingDirectory
        set(value) {
            options.workingDirectory = value
        }

    var environmentVariables: Map<String, String>
        get() = options.environmentVariables
        set(value) {
            options.environmentVariables = value
        }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return AtmosRunConfigurationEditor(project)
    }

    override fun checkConfiguration() {
        val atmosPath = AtmosSettings.getInstance().getEffectiveAtmosPath()
        if (atmosPath == null) {
            throw RuntimeConfigurationError(AtmosBundle.message("run.configuration.error.atmos.not.found"))
        }

        when (commandType) {
            AtmosCommandType.TERRAFORM_PLAN,
            AtmosCommandType.TERRAFORM_APPLY,
            AtmosCommandType.TERRAFORM_DESTROY,
            AtmosCommandType.TERRAFORM_INIT,
            AtmosCommandType.TERRAFORM_VALIDATE,
            AtmosCommandType.DESCRIBE_COMPONENT,
            AtmosCommandType.VALIDATE_COMPONENT -> {
                if (component.isBlank()) {
                    throw RuntimeConfigurationError(AtmosBundle.message("run.configuration.error.component.required"))
                }
                if (stack.isBlank()) {
                    throw RuntimeConfigurationError(AtmosBundle.message("run.configuration.error.stack.required"))
                }
            }
            AtmosCommandType.WORKFLOW -> {
                if (workflowName.isBlank()) {
                    throw RuntimeConfigurationError(AtmosBundle.message("run.configuration.error.workflow.required"))
                }
            }
            AtmosCommandType.CUSTOM -> {
                if (customCommand.isBlank()) {
                    throw RuntimeConfigurationError(AtmosBundle.message("run.configuration.error.custom.command.required"))
                }
            }
            AtmosCommandType.DESCRIBE_STACKS,
            AtmosCommandType.VALIDATE_STACKS -> {
                // No additional validation required
            }
        }
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return AtmosRunProfileState(this, environment)
    }

    /**
     * Builds the full command line for this configuration.
     */
    fun buildCommandLine(): List<String> {
        val commands = mutableListOf<String>()

        when (commandType) {
            AtmosCommandType.TERRAFORM_PLAN,
            AtmosCommandType.TERRAFORM_APPLY,
            AtmosCommandType.TERRAFORM_DESTROY,
            AtmosCommandType.TERRAFORM_INIT,
            AtmosCommandType.TERRAFORM_VALIDATE -> {
                commands.addAll(commandType.command.split(" "))
                commands.add(component)
                commands.add("-s")
                commands.add(stack)
            }
            AtmosCommandType.DESCRIBE_STACKS -> {
                commands.addAll(commandType.command.split(" "))
                if (stack.isNotBlank()) {
                    commands.add("--stack")
                    commands.add(stack)
                }
            }
            AtmosCommandType.DESCRIBE_COMPONENT -> {
                commands.addAll(commandType.command.split(" "))
                commands.add(component)
                commands.add("-s")
                commands.add(stack)
            }
            AtmosCommandType.VALIDATE_COMPONENT -> {
                commands.addAll(commandType.command.split(" "))
                commands.add(component)
                commands.add("-s")
                commands.add(stack)
            }
            AtmosCommandType.VALIDATE_STACKS -> {
                commands.addAll(commandType.command.split(" "))
            }
            AtmosCommandType.WORKFLOW -> {
                commands.add("workflow")
                commands.add(workflowName)
                if (stack.isNotBlank()) {
                    commands.add("-s")
                    commands.add(stack)
                }
            }
            AtmosCommandType.CUSTOM -> {
                commands.addAll(customCommand.split("\\s+".toRegex()))
            }
        }

        if (additionalArguments.isNotBlank()) {
            commands.addAll(additionalArguments.split("\\s+".toRegex()))
        }

        return commands
    }
}

/**
 * Options for Atmos run configuration (persisted state).
 */
class AtmosRunConfigurationOptions : RunConfigurationOptions() {
    private var _commandType: String = AtmosCommandType.TERRAFORM_PLAN.name
    private var _component: String = ""
    private var _stack: String = ""
    private var _workflowName: String = ""
    private var _additionalArguments: String = ""
    private var _customCommand: String = ""
    private var _workingDirectory: String = ""
    private var _environmentVariables: MutableMap<String, String> = mutableMapOf()

    var commandType: AtmosCommandType
        get() = try {
            AtmosCommandType.valueOf(_commandType)
        } catch (e: IllegalArgumentException) {
            AtmosCommandType.TERRAFORM_PLAN
        }
        set(value) {
            _commandType = value.name
        }

    var component: String
        get() = _component
        set(value) {
            _component = value
        }

    var stack: String
        get() = _stack
        set(value) {
            _stack = value
        }

    var workflowName: String
        get() = _workflowName
        set(value) {
            _workflowName = value
        }

    var additionalArguments: String
        get() = _additionalArguments
        set(value) {
            _additionalArguments = value
        }

    var customCommand: String
        get() = _customCommand
        set(value) {
            _customCommand = value
        }

    var workingDirectory: String
        get() = _workingDirectory
        set(value) {
            _workingDirectory = value
        }

    var environmentVariables: Map<String, String>
        get() = _environmentVariables
        set(value) {
            _environmentVariables = value.toMutableMap()
        }
}

/**
 * Run profile state for executing Atmos commands.
 */
class AtmosRunProfileState(
    private val configuration: AtmosRunConfiguration,
    private val environment: ExecutionEnvironment
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val atmosPath = AtmosSettings.getInstance().getEffectiveAtmosPath()
            ?: throw RuntimeConfigurationError(AtmosBundle.message("run.configuration.error.atmos.not.found"))

        val commandLine = GeneralCommandLine(atmosPath)
            .withParameters(configuration.buildCommandLine())
            .withWorkDirectory(getWorkingDirectory())
            .withEnvironment(configuration.environmentVariables)
            .withCharset(Charsets.UTF_8)

        val handler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }

    private fun getWorkingDirectory(): String {
        val workDir = configuration.workingDirectory
        if (workDir.isNotBlank()) {
            return workDir
        }
        return environment.project.basePath ?: System.getProperty("user.dir")
    }
}
