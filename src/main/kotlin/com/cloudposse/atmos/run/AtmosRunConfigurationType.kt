package com.cloudposse.atmos.run

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.AtmosIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * Run configuration type for Atmos commands.
 */
class AtmosRunConfigurationType : ConfigurationType {

    override fun getDisplayName(): String = AtmosBundle.message("run.configuration.type.name")

    override fun getConfigurationTypeDescription(): String = AtmosBundle.message("run.configuration.type.description")

    override fun getIcon(): Icon = AtmosIcons.ATMOS

    override fun getId(): String = "AtmosRunConfiguration"

    override fun getConfigurationFactories(): Array<ConfigurationFactory> {
        return arrayOf(AtmosConfigurationFactory(this))
    }

    companion object {
        val INSTANCE: AtmosRunConfigurationType
            get() = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
                .filterIsInstance<AtmosRunConfigurationType>()
                .first()
    }
}

/**
 * Factory for creating Atmos run configurations.
 */
class AtmosConfigurationFactory(type: ConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "AtmosConfigurationFactory"

    override fun getName(): String = AtmosBundle.message("run.configuration.factory.name")

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return AtmosRunConfiguration(project, this, "Atmos")
    }

    override fun getOptionsClass(): Class<out BaseState> {
        return AtmosRunConfigurationOptions::class.java
    }
}
