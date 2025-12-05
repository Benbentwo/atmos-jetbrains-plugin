package com.cloudposse.atmos.actions

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.run.AtmosCommandType
import com.cloudposse.atmos.run.AtmosRunConfiguration
import com.cloudposse.atmos.run.AtmosRunConfigurationType
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.ui.Messages

/**
 * Action to run 'atmos terraform plan' for a component.
 */
class TerraformPlanAction : AtmosBaseAction(
    AtmosBundle.message("action.terraform.plan"),
    AtmosBundle.message("action.terraform.plan.description"),
    AtmosIcons.ATMOS
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Prompt for component and stack
        val component = Messages.showInputDialog(
            project,
            AtmosBundle.message("dialog.enter.component"),
            AtmosBundle.message("action.terraform.plan"),
            null
        ) ?: return

        val stack = Messages.showInputDialog(
            project,
            AtmosBundle.message("dialog.enter.stack"),
            AtmosBundle.message("action.terraform.plan"),
            null
        ) ?: return

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.INSTANCE
        val settings = runManager.createConfiguration("Plan $component -s $stack", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration
        config.commandType = AtmosCommandType.TERRAFORM_PLAN
        config.component = component
        config.stack = stack

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}
