package com.cloudposse.atmos.settings

import com.cloudposse.atmos.AtmosBundle
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.*
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings configurable for the Atmos plugin.
 * Accessible via Settings > Tools > Atmos
 */
class AtmosSettingsConfigurable : Configurable {

    private var mainPanel: JPanel? = null
    private var atmosPathField: TextFieldWithBrowseButton? = null
    private var validateOnSaveCheckbox: JBCheckBox? = null
    private var showResolvedValuesCheckbox: JBCheckBox? = null

    override fun getDisplayName(): String = "Atmos"

    override fun createComponent(): JComponent {
        val settings = AtmosSettings.getInstance()

        atmosPathField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "Select Atmos Executable",
                "Choose the path to the atmos executable",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor()
            )
        }

        validateOnSaveCheckbox = JBCheckBox("Validate on save", settings.validateOnSave)
        showResolvedValuesCheckbox = JBCheckBox("Show resolved values inline", settings.showResolvedValuesInline)

        mainPanel = panel {
            group("Atmos Executable") {
                row("Executable path:") {
                    cell(atmosPathField!!)
                        .resizableColumn()
                        .align(Align.FILL)
                        .comment("Leave empty for auto-detection")
                }
                row {
                    val detectedPath = settings.autoDetectAtmosPath()
                    if (detectedPath != null) {
                        label("Auto-detected: $detectedPath")
                            .applyToComponent {
                                foreground = java.awt.Color.GRAY
                            }
                    } else {
                        label("Auto-detection: atmos not found in PATH")
                            .applyToComponent {
                                foreground = java.awt.Color.ORANGE
                            }
                    }
                }
            }

            group("Editor") {
                row {
                    cell(showResolvedValuesCheckbox!!)
                        .comment("Display resolved values as inline hints (Phase 6 feature)")
                }
            }

            group("Validation") {
                row {
                    cell(validateOnSaveCheckbox!!)
                        .comment("Run atmos validate when saving stack files")
                }
            }
        }

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = AtmosSettings.getInstance()
        return atmosPathField?.text != settings.atmosExecutablePath ||
                validateOnSaveCheckbox?.isSelected != settings.validateOnSave ||
                showResolvedValuesCheckbox?.isSelected != settings.showResolvedValuesInline
    }

    override fun apply() {
        val settings = AtmosSettings.getInstance()
        settings.atmosExecutablePath = atmosPathField?.text ?: ""
        settings.validateOnSave = validateOnSaveCheckbox?.isSelected ?: false
        settings.showResolvedValuesInline = showResolvedValuesCheckbox?.isSelected ?: true
    }

    override fun reset() {
        val settings = AtmosSettings.getInstance()
        atmosPathField?.text = settings.atmosExecutablePath
        validateOnSaveCheckbox?.isSelected = settings.validateOnSave
        showResolvedValuesCheckbox?.isSelected = settings.showResolvedValuesInline
    }

    override fun disposeUIResources() {
        mainPanel = null
        atmosPathField = null
        validateOnSaveCheckbox = null
        showResolvedValuesCheckbox = null
    }
}
