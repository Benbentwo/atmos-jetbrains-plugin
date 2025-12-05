package com.cloudposse.atmos.run

import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.settings.AtmosSettings
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import org.jdom.Element
import java.io.File
import javax.swing.Icon

/**
 * Run configuration for Atmos CLI commands.
 */
class AtmosRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<AtmosRunConfigurationOptions>(project, factory, name) {

    var commandType: AtmosCommandType = AtmosCommandType.TERRAFORM_PLAN
    var component: String = ""
    var stack: String = ""
    var workflowName: String = ""
    var customCommand: String = ""
    var additionalArguments: String = ""

    override fun getOptions(): AtmosRunConfigurationOptions {
        return super.getOptions() as AtmosRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return AtmosRunConfigurationEditor(project)
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return AtmosRunProfileState(this, environment)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        commandType = AtmosCommandType.valueOf(
            JDOMExternalizerUtil.readField(element, "commandType") ?: AtmosCommandType.TERRAFORM_PLAN.name
        )
        component = JDOMExternalizerUtil.readField(element, "component") ?: ""
        stack = JDOMExternalizerUtil.readField(element, "stack") ?: ""
        workflowName = JDOMExternalizerUtil.readField(element, "workflowName") ?: ""
        customCommand = JDOMExternalizerUtil.readField(element, "customCommand") ?: ""
        additionalArguments = JDOMExternalizerUtil.readField(element, "additionalArguments") ?: ""
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, "commandType", commandType.name)
        JDOMExternalizerUtil.writeField(element, "component", component)
        JDOMExternalizerUtil.writeField(element, "stack", stack)
        JDOMExternalizerUtil.writeField(element, "workflowName", workflowName)
        JDOMExternalizerUtil.writeField(element, "customCommand", customCommand)
        JDOMExternalizerUtil.writeField(element, "additionalArguments", additionalArguments)
    }

    override fun checkConfiguration() {
        when (commandType) {
            AtmosCommandType.TERRAFORM_PLAN,
            AtmosCommandType.TERRAFORM_APPLY,
            AtmosCommandType.TERRAFORM_DESTROY,
            AtmosCommandType.DESCRIBE_COMPONENT,
            AtmosCommandType.VALIDATE_COMPONENT -> {
                if (component.isBlank()) {
                    throw RuntimeConfigurationError("Component is required")
                }
                if (stack.isBlank()) {
                    throw RuntimeConfigurationError("Stack is required")
                }
            }
            AtmosCommandType.WORKFLOW -> {
                if (workflowName.isBlank()) {
                    throw RuntimeConfigurationError("Workflow name is required")
                }
            }
            AtmosCommandType.CUSTOM -> {
                if (customCommand.isBlank()) {
                    throw RuntimeConfigurationError("Custom command is required")
                }
            }
            AtmosCommandType.DESCRIBE_STACKS -> {
                // No additional requirements
            }
        }

        if (AtmosSettings.getInstance().getEffectiveAtmosPath() == null) {
            throw RuntimeConfigurationWarning("Atmos executable not found. Please configure it in Settings > Tools > Atmos.")
        }
    }

    /**
     * Builds the command line arguments for this configuration.
     */
    fun buildCommandLineArgs(): List<String> {
        val args = mutableListOf<String>()

        when (commandType) {
            AtmosCommandType.TERRAFORM_PLAN -> {
                args.addAll(listOf("terraform", "plan", component, "-s", stack))
            }
            AtmosCommandType.TERRAFORM_APPLY -> {
                args.addAll(listOf("terraform", "apply", component, "-s", stack))
            }
            AtmosCommandType.TERRAFORM_DESTROY -> {
                args.addAll(listOf("terraform", "destroy", component, "-s", stack))
            }
            AtmosCommandType.DESCRIBE_STACKS -> {
                args.addAll(listOf("describe", "stacks"))
            }
            AtmosCommandType.DESCRIBE_COMPONENT -> {
                args.addAll(listOf("describe", "component", component, "-s", stack))
            }
            AtmosCommandType.VALIDATE_COMPONENT -> {
                args.addAll(listOf("validate", "component", component, "-s", stack))
            }
            AtmosCommandType.WORKFLOW -> {
                args.addAll(listOf("workflow", workflowName))
            }
            AtmosCommandType.CUSTOM -> {
                args.addAll(customCommand.split("\\s+".toRegex()))
            }
        }

        if (additionalArguments.isNotBlank()) {
            args.addAll(additionalArguments.split("\\s+".toRegex()))
        }

        return args
    }
}

/**
 * Run profile state for Atmos command execution.
 */
class AtmosRunProfileState(
    private val configuration: AtmosRunConfiguration,
    private val environment: ExecutionEnvironment
) : CommandLineState(environment) {

    override fun startProcess(): OSProcessHandler {
        val atmosPath = AtmosSettings.getInstance().getEffectiveAtmosPath()
            ?: throw RuntimeConfigurationError("Atmos executable not found")

        val workingDir = environment.project.basePath?.let { File(it) }
            ?: throw RuntimeConfigurationError("Project working directory not found")

        val commandLine = GeneralCommandLine(atmosPath)
            .withParameters(configuration.buildCommandLineArgs())
            .withWorkDirectory(workingDir)
            .withCharset(Charsets.UTF_8)
            .withEnvironment("TERM", "xterm-256color")

        val handler = OSProcessHandler(commandLine)
        ProcessTerminatedListener.attach(handler)
        return handler
    }
}

/**
 * Options class for Atmos run configuration persistence.
 */
class AtmosRunConfigurationOptions : RunConfigurationOptions()
