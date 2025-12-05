package com.cloudposse.atmos.run

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import java.awt.CardLayout
import javax.swing.*

/**
 * Settings editor for Atmos run configurations.
 */
class AtmosRunConfigurationEditor(private val project: Project) : SettingsEditor<AtmosRunConfiguration>() {

    private val commandTypeCombo = ComboBox(AtmosCommandType.values().map { it.displayName }.toTypedArray())
    private val componentField = JBTextField()
    private val stackField = JBTextField()
    private val workflowField = JBTextField()
    private val customCommandField = JBTextField()
    private val additionalArgsField = JBTextField()

    private val cardLayout = CardLayout()
    private val cardsPanel = JPanel(cardLayout)

    private val componentStackPanel: JPanel
    private val workflowPanel: JPanel
    private val customPanel: JPanel
    private val emptyPanel: JPanel

    init {
        // Create panels for different command types
        componentStackPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.component")), componentField)
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.stack")), stackField)
            .panel

        workflowPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.workflow")), workflowField)
            .panel

        customPanel = FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.custom.command")), customCommandField)
            .panel

        emptyPanel = JPanel()

        cardsPanel.add(componentStackPanel, "COMPONENT_STACK")
        cardsPanel.add(workflowPanel, "WORKFLOW")
        cardsPanel.add(customPanel, "CUSTOM")
        cardsPanel.add(emptyPanel, "EMPTY")

        // Update visible panel when command type changes
        commandTypeCombo.addActionListener {
            updateVisiblePanel()
        }

        // Populate component/stack suggestions if available
        populateSuggestions()
    }

    private fun updateVisiblePanel() {
        val selectedType = AtmosCommandType.fromDisplayName(commandTypeCombo.selectedItem as String)
        when (selectedType) {
            AtmosCommandType.TERRAFORM_PLAN,
            AtmosCommandType.TERRAFORM_APPLY,
            AtmosCommandType.TERRAFORM_DESTROY,
            AtmosCommandType.DESCRIBE_COMPONENT,
            AtmosCommandType.VALIDATE_COMPONENT -> cardLayout.show(cardsPanel, "COMPONENT_STACK")
            AtmosCommandType.WORKFLOW -> cardLayout.show(cardsPanel, "WORKFLOW")
            AtmosCommandType.CUSTOM -> cardLayout.show(cardsPanel, "CUSTOM")
            AtmosCommandType.DESCRIBE_STACKS -> cardLayout.show(cardsPanel, "EMPTY")
        }
    }

    private fun populateSuggestions() {
        // Could add auto-completion for components/stacks from the project
        // For now, leave as free text fields
    }

    override fun resetEditorFrom(config: AtmosRunConfiguration) {
        commandTypeCombo.selectedItem = config.commandType.displayName
        componentField.text = config.component
        stackField.text = config.stack
        workflowField.text = config.workflowName
        customCommandField.text = config.customCommand
        additionalArgsField.text = config.additionalArguments
        updateVisiblePanel()
    }

    override fun applyEditorTo(config: AtmosRunConfiguration) {
        config.commandType = AtmosCommandType.fromDisplayName(commandTypeCombo.selectedItem as String)
        config.component = componentField.text
        config.stack = stackField.text
        config.workflowName = workflowField.text
        config.customCommand = customCommandField.text
        config.additionalArguments = additionalArgsField.text
    }

    override fun createEditor(): JComponent {
        return FormBuilder.createFormBuilder()
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.command.type")), commandTypeCombo)
            .addComponent(cardsPanel)
            .addLabeledComponent(JBLabel(AtmosBundle.message("run.configuration.additional.args")), additionalArgsField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }
}
