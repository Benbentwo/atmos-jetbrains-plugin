package com.cloudposse.atmos.run

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.CardLayout
import java.awt.event.ItemEvent
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings editor UI for Atmos run configurations.
 */
class AtmosRunConfigurationEditor(private val project: Project) : SettingsEditor<AtmosRunConfiguration>() {

    private val commandTypeComboBox = ComboBox(AtmosCommandType.values())
    private val componentField = JBTextField()
    private val stackField = JBTextField()
    private val workflowField = JBTextField()
    private val customCommandField = JBTextField()
    private val additionalArgumentsField = JBTextField()
    private val workingDirectoryField = TextFieldWithBrowseButton()

    // Panels for different command types
    private val cardPanel = JPanel(CardLayout())
    private val terraformPanel: JPanel
    private val describeStacksPanel: JPanel
    private val describeComponentPanel: JPanel
    private val workflowPanel: JPanel
    private val customPanel: JPanel

    init {
        workingDirectoryField.addBrowseFolderListener(
            AtmosBundle.message("run.configuration.working.directory.title"),
            AtmosBundle.message("run.configuration.working.directory.description"),
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor()
        )

        // Create panels for different command types
        terraformPanel = createTerraformPanel()
        describeStacksPanel = createDescribeStacksPanel()
        describeComponentPanel = createDescribeComponentPanel()
        workflowPanel = createWorkflowPanel()
        customPanel = createCustomPanel()

        // Add panels to card layout
        cardPanel.add(terraformPanel, "terraform")
        cardPanel.add(describeStacksPanel, "describe_stacks")
        cardPanel.add(describeComponentPanel, "describe_component")
        cardPanel.add(workflowPanel, "workflow")
        cardPanel.add(customPanel, "custom")

        // Set up command type listener
        commandTypeComboBox.addItemListener { event ->
            if (event.stateChange == ItemEvent.SELECTED) {
                updateCardPanel()
            }
        }
    }

    private fun createTerraformPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.component")), componentField, 1, false)
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.stack")), stackField, 1, false)
            .panel
    }

    private fun createDescribeStacksPanel(): JPanel {
        val stackFilterField = JBTextField()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.stack.optional")), stackFilterField, 1, false)
            .panel
    }

    private fun createDescribeComponentPanel(): JPanel {
        val compField = JBTextField()
        val stkField = JBTextField()
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.component")), compField, 1, false)
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.stack")), stkField, 1, false)
            .panel
    }

    private fun createWorkflowPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.workflow.name")), workflowField, 1, false)
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.stack.optional")), stackField, 1, false)
            .panel
    }

    private fun createCustomPanel(): JPanel {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.custom.command")), customCommandField, 1, false)
            .panel
    }

    private fun updateCardPanel() {
        val cardLayout = cardPanel.layout as CardLayout
        when (commandTypeComboBox.selectedItem as AtmosCommandType) {
            AtmosCommandType.TERRAFORM_PLAN,
            AtmosCommandType.TERRAFORM_APPLY,
            AtmosCommandType.TERRAFORM_DESTROY,
            AtmosCommandType.TERRAFORM_INIT,
            AtmosCommandType.TERRAFORM_VALIDATE,
            AtmosCommandType.VALIDATE_COMPONENT -> cardLayout.show(cardPanel, "terraform")
            AtmosCommandType.DESCRIBE_STACKS,
            AtmosCommandType.VALIDATE_STACKS -> cardLayout.show(cardPanel, "describe_stacks")
            AtmosCommandType.DESCRIBE_COMPONENT -> cardLayout.show(cardPanel, "describe_component")
            AtmosCommandType.WORKFLOW -> cardLayout.show(cardPanel, "workflow")
            AtmosCommandType.CUSTOM -> cardLayout.show(cardPanel, "custom")
        }
    }

    override fun resetEditorFrom(configuration: AtmosRunConfiguration) {
        commandTypeComboBox.selectedItem = configuration.commandType
        componentField.text = configuration.component
        stackField.text = configuration.stack
        workflowField.text = configuration.workflowName
        customCommandField.text = configuration.customCommand
        additionalArgumentsField.text = configuration.additionalArguments
        workingDirectoryField.text = configuration.workingDirectory.ifBlank {
            project.basePath ?: ""
        }
        updateCardPanel()
    }

    override fun applyEditorTo(configuration: AtmosRunConfiguration) {
        configuration.commandType = commandTypeComboBox.selectedItem as AtmosCommandType
        configuration.component = componentField.text
        configuration.stack = stackField.text
        configuration.workflowName = workflowField.text
        configuration.customCommand = customCommandField.text
        configuration.additionalArguments = additionalArgumentsField.text
        configuration.workingDirectory = workingDirectoryField.text
    }

    override fun createEditor(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.command.type")), commandTypeComboBox, 1, false)
            .addComponent(cardPanel)
            .addSeparator()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.additional.arguments")), additionalArgumentsField, 1, false)
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.working.directory")), workingDirectoryField, 1, false)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
}
