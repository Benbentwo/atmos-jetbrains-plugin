package com.cloudposse.atmos.run

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.AtmosIcons
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.ConfigurationTypeBase
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * Run configuration type for Atmos CLI commands.
 *
 * This allows users to create run configurations for commands like:
 * - atmos terraform plan <component> -s <stack>
 * - atmos terraform apply <component> -s <stack>
 * - atmos describe stacks
 * - atmos describe component <component> -s <stack>
 * - atmos validate component <component> -s <stack>
 * - atmos workflow <name>
 */
class AtmosRunConfigurationType : ConfigurationTypeBase(
    ID,
    AtmosBundle.message("run.configuration.type.name"),
    AtmosBundle.message("run.configuration.type.description"),
    AtmosIcons.ATMOS
) {
    companion object {
        const val ID = "AtmosRunConfiguration"

        fun getInstance(): AtmosRunConfigurationType {
            return ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
                .filterIsInstance<AtmosRunConfigurationType>()
                .first()
        }
    }

    init {
        addFactory(AtmosConfigurationFactory(this))
    }
}

/**
 * Factory for creating Atmos run configurations.
 */
class AtmosConfigurationFactory(type: AtmosRunConfigurationType) : ConfigurationFactory(type) {

    override fun getId(): String = "AtmosConfigurationFactory"

    override fun getName(): String = AtmosBundle.message("run.configuration.factory.name")

    override fun createTemplateConfiguration(project: Project): RunConfiguration {
        return AtmosRunConfiguration(project, this, "Atmos")
    }

    override fun getIcon(): Icon = AtmosIcons.ATMOS
}
