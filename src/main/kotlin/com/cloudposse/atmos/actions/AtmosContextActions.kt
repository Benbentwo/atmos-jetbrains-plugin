package com.cloudposse.atmos.actions

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.run.AtmosCommandType
import com.cloudposse.atmos.run.AtmosRunConfiguration
import com.cloudposse.atmos.run.AtmosRunConfigurationType
import com.cloudposse.atmos.services.AtmosCommandRunner
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * Base class for Atmos context actions.
 */
abstract class AtmosContextAction(
    text: String,
    description: String,
    icon: javax.swing.Icon?
) : AnAction(text, description, icon), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.PSI_FILE) as? YAMLFile

        e.presentation.isEnabledAndVisible = project != null &&
                file != null &&
                AtmosProjectService.getInstance(project).isStackFile(file.virtualFile)
    }

    /**
     * Finds component context from the current caret position.
     */
    protected fun findComponentContext(e: AnActionEvent): ComponentContext? {
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return null
        val file = e.getData(CommonDataKeys.PSI_FILE) as? YAMLFile ?: return null
        val project = e.project ?: return null

        val offset = editor.caretModel.offset
        var element = file.findElementAt(offset) ?: return null

        // Walk up to find component context
        while (element.parent != null) {
            if (element is YAMLKeyValue) {
                val parent = element.parent?.parent
                if (parent is YAMLKeyValue) {
                    val parentKey = parent.keyText
                    if (parentKey == "terraform" || parentKey == "helmfile") {
                        val grandParent = parent.parent?.parent
                        if (grandParent is YAMLKeyValue && grandParent.keyText == "components") {
                            val stackName = deriveStackName(file, project)
                            return ComponentContext(
                                componentName = element.keyText,
                                componentType = parentKey,
                                stackName = stackName ?: ""
                            )
                        }
                    }
                }
            }
            element = element.parent ?: break
        }

        return null
    }

    /**
     * Derives the stack name from the file path.
     */
    protected fun deriveStackName(file: YAMLFile, project: com.intellij.openapi.project.Project): String? {
        val configService = AtmosConfigurationService.getInstance(project)
        val projectService = AtmosProjectService.getInstance(project)

        val stacksBasePath = configService.getStacksBasePath() ?: return null
        val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return null
        val filePath = file.virtualFile?.path ?: return null
        val stacksDirPath = stacksDir.path

        if (!filePath.startsWith(stacksDirPath)) {
            return null
        }

        var relativePath = filePath.removePrefix(stacksDirPath).removePrefix("/")
        relativePath = relativePath
            .removeSuffix(".yaml.tmpl")
            .removeSuffix(".yml.tmpl")
            .removeSuffix(".yaml")
            .removeSuffix(".yml")

        return if (relativePath.startsWith("catalog/") || relativePath.startsWith("mixins/")) {
            relativePath
        } else {
            relativePath.replace("/", "-")
        }
    }

    data class ComponentContext(
        val componentName: String,
        val componentType: String,
        val stackName: String
    )
}

/**
 * Action to run "atmos describe component" for the current component.
 */
class DescribeComponentAction : AtmosContextAction(
    AtmosBundle.message("action.describe.component"),
    AtmosBundle.message("action.describe.component.description"),
    AllIcons.Actions.Preview
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = findComponentContext(e)

        if (context == null) {
            Messages.showInfoMessage(
                project,
                AtmosBundle.message("action.no.component.context"),
                AtmosBundle.message("action.describe.component")
            )
            return
        }

        AtmosCommandRunner.getInstance(project).describeComponent(context.componentName, context.stackName)
            .thenAccept { result ->
                ApplicationManager.getApplication().invokeLater {
                    showResultInToolWindow(project, result, "Describe Component: ${context.componentName}")
                }
            }
    }

    private fun showResultInToolWindow(
        project: com.intellij.openapi.project.Project,
        result: AtmosCommandRunner.CommandResult,
        title: String
    ) {
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Atmos")
        toolWindow?.show {
            // The console is in the tool window - for now just show a message
            if (!result.isSuccess) {
                Messages.showErrorDialog(project, result.stderr, title)
            }
        }
    }
}

/**
 * Action to run "atmos validate component" for the current component.
 */
