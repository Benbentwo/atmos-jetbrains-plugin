package com.cloudposse.atmos.actions

import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.services.AtmosProjectService
import com.cloudposse.atmos.settings.AtmosSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * Base class for Atmos context actions.
 */
abstract class AtmosContextAction(
    text: String,
    description: String? = null
) : AnAction(text, description, AtmosIcons.ATMOS) {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)

        e.presentation.isEnabledAndVisible = project != null &&
                file != null &&
                AtmosProjectService.getInstance(project).isAtmosProject &&
                (AtmosProjectService.getInstance(project).isStackFile(file) ||
                        AtmosProjectService.getInstance(project).isStackTemplateFile(file))
    }

    protected fun getAtmosPath(): String {
        val settings = AtmosSettings.getInstance()
        return settings.atmosExecutablePath.ifEmpty { "atmos" }
    }

    protected fun executeAtmosCommand(project: Project, vararg args: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            val commandLine = GeneralCommandLine(getAtmosPath(), *args)
            commandLine.setWorkDirectory(project.basePath)

            val processHandler = ProcessHandlerFactory.getInstance().createColoredProcessHandler(commandLine)
            ProcessTerminatedListener.attach(processHandler)

            ApplicationManager.getApplication().invokeLater {
                val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Run")
                toolWindow?.show()
            }

            processHandler.startNotify()
        }
    }

    protected fun findComponentContext(event: AnActionEvent): ComponentContext? {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
        val psiFile = event.getData(CommonDataKeys.PSI_FILE) as? YAMLFile ?: return null
        val offset = editor.caretModel.offset
        val element = psiFile.findElementAt(offset) ?: return null

        return findComponentFromElement(element)
    }

    private fun findComponentFromElement(element: PsiElement): ComponentContext? {
        var current: PsiElement? = element
        var componentName: String? = null
        var componentType: String? = null

        while (current != null) {
            if (current is YAMLKeyValue) {
                when (current.keyText) {
                    "terraform" -> componentType = "terraform"
                    "helmfile" -> componentType = "helmfile"
                    "components" -> break
                    else -> {
                        if (componentType != null && componentName == null) {
                            componentName = current.keyText
                        }
                    }
                }
            }
            current = current.parent
        }

        if (componentName == null) return null

        return ComponentContext(componentName, componentType ?: "terraform")
    }

    data class ComponentContext(
        val componentName: String,
        val componentType: String
    )
}

/**
 * Action to describe the current stack.
 */
class DescribeStackAction : AtmosContextAction("Describe This Stack", "Run 'atmos describe stacks' for the current file") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val stackName = file.nameWithoutExtension

        executeAtmosCommand(project, "describe", "stacks", "--stack", stackName)
    }
}

/**
 * Action to describe the current component.
 */
class DescribeComponentAction : AtmosContextAction("Describe Component", "Run 'atmos describe component' for the component at cursor") {

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isEnabledAndVisible) return

        val context = findComponentContext(e)
        e.presentation.isEnabledAndVisible = context != null
        if (context != null) {
            e.presentation.text = "Describe Component '${context.componentName}'"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val context = findComponentContext(e) ?: return
        val stackName = file.nameWithoutExtension

        executeAtmosCommand(project, "describe", "component", context.componentName, "-s", stackName)
    }
}

/**
 * Action to validate the current component.
 */
class ValidateComponentAction : AtmosContextAction("Validate Component", "Run 'atmos validate component' for the component at cursor") {

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isEnabledAndVisible) return

        val context = findComponentContext(e)
        e.presentation.isEnabledAndVisible = context != null
        if (context != null) {
            e.presentation.text = "Validate Component '${context.componentName}'"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val context = findComponentContext(e) ?: return
        val stackName = file.nameWithoutExtension

        executeAtmosCommand(project, "validate", "component", context.componentName, "-s", stackName)
    }
}

/**
 * Action to run terraform plan for the current component.
 */
class TerraformPlanAction : AtmosContextAction("Terraform Plan", "Run 'atmos terraform plan' for the component at cursor") {

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isEnabledAndVisible) return

        val context = findComponentContext(e)
        e.presentation.isEnabledAndVisible = context != null && context.componentType == "terraform"
        if (context != null) {
            e.presentation.text = "Terraform Plan '${context.componentName}'"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val context = findComponentContext(e) ?: return
        val stackName = file.nameWithoutExtension

        executeAtmosCommand(project, "terraform", "plan", context.componentName, "-s", stackName)
    }
}

/**
 * Action to run terraform apply for the current component.
 */
class TerraformApplyAction : AtmosContextAction("Terraform Apply", "Run 'atmos terraform apply' for the component at cursor") {

    override fun update(e: AnActionEvent) {
        super.update(e)
        if (!e.presentation.isEnabledAndVisible) return

        val context = findComponentContext(e)
        e.presentation.isEnabledAndVisible = context != null && context.componentType == "terraform"
        if (context != null) {
            e.presentation.text = "Terraform Apply '${context.componentName}'"
        }
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val context = findComponentContext(e) ?: return
        val stackName = file.nameWithoutExtension

        executeAtmosCommand(project, "terraform", "apply", context.componentName, "-s", stackName)
    }
}

/**
 * Action to show the describe affected output.
 */
class DescribeAffectedAction : AtmosContextAction("Describe Affected", "Run 'atmos describe affected' to show affected components") {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        executeAtmosCommand(project, "describe", "affected")
    }
}
