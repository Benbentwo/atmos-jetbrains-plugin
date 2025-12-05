package com.cloudposse.atmos.completion

import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * Provides code completion for Atmos stack files.
 * Includes completions for:
 * - Import paths (stack files from stacks.base_path)
 * - Component names (from components/terraform and components/helmfile)
 * - YAML function tags (!env, !exec, etc.)
 * - Common metadata keys
 */
class AtmosCompletionContributor : CompletionContributor() {

    init {
        // Import path completion
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(YAMLScalar::class.java),
            ImportPathCompletionProvider()
        )

        // Component name completion
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(YAMLMapping::class.java),
            ComponentNameCompletionProvider()
        )

        // Metadata completion
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement().inside(YAMLMapping::class.java),
            MetadataCompletionProvider()
        )
    }
}

/**
 * Provides completion for import paths.
 */
class ImportPathCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        if (!isWithinImportBlock(element)) return

        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return

        val configService = AtmosConfigurationService.getInstance(project)
        val stacksBasePath = configService.getStacksBasePath() ?: return
        val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return

        // Get current typed text for filtering
        val currentText = extractTypedText(element)
        val prefix = currentText.substringBeforeLast("/", "")

        // Find the directory to complete from
        val searchDir = if (prefix.isNotEmpty()) {
            stacksDir.findFileByRelativePath(prefix) ?: stacksDir
        } else {
            stacksDir
        }

        // Collect all stack files and directories
        collectStackFiles(searchDir, stacksDir, result, prefix)
    }

    private fun isWithinImportBlock(element: PsiElement): Boolean {
        var current: PsiElement? = element
        while (current != null) {
            if (current is YAMLSequenceItem) {
                val parent = current.parent?.parent
                if (parent is YAMLKeyValue && parent.keyText == "import") {
                    return true
                }
            }
            current = current.parent
        }
        return false
    }

    private fun extractTypedText(element: PsiElement): String {
        val text = element.text
        // Remove the IntelliJ completion placeholder
        return text.replace("IntellijIdeaRulezzz", "").trim('"', '\'')
    }

    private fun collectStackFiles(
        searchDir: VirtualFile,
        stacksDir: VirtualFile,
        result: CompletionResultSet,
        prefix: String
    ) {
        searchDir.children.forEach { child ->
            val relativePath = getRelativePath(child, stacksDir)
            val displayPath = removeExtension(relativePath)

            if (child.isDirectory) {
                // Add directory with trailing slash to indicate more completions available
                result.addElement(
                    LookupElementBuilder.create("$displayPath/")
                        .withIcon(AtmosIcons.STACK_FOLDER)
                        .withTypeText("directory")
                )
                // Recursively add files from subdirectories
                collectStackFiles(child, stacksDir, result, prefix)
            } else if (isStackFile(child)) {
                result.addElement(
                    LookupElementBuilder.create(displayPath)
                        .withIcon(AtmosIcons.STACK_FILE)
                        .withTypeText(child.extension ?: "yaml")
                        .withTailText(" (${child.name})", true)
                )
            }
        }
    }

    private fun getRelativePath(file: VirtualFile, baseDir: VirtualFile): String {
        val basePath = baseDir.path
        val filePath = file.path
        return if (filePath.startsWith(basePath)) {
            filePath.substring(basePath.length + 1)
        } else {
            file.name
        }
    }

    private fun removeExtension(path: String): String {
        return path
            .removeSuffix(".yaml.tmpl")
            .removeSuffix(".yml.tmpl")
            .removeSuffix(".yaml")
            .removeSuffix(".yml")
    }

    private fun isStackFile(file: VirtualFile): Boolean {
        val name = file.name
        return name.endsWith(".yaml") || name.endsWith(".yml") ||
                name.endsWith(".yaml.tmpl") || name.endsWith(".yml.tmpl")
    }
}

/**
 * Provides completion for component names under components.terraform or components.helmfile.
 */
