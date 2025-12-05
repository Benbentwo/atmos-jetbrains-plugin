package com.cloudposse.atmos.cli

import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.settings.AtmosSettings
import com.intellij.execution.Executor
import com.intellij.execution.configurations.*
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMExternalizerUtil
import com.intellij.openapi.util.NlsActions
import org.jdom.Element
import javax.swing.*

/**
 * Run configuration type for Atmos CLI commands.
 */
class AtmosRunConfigurationType : ConfigurationType {
    override fun getDisplayName(): String = "Atmos"
    override fun getConfigurationTypeDescription(): String = "Run Atmos CLI commands"
    override fun getIcon(): Icon = AtmosIcons.ATMOS
    override fun getId(): String = "AtmosRunConfiguration"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(AtmosConfigurationFactory(this))
    }

    companion object {
        fun getInstance(): AtmosRunConfigurationType {
            return ConfigurationTypeUtil.findConfigurationType(AtmosRunConfigurationType::class.java)
        }
    }
}

/**
 * Factory for creating Atmos run configurations.
 */
class AtmosConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {
    override fun getId(): String = "AtmosConfigurationFactory"
    override fun getName(): String = "Atmos"

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return AtmosRunConfiguration(project, this, "Atmos")
    }
}

/**
 * Run configuration for executing Atmos CLI commands.
 */
class AtmosRunConfiguration(
    project: Project,
    factory: ConfigurationFactory,
    name: String
) : RunConfigurationBase<AtmosRunConfigurationOptions>(project, factory, name) {

    var command: AtmosCommand = AtmosCommand.DESCRIBE_STACKS
    var componentName: String = ""
    var stackName: String = ""
    var additionalArgs: String = ""

    override fun getOptions(): AtmosRunConfigurationOptions {
        return super.getOptions() as AtmosRunConfigurationOptions
    }

    override fun getConfigurationEditor(): SettingsEditor<out RunConfiguration> {
        return AtmosRunConfigurationEditor()
    }

    override fun getState(executor: Executor, environment: ExecutionEnvironment): RunProfileState {
        return AtmosRunProfileState(this, environment)
    }

    override fun readExternal(element: Element) {
        super.readExternal(element)
        command = AtmosCommand.valueOf(
            JDOMExternalizerUtil.readField(element, "command", AtmosCommand.DESCRIBE_STACKS.name)
        )
        componentName = JDOMExternalizerUtil.readField(element, "componentName", "")
        stackName = JDOMExternalizerUtil.readField(element, "stackName", "")
        additionalArgs = JDOMExternalizerUtil.readField(element, "additionalArgs", "")
    }

    override fun writeExternal(element: Element) {
        super.writeExternal(element)
        JDOMExternalizerUtil.writeField(element, "command", command.name)
        JDOMExternalizerUtil.writeField(element, "componentName", componentName)
        JDOMExternalizerUtil.writeField(element, "stackName", stackName)
        JDOMExternalizerUtil.writeField(element, "additionalArgs", additionalArgs)
    }

    fun buildCommandLine(): List<String> {
        val settings = AtmosSettings.getInstance()
        val atmosPath = settings.atmosExecutablePath.ifEmpty { "atmos" }

        val args = mutableListOf(atmosPath)

        when (command) {
            AtmosCommand.TERRAFORM_PLAN -> {
                args.addAll(listOf("terraform", "plan", componentName))
                if (stackName.isNotEmpty()) {
                    args.addAll(listOf("-s", stackName))
                }
            }
            AtmosCommand.TERRAFORM_APPLY -> {
                args.addAll(listOf("terraform", "apply", componentName))
                if (stackName.isNotEmpty()) {
                    args.addAll(listOf("-s", stackName))
                }
            }
            AtmosCommand.DESCRIBE_STACKS -> {
                args.addAll(listOf("describe", "stacks"))
                if (stackName.isNotEmpty()) {
                    args.addAll(listOf("--stack", stackName))
                }
            }
            AtmosCommand.DESCRIBE_COMPONENT -> {
                args.addAll(listOf("describe", "component", componentName))
                if (stackName.isNotEmpty()) {
                    args.addAll(listOf("-s", stackName))
                }
            }
            AtmosCommand.VALIDATE_COMPONENT -> {
                args.addAll(listOf("validate", "component", componentName))
                if (stackName.isNotEmpty()) {
                    args.addAll(listOf("-s", stackName))
                }
            }
            AtmosCommand.WORKFLOW -> {
                args.addAll(listOf("workflow", componentName))
            }
            AtmosCommand.DESCRIBE_AFFECTED -> {
                args.addAll(listOf("describe", "affected"))
            }
            AtmosCommand.VERSION -> {
                args.add("version")
            }
        }

        if (additionalArgs.isNotEmpty()) {
            args.addAll(additionalArgs.split(" ").filter { it.isNotBlank() })
        }

        return args
    }
}

