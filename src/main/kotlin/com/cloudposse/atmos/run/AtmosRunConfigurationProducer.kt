package com.cloudposse.atmos.run

import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.execution.actions.ConfigurationContext
import com.intellij.execution.actions.LazyRunConfigurationProducer
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping

/**
 * Producer for creating Atmos run configurations from context (e.g., right-click menu).
 *
 * This allows users to right-click on a component in a stack file and run terraform
 * plan/apply directly from the context menu.
 */
class AtmosRunConfigurationProducer : LazyRunConfigurationProducer<AtmosRunConfiguration>() {

    override fun getConfigurationFactory(): ConfigurationFactory {
        return AtmosRunConfigurationType.getInstance().configurationFactories[0]
    }

    override fun setupConfigurationFromContext(
        configuration: AtmosRunConfiguration,
        context: ConfigurationContext,
        sourceElement: Ref<PsiElement>
    ): Boolean {
        val project = context.project
        val projectService = AtmosProjectService.getInstance(project)

        if (!projectService.isAtmosProject) {
            return false
        }

        val element = context.psiLocation ?: return false
        val file = element.containingFile as? YAMLFile ?: return false

        if (!projectService.isStackFile(file.virtualFile)) {
            return false
        }

        // Try to find component context
        val componentInfo = findComponentContext(element) ?: return false

        // Determine stack name from file
        val stackName = deriveStackName(file, project) ?: return false

        // Set up the configuration
        configuration.name = "atmos terraform plan ${componentInfo.componentName} -s $stackName"
        configuration.commandType = AtmosCommandType.TERRAFORM_PLAN
        configuration.component = componentInfo.componentName
        configuration.stack = stackName
        configuration.workingDirectory = project.basePath ?: ""

        sourceElement.set(element)
        return true
    }

    override fun isConfigurationFromContext(
        configuration: AtmosRunConfiguration,
        context: ConfigurationContext
    ): Boolean {
        val project = context.project
        val projectService = AtmosProjectService.getInstance(project)

        if (!projectService.isAtmosProject) {
            return false
        }

        val element = context.psiLocation ?: return false
        val file = element.containingFile as? YAMLFile ?: return false

        if (!projectService.isStackFile(file.virtualFile)) {
            return false
        }

        val componentInfo = findComponentContext(element) ?: return false
        val stackName = deriveStackName(file, project) ?: return false

        return configuration.component == componentInfo.componentName &&
                configuration.stack == stackName &&
                (configuration.commandType == AtmosCommandType.TERRAFORM_PLAN ||
                        configuration.commandType == AtmosCommandType.TERRAFORM_APPLY)
    }

    /**
     * Finds the component context from the current PSI element.
     * Looks for patterns like:
     * components:
     *   terraform:
     *     vpc:   <- component name
     *       vars: ...
     */
    private fun findComponentContext(element: PsiElement): ComponentInfo? {
        var current: PsiElement? = element

        while (current != null) {
            if (current is YAMLKeyValue) {
                val parent = current.parent?.parent
                if (parent is YAMLKeyValue) {
                    val parentKey = parent.keyText
                    if (parentKey == "terraform" || parentKey == "helmfile") {
                        val grandParent = parent.parent?.parent
                        if (grandParent is YAMLKeyValue && grandParent.keyText == "components") {
                            return ComponentInfo(
                                componentName = current.keyText,
                                componentType = parentKey
                            )
                        }
                    }
                }

                // Check if we're at the terraform/helmfile level
                if (current.keyText == "terraform" || current.keyText == "helmfile") {
                    val parent2 = current.parent?.parent
                    if (parent2 is YAMLKeyValue && parent2.keyText == "components") {
                        // Get the first child component
                        val componentMapping = current.value as? YAMLMapping
                        val firstComponent = componentMapping?.keyValues?.firstOrNull()
                        if (firstComponent != null) {
                            return ComponentInfo(
                                componentName = firstComponent.keyText,
                                componentType = current.keyText
                            )
                        }
                    }
                }
            }
            current = current.parent
        }

        return null
    }

    /**
     * Derives the stack name from the file path using the name pattern.
     */
    private fun deriveStackName(file: PsiFile, project: com.intellij.openapi.project.Project): String? {
        val configService = AtmosConfigurationService.getInstance(project)
        val projectService = AtmosProjectService.getInstance(project)

        val stacksBasePath = configService.getStacksBasePath() ?: return null
        val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return null
        val filePath = file.virtualFile?.path ?: return null
        val stacksDirPath = stacksDir.path

        if (!filePath.startsWith(stacksDirPath)) {
            return null
        }

        // Get relative path without extension
        var relativePath = filePath.removePrefix(stacksDirPath).removePrefix("/")

        // Remove file extensions
        relativePath = relativePath
            .removeSuffix(".yaml.tmpl")
            .removeSuffix(".yml.tmpl")
            .removeSuffix(".yaml")
            .removeSuffix(".yml")

        // If the path starts with catalog/ or orgs/, use the file name
        // Otherwise, convert path separators to dashes for the stack name
        return if (relativePath.startsWith("catalog/") || relativePath.startsWith("mixins/")) {
            // For catalog files, use the relative path as-is (without leading catalog/)
            relativePath
        } else {
            // For org-based stacks, the file path often IS the stack name
            // Convert path to stack name format (e.g., acme/prod/us-east-2 -> acme-prod-us-east-2)
            relativePath.replace("/", "-")
        }
    }

    private data class ComponentInfo(
        val componentName: String,
        val componentType: String
    )
}