class ComponentNameCompletionProvider : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        val componentContext = getComponentContext(element) ?: return

        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return

        val configService = AtmosConfigurationService.getInstance(project)

        val componentsBasePath = when (componentContext) {
            ComponentType.TERRAFORM -> configService.getTerraformComponentsBasePath()
            ComponentType.HELMFILE -> configService.getHelmfileComponentsBasePath()
        } ?: return

        val componentsDir = projectService.resolveProjectPath(componentsBasePath) ?: return

        // Collect component directories
        collectComponents(componentsDir, componentsDir, result, componentContext)
    }

    private fun getComponentContext(element: PsiElement): ComponentType? {
        var current: PsiElement? = element
        var foundComponents = false

        while (current != null) {
            if (current is YAMLKeyValue) {
                when (current.keyText) {
                    "terraform" -> if (foundComponents) return ComponentType.TERRAFORM
                    "helmfile" -> if (foundComponents) return ComponentType.HELMFILE
                    "components" -> foundComponents = true
                }
            }
            current = current.parent
        }
        return null
    }

    private fun collectComponents(
        searchDir: VirtualFile,
        baseDir: VirtualFile,
        result: CompletionResultSet,
        componentType: ComponentType
    ) {
        searchDir.children.forEach { child ->
            if (child.isDirectory) {
                // Check if this is a component (has main.tf for terraform)
                val isComponent = when (componentType) {
                    ComponentType.TERRAFORM -> child.findChild("main.tf") != null ||
                            child.findChild("variables.tf") != null
                    ComponentType.HELMFILE -> child.findChild("helmfile.yaml") != null
                }

                if (isComponent) {
                    val componentPath = getRelativePath(child, baseDir)
                    result.addElement(
                        LookupElementBuilder.create(componentPath)
                            .withIcon(AtmosIcons.COMPONENT)
                            .withTypeText(componentType.name.lowercase())
                            .withTailText(" component", true)
                    )
                }

                // Recursively search subdirectories
                collectComponents(child, baseDir, result, componentType)
            }
        }
    }

    private fun getRelativePath(file: VirtualFile, baseDir: VirtualFile): String {
        val basePath = baseDir.path
        val filePath = file.path
        return if (filePath.startsWith(basePath)) {
            filePath.substring(basePath.length + 1)
        } else {
            file.name
        }
    }

    enum class ComponentType {
        TERRAFORM, HELMFILE
    }
}

/**
 * Provides completion for metadata keys and common settings.
 */
class MetadataCompletionProvider : CompletionProvider<CompletionParameters>() {

    companion object {
        private val METADATA_KEYS = listOf(
            "type" to "Component type (abstract or real)",
            "component" to "Path to actual component",
            "inherits" to "List of components to inherit from",
            "terraform_workspace" to "Override Terraform workspace name"
        )

        private val COMPONENT_TYPES = listOf(
            "abstract" to "Abstract component, not directly deployable",
            "real" to "Real component, can be deployed"
        )

        private val SETTINGS_KEYS = listOf(
            "spacelift" to "Spacelift integration settings",
            "atlantis" to "Atlantis integration settings",
            "validation" to "Component validation settings"
        )

        private val YAML_FUNCTIONS = listOf(
            "!env" to "Environment variable reference",
            "!exec" to "Shell command execution",
            "!include" to "Include external YAML file",
            "!repo-root" to "Git repository root path",
            "!terraform.output" to "Reference Terraform output",
            "!terraform.state" to "Reference Terraform state",
            "!atmos.Component" to "Cross-component reference"
        )
    }

    override fun addCompletions(
        parameters: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet
    ) {
        val element = parameters.position
        val completionContext = getCompletionContext(element) ?: return

        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return

        when (completionContext) {
            CompletionContext.METADATA_KEY -> {
                METADATA_KEYS.forEach { (key, description) ->
                    result.addElement(
                        LookupElementBuilder.create(key)
                            .withTypeText(description)
                            .withIcon(AtmosIcons.METADATA)
                    )
                }
            }
            CompletionContext.METADATA_TYPE -> {
                COMPONENT_TYPES.forEach { (type, description) ->
                    result.addElement(
                        LookupElementBuilder.create(type)
                            .withTypeText(description)
                    )
                }
            }
            CompletionContext.SETTINGS_KEY -> {
                SETTINGS_KEYS.forEach { (key, description) ->
                    result.addElement(
                        LookupElementBuilder.create(key)
                            .withTypeText(description)
                            .withIcon(AtmosIcons.SETTINGS)
                    )
                }
            }
            CompletionContext.YAML_TAG -> {
                YAML_FUNCTIONS.forEach { (tag, description) ->
                    result.addElement(
                        LookupElementBuilder.create(tag)
                            .withTypeText(description)
                            .bold()
                    )
                }
            }
        }
    }

    private fun getCompletionContext(element: PsiElement): CompletionContext? {
        var current: PsiElement? = element

        while (current != null) {
            if (current is YAMLKeyValue) {
                when (current.keyText) {
                    "metadata" -> return CompletionContext.METADATA_KEY
                    "type" -> {
                        // Check if parent is metadata
                        val parent = current.parent?.parent
                        if (parent is YAMLKeyValue && parent.keyText == "metadata") {
                            return CompletionContext.METADATA_TYPE
                        }
                    }
                    "settings" -> return CompletionContext.SETTINGS_KEY
                }
            }
            current = current.parent
        }

        // Check if we're at a tag position
        val text = element.text
        if (text.startsWith("!") || text.contains("IntellijIdeaRulezzz") && element.parent?.text?.contains("!") == true) {
            return CompletionContext.YAML_TAG
        }

        return null
    }

    enum class CompletionContext {
        METADATA_KEY,
        METADATA_TYPE,
        SETTINGS_KEY,
        YAML_TAG
    }
}
