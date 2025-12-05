package com.cloudposse.atmos.navigation

import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Provides references for component names and variable keys in Atmos stack files.
 * Enables navigation to:
 * - Terraform component directories (when clicking on component names)
 * - Variable definitions in variables.tf (when clicking on var keys)
 * - Inherited component definitions (when clicking on metadata.inherits entries)
 */
class AtmosComponentReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Register provider for YAML key values
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLKeyValue::class.java),
            AtmosComponentReferenceProvider()
        )
    }
}

/**
 * Provides references based on the context within an Atmos stack file.
 */
class AtmosComponentReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is YAMLKeyValue) return PsiReference.EMPTY_ARRAY

        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return PsiReference.EMPTY_ARRAY

        val containingFile = element.containingFile?.virtualFile
        if (containingFile != null && !projectService.isStackFile(containingFile) &&
            !projectService.isStackTemplateFile(containingFile)) {
            return PsiReference.EMPTY_ARRAY
        }

        // Determine the context and create appropriate references
        val componentContext = getComponentContext(element)

        return when (componentContext) {
            is ComponentContext.ComponentName -> {
                arrayOf(AtmosComponentNameReference(element, componentContext.componentType))
            }
            is ComponentContext.VariableKey -> {
                arrayOf(AtmosVariableReference(element, componentContext.componentName, componentContext.componentType))
            }
            is ComponentContext.MetadataComponent -> {
                arrayOf(AtmosMetadataComponentReference(element))
            }
            null -> PsiReference.EMPTY_ARRAY
        }
    }

    private fun getComponentContext(element: YAMLKeyValue): ComponentContext? {
        val keyText = element.keyText
        var current: PsiElement? = element.parent
        var depth = 0
        var inVars = false
        var componentName: String? = null
        var componentType: ComponentType? = null

        while (current != null && depth < 10) {
            if (current is YAMLKeyValue) {
                when (current.keyText) {
                    "vars" -> inVars = true
                    "terraform" -> componentType = ComponentType.TERRAFORM
                    "helmfile" -> componentType = ComponentType.HELMFILE
                    "components" -> {
                        // We found the components block
                        if (componentType != null) {
                            // Check if this element is a component name (direct child of terraform/helmfile)
                            if (element.parent?.parent is YAMLKeyValue &&
                                (element.parent?.parent as YAMLKeyValue).keyText in listOf("terraform", "helmfile")) {
                                return ComponentContext.ComponentName(componentType)
                            }
                            // Check if this element is within vars of a component
                            if (inVars && componentName != null) {
                                return ComponentContext.VariableKey(componentName, componentType)
                            }
                        }
                        break
                    }
                    "metadata" -> {
                        // Check if this is metadata.component
                        if (keyText == "component") {
                            return ComponentContext.MetadataComponent
                        }
                    }
                }

                // Track component name if we're at the right level
                if (componentType != null && !inVars && current != element) {
                    componentName = current.keyText
                }
            }
            current = current.parent
            depth++
        }

        return null
    }

    sealed class ComponentContext {
        data class ComponentName(val componentType: ComponentType) : ComponentContext()
        data class VariableKey(val componentName: String, val componentType: ComponentType) : ComponentContext()
        data object MetadataComponent : ComponentContext()
    }

    enum class ComponentType {
        TERRAFORM, HELMFILE
    }
}

/**
 * Reference for a component name that resolves to the component directory.
 */
class AtmosComponentNameReference(
    element: YAMLKeyValue,
    private val componentType: AtmosComponentReferenceProvider.ComponentType
) : PsiReferenceBase<YAMLKeyValue>(element, TextRange(0, element.keyText.length), true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val componentName = element.keyText
        val componentDir = resolveComponentDirectory(componentName) ?: return null

        // Return the main.tf file or the directory itself
        val mainFile = when (componentType) {
            AtmosComponentReferenceProvider.ComponentType.TERRAFORM ->
                componentDir.findChild("main.tf") ?: componentDir.findChild("variables.tf")
            AtmosComponentReferenceProvider.ComponentType.HELMFILE ->
                componentDir.findChild("helmfile.yaml")
        }

        val targetFile = mainFile ?: return PsiManager.getInstance(project).findDirectory(componentDir)
        return PsiManager.getInstance(project).findFile(targetFile)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    private fun resolveComponentDirectory(componentName: String): VirtualFile? {
        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        val configService = AtmosConfigurationService.getInstance(project)

        val basePath = when (componentType) {
            AtmosComponentReferenceProvider.ComponentType.TERRAFORM -> configService.getTerraformComponentsBasePath()
            AtmosComponentReferenceProvider.ComponentType.HELMFILE -> configService.getHelmfileComponentsBasePath()
        } ?: return null

        val componentsDir = projectService.resolveProjectPath(basePath) ?: return null
        return componentsDir.findFileByRelativePath(componentName)
    }
}

