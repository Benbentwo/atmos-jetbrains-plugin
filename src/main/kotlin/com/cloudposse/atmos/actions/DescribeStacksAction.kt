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

/**
 * Action to run 'atmos describe stacks'.
 */
class DescribeStacksAction : AtmosBaseAction(
    AtmosBundle.message("action.describe.stacks"),
    AtmosBundle.message("action.describe.stacks.description"),
    AtmosIcons.ATMOS
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.INSTANCE
        val settings = runManager.createConfiguration("Describe Stacks", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration
        config.commandType = AtmosCommandType.DESCRIBE_STACKS

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}