class ValidateComponentAction : AtmosContextAction(
    AtmosBundle.message("action.validate.component"),
    AtmosBundle.message("action.validate.component.description"),
    AllIcons.Actions.Checked
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = findComponentContext(e)

        if (context == null) {
            Messages.showInfoMessage(
                project,
                AtmosBundle.message("action.no.component.context"),
                AtmosBundle.message("action.validate.component")
            )
            return
        }

        AtmosCommandRunner.getInstance(project).validateComponent(context.componentName, context.stackName)
            .thenAccept { result ->
                ApplicationManager.getApplication().invokeLater {
                    if (result.isSuccess) {
                        Messages.showInfoMessage(
                            project,
                            AtmosBundle.message("action.validate.component.success", context.componentName),
                            AtmosBundle.message("action.validate.component")
                        )
                    } else {
                        Messages.showErrorDialog(
                            project,
                            result.stderr.ifBlank { result.stdout },
                            AtmosBundle.message("action.validate.component")
                        )
                    }
                }
            }
    }
}

/**
 * Action to run "atmos terraform plan" for the current component.
 */
class TerraformPlanAction : AtmosContextAction(
    AtmosBundle.message("action.terraform.plan"),
    AtmosBundle.message("action.terraform.plan.description"),
    AllIcons.Actions.Execute
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = findComponentContext(e)

        if (context == null) {
            Messages.showInfoMessage(
                project,
                AtmosBundle.message("action.no.component.context"),
                AtmosBundle.message("action.terraform.plan")
            )
            return
        }

        runAtmosCommand(project, context, AtmosCommandType.TERRAFORM_PLAN)
    }

    private fun runAtmosCommand(
        project: com.intellij.openapi.project.Project,
        context: ComponentContext,
        commandType: AtmosCommandType
    ) {
        val runManager = RunManager.getInstance(project)
        val configurationType = AtmosRunConfigurationType.getInstance()
        val factory = configurationType.configurationFactories[0]

        val configName = "atmos ${commandType.command} ${context.componentName} -s ${context.stackName}"
        val settings = runManager.createConfiguration(configName, factory)
        val configuration = settings.configuration as AtmosRunConfiguration

        configuration.commandType = commandType
        configuration.component = context.componentName
        configuration.stack = context.stackName
        configuration.workingDirectory = project.basePath ?: ""

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings

        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}

/**
 * Action to run "atmos terraform apply" for the current component.
 */
class TerraformApplyAction : AtmosContextAction(
    AtmosBundle.message("action.terraform.apply"),
    AtmosBundle.message("action.terraform.apply.description"),
    AllIcons.Actions.Commit
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val context = findComponentContext(e)

        if (context == null) {
            Messages.showInfoMessage(
                project,
                AtmosBundle.message("action.no.component.context"),
                AtmosBundle.message("action.terraform.apply")
            )
            return
        }

        // Confirm before apply
        val confirmed = Messages.showYesNoDialog(
            project,
            AtmosBundle.message("action.terraform.apply.confirm", context.componentName, context.stackName),
            AtmosBundle.message("action.terraform.apply"),
            Messages.getQuestionIcon()
        )

        if (confirmed == Messages.YES) {
            runAtmosCommand(project, context, AtmosCommandType.TERRAFORM_APPLY)
        }
    }

    private fun runAtmosCommand(
        project: com.intellij.openapi.project.Project,
        context: ComponentContext,
        commandType: AtmosCommandType
    ) {
        val runManager = RunManager.getInstance(project)
        val configurationType = AtmosRunConfigurationType.getInstance()
        val factory = configurationType.configurationFactories[0]

        val configName = "atmos ${commandType.command} ${context.componentName} -s ${context.stackName}"
        val settings = runManager.createConfiguration(configName, factory)
        val configuration = settings.configuration as AtmosRunConfiguration

        configuration.commandType = commandType
        configuration.component = context.componentName
        configuration.stack = context.stackName
        configuration.workingDirectory = project.basePath ?: ""

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings

        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }
}

/**
 * Action to describe the current stack.
 */
class DescribeStackAction : AtmosContextAction(
    AtmosBundle.message("action.describe.stack"),
    AtmosBundle.message("action.describe.stack.description"),
    AllIcons.Actions.Preview
) {
    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.PSI_FILE) as? YAMLFile

        e.presentation.isEnabledAndVisible = project != null &&
                file != null &&
                AtmosProjectService.getInstance(project).isStackFile(file.virtualFile)
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.PSI_FILE) as? YAMLFile ?: return

        val stackName = deriveStackName(file, project)
        if (stackName == null) {
            Messages.showErrorDialog(
                project,
                AtmosBundle.message("action.could.not.determine.stack"),
                AtmosBundle.message("action.describe.stack")
            )
            return
        }

        AtmosCommandRunner.getInstance(project).describeStacks()
            .thenAccept { result ->
                ApplicationManager.getApplication().invokeLater {
                    val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Atmos")
                    toolWindow?.show()

                    if (!result.isSuccess) {
                        Messages.showErrorDialog(project, result.stderr, "Describe Stack: $stackName")
                    }
                }
            }
    }
}