/**
 * Available Atmos commands.
 */
enum class AtmosCommand(val displayName: String) {
    TERRAFORM_PLAN("terraform plan"),
    TERRAFORM_APPLY("terraform apply"),
    DESCRIBE_STACKS("describe stacks"),
    DESCRIBE_COMPONENT("describe component"),
    VALIDATE_COMPONENT("validate component"),
    WORKFLOW("workflow"),
    DESCRIBE_AFFECTED("describe affected"),
    VERSION("version")
}

/**
 * Options class for Atmos run configuration.
 */
class AtmosRunConfigurationOptions : RunConfigurationOptions()

/**
 * Run profile state for executing Atmos commands.
 */
class AtmosRunProfileState(
    private val configuration: AtmosRunConfiguration,
    private val environment: ExecutionEnvironment
) : CommandLineState(environment) {

    override fun startProcess(): ProcessHandler {
        val commandLine = GeneralCommandLine(configuration.buildCommandLine())
        commandLine.setWorkDirectory(configuration.project.basePath)

        val processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
        ProcessTerminatedListener.attach(processHandler)
        return processHandler
    }
}

/**
 * Settings editor for Atmos run configuration.
 */
class AtmosRunConfigurationEditor : SettingsEditor<AtmosRunConfiguration>() {

    private val commandComboBox = JComboBox(AtmosCommand.values())
    private val componentField = JTextField()
    private val stackField = JTextField()
    private val additionalArgsField = JTextField()

    override fun resetEditorFrom(configuration: AtmosRunConfiguration) {
        commandComboBox.selectedItem = configuration.command
        componentField.text = configuration.componentName
        stackField.text = configuration.stackName
        additionalArgsField.text = configuration.additionalArgs
    }

    override fun applyEditorTo(configuration: AtmosRunConfiguration) {
        configuration.command = commandComboBox.selectedItem as AtmosCommand
        configuration.componentName = componentField.text
        configuration.stackName = stackField.text
        configuration.additionalArgs = additionalArgsField.text
    }

    override fun createEditor(): JComponent {
        val panel = JPanel()
        panel.layout = BoxLayout(panel, BoxLayout.Y_AXIS)

        // Command selector
        val commandPanel = JPanel()
        commandPanel.layout = BoxLayout(commandPanel, BoxLayout.X_AXIS)
        commandPanel.add(JLabel("Command: "))
        commandPanel.add(commandComboBox)
        commandPanel.add(Box.createHorizontalGlue())
        panel.add(commandPanel)

        // Component name
        val componentPanel = JPanel()
        componentPanel.layout = BoxLayout(componentPanel, BoxLayout.X_AXIS)
        componentPanel.add(JLabel("Component: "))
        componentPanel.add(componentField)
        panel.add(componentPanel)

        // Stack name
        val stackPanel = JPanel()
        stackPanel.layout = BoxLayout(stackPanel, BoxLayout.X_AXIS)
        stackPanel.add(JLabel("Stack: "))
        stackPanel.add(stackField)
        panel.add(stackPanel)

        // Additional args
        val argsPanel = JPanel()
        argsPanel.layout = BoxLayout(argsPanel, BoxLayout.X_AXIS)
        argsPanel.add(JLabel("Additional Args: "))
        argsPanel.add(additionalArgsField)
        panel.add(argsPanel)

        return panel
    }
}