/**
 * Reference for a variable key that resolves to the variable definition in variables.tf.
 */
class AtmosVariableReference(
    element: YAMLKeyValue,
    private val componentName: String,
    private val componentType: AtmosComponentReferenceProvider.ComponentType
) : PsiReferenceBase<YAMLKeyValue>(element, TextRange(0, element.keyText.length), true) {

    override fun resolve(): PsiElement? {
        if (componentType != AtmosComponentReferenceProvider.ComponentType.TERRAFORM) {
            return null // Only Terraform components have variables.tf
        }

        val project = element.project
        val variableName = element.keyText
        val variablesFile = resolveVariablesFile() ?: return null

        // Find the variable block in variables.tf
        val psiFile = PsiManager.getInstance(project).findFile(variablesFile) ?: return null
        return findVariableDefinition(psiFile, variableName)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    private fun resolveVariablesFile(): VirtualFile? {
        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        val configService = AtmosConfigurationService.getInstance(project)

        val basePath = configService.getTerraformComponentsBasePath() ?: return null
        val componentsDir = projectService.resolveProjectPath(basePath) ?: return null

        // First check if there's a metadata.component path
        val metadataComponentPath = findMetadataComponentPath()
        val componentPath = metadataComponentPath ?: componentName

        val componentDir = componentsDir.findFileByRelativePath(componentPath) ?: return null
        return componentDir.findChild("variables.tf")
    }

    private fun findMetadataComponentPath(): String? {
        // Walk up to find the component's metadata.component value
        var current: PsiElement? = element
        while (current != null) {
            if (current is YAMLKeyValue && current.keyText == componentName) {
                // Found the component, look for metadata.component
                val value = current.value
                if (value is YAMLMapping) {
                    val metadata = value.getKeyValueByKey("metadata")?.value
                    if (metadata is YAMLMapping) {
                        val component = metadata.getKeyValueByKey("component")?.value
                        if (component is YAMLScalar) {
                            return component.textValue
                        }
                    }
                }
                break
            }
            current = current.parent
        }
        return null
    }

    private fun findVariableDefinition(psiFile: PsiFile, variableName: String): PsiElement? {
        // For HCL files, we need to find: variable "name" { ... }
        // This is a simple text-based search; a proper implementation would use HCL PSI
        val text = psiFile.text
        val pattern = """variable\s+"$variableName"\s*\{""".toRegex()
        val match = pattern.find(text) ?: return null

        return psiFile.findElementAt(match.range.first)
    }
}

/**
 * Reference for metadata.component that resolves to the actual component path.
 */
class AtmosMetadataComponentReference(
    element: YAMLKeyValue
) : PsiReferenceBase<YAMLKeyValue>(element, calculateValueRange(element), true) {

    companion object {
        private fun calculateValueRange(element: YAMLKeyValue): TextRange {
            val value = element.value
            return if (value != null) {
                val startOffset = value.startOffsetInParent
                TextRange(startOffset, startOffset + value.textLength)
            } else {
                TextRange(0, element.textLength)
            }
        }
    }

    override fun resolve(): PsiElement? {
        val project = element.project
        val value = element.value as? YAMLScalar ?: return null
        val componentPath = value.textValue

        val componentDir = resolveComponentDirectory(componentPath) ?: return null

        // Return the main.tf file or the directory itself
        val mainFile = componentDir.findChild("main.tf") ?: componentDir.findChild("variables.tf")

        val targetFile = mainFile ?: return PsiManager.getInstance(project).findDirectory(componentDir)
        return PsiManager.getInstance(project).findFile(targetFile)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    private fun resolveComponentDirectory(componentPath: String): VirtualFile? {
        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        val configService = AtmosConfigurationService.getInstance(project)

        // Try Terraform components first
        var basePath = configService.getTerraformComponentsBasePath()
        var componentsDir = basePath?.let { projectService.resolveProjectPath(it) }
        var result = componentsDir?.findFileByRelativePath(componentPath)

        if (result != null) return result

        // Try Helmfile components
        basePath = configService.getHelmfileComponentsBasePath()
        componentsDir = basePath?.let { projectService.resolveProjectPath(it) }
        return componentsDir?.findFileByRelativePath(componentPath)
    }
}
