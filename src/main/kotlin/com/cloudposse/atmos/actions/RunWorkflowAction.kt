package com.cloudposse.atmos.actions

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.run.AtmosCommandType
import com.cloudposse.atmos.run.AtmosRunConfiguration
import com.cloudposse.atmos.run.AtmosRunConfigurationType
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.ui.Messages

/**
 * Action to run 'atmos workflow' for a specified workflow.
 */
class RunWorkflowAction : AtmosBaseAction(
    AtmosBundle.message("action.run.workflow"),
    AtmosBundle.message("action.run.workflow.description"),
    AtmosIcons.ATMOS
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        // Prompt for workflow name
        val workflowName = Messages.showInputDialog(
            project,
            AtmosBundle.message("dialog.enter.workflow"),
            AtmosBundle.message("action.run.workflow"),
            null
        ) ?: return

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.INSTANCE
        val settings = runManager.createConfiguration("Workflow: $workflowName", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration
        config.commandType = AtmosCommandType.WORKFLOW
        config.workflowName = workflowName

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}
