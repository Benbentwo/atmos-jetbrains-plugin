package com.cloudposse.atmos.documentation

import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequenceItem

/**
 * Provides Quick Documentation (F1/Ctrl+Q) for Atmos elements.
 * Shows documentation for:
 * - Import paths (file location and contents preview)
 * - Component names (component info and variables)
 * - YAML functions (usage documentation)
 * - Metadata keys (documentation)
 */
class AtmosDocumentationProvider : AbstractDocumentationProvider() {

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val project = element?.project ?: return null
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return null

        // Check if we're in a stack file
        val containingFile = element.containingFile?.virtualFile
        if (containingFile != null &&
            !projectService.isStackFile(containingFile) &&
            !projectService.isStackTemplateFile(containingFile)) {
            return null
        }

        return when {
            isImportElement(element) -> generateImportDoc(element, projectService)
            isComponentElement(element) -> generateComponentDoc(element, projectService)
            isYamlFunctionElement(element) -> generateYamlFunctionDoc(element)
            isMetadataElement(element) -> generateMetadataDoc(element)
            isSettingsElement(element) -> generateSettingsDoc(element)
            else -> null
        }
    }

    override fun getQuickNavigateInfo(element: PsiElement?, originalElement: PsiElement?): String? {
        val project = element?.project ?: return null
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return null

        return when {
            isImportElement(element) -> getImportQuickInfo(element, projectService)
            isComponentElement(element) -> getComponentQuickInfo(element, projectService)
            else -> null
        }
    }

    private fun isImportElement(element: PsiElement): Boolean {
        if (element !is YAMLScalar) return false
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

    private fun isComponentElement(element: PsiElement): Boolean {
        if (element !is YAMLKeyValue) return false
        val parent = element.parent?.parent
        if (parent is YAMLKeyValue && parent.keyText in listOf("terraform", "helmfile")) {
            val grandParent = parent.parent?.parent
            if (grandParent is YAMLKeyValue && grandParent.keyText == "components") {
                return true
            }
        }
        return false
    }

    private fun isYamlFunctionElement(element: PsiElement): Boolean {
        val text = element.text
        return text.startsWith("!env") || text.startsWith("!exec") ||
                text.startsWith("!include") || text.startsWith("!terraform") ||
                text.startsWith("!atmos") || text.startsWith("!repo-root")
    }

    private fun isMetadataElement(element: PsiElement): Boolean {
        if (element !is YAMLKeyValue) return false
        val parent = element.parent?.parent
        return parent is YAMLKeyValue && parent.keyText == "metadata"
    }

    private fun isSettingsElement(element: PsiElement): Boolean {
        if (element !is YAMLKeyValue) return false
        val parent = element.parent?.parent
        return parent is YAMLKeyValue && parent.keyText == "settings"
    }

    private fun generateImportDoc(element: PsiElement, projectService: AtmosProjectService): String {
        val scalar = element as? YAMLScalar ?: return ""
        val importPath = scalar.textValue
        val configService = AtmosConfigurationService.getInstance(element.project)

        val file = resolveImportFile(importPath, element, projectService, configService)

        return buildString {
            append("<html><body>")
            append("<h2>Import: <code>$importPath</code></h2>")

            if (file != null) {
                append("<p><b>File:</b> ${file.path}</p>")
                append("<hr/>")
                append("<h3>Contents Preview:</h3>")
                append("<pre>")
                append(getFilePreview(file, 20))
                append("</pre>")
            } else {
                append("<p><b>Status:</b> <span style='color:red'>File not found</span></p>")
                append("<p>Expected locations:</p>")
                append("<ul>")
                val stacksBase = configService.getStacksBasePath() ?: "stacks"
                append("<li>$stacksBase/$importPath.yaml</li>")
                append("<li>$stacksBase/$importPath.yml</li>")
                append("</ul>")
            }

            append("<hr/>")
            append("<p><a href='https://atmos.tools/stacks/imports'>Atmos Import Documentation</a></p>")
            append("</body></html>")
        }
    }

    private fun generateComponentDoc(element: PsiElement, projectService: AtmosProjectService): String {
        val keyValue = element as? YAMLKeyValue ?: return ""
        val componentName = keyValue.keyText
        val configService = AtmosConfigurationService.getInstance(element.project)

        // Determine component type
        val parent = keyValue.parent?.parent as? YAMLKeyValue
        val componentType = parent?.keyText ?: "terraform"

        val basePath = when (componentType) {
            "terraform" -> configService.getTerraformComponentsBasePath()
            "helmfile" -> configService.getHelmfileComponentsBasePath()
            else -> null
        }

        val componentDir = basePath?.let { projectService.resolveProjectPath(it) }
            ?.findFileByRelativePath(componentName)

        // Get metadata if present
        val componentMapping = keyValue.value as? YAMLMapping
        val metadataKv = componentMapping?.getKeyValueByKey("metadata")
        val metadata = metadataKv?.value as? YAMLMapping

        val actualComponent = metadata?.getKeyValueByKey("component")?.value?.let {
            (it as? YAMLScalar)?.textValue
        }
        @Suppress("UNUSED_VARIABLE")
        val inherits = metadata?.getKeyValueByKey("inherits")
        val componentTypeValue = metadata?.getKeyValueByKey("type")?.value?.let {
            (it as? YAMLScalar)?.textValue
        }

        return buildString {
            append("<html><body>")
            append("<h2>Component: <code>$componentName</code></h2>")

            append("<p><b>Type:</b> ${componentTypeValue ?: "real"}</p>")
            if (actualComponent != null) {
                append("<p><b>Actual Component:</b> $actualComponent</p>")
            }

            if (componentDir != null) {
                append("<p><b>Location:</b> ${componentDir.path}</p>")

                // List component files
                append("<h3>Files:</h3>")
                append("<ul>")
                componentDir.children.filter { !it.isDirectory }.take(10).forEach { file ->
                    append("<li>${file.name}</li>")
                }
                append("</ul>")

                // Show variables if available
                val variablesTf = componentDir.findChild("variables.tf")
                if (variablesTf != null) {
                    append("<h3>Variables:</h3>")
                    append("<pre>")
                    append(extractVariables(variablesTf))
                    append("</pre>")
                }
            } else {
                append("<p><b>Status:</b> <span style='color:orange'>Component not found</span></p>")
            }

            append("<hr/>")
            append("<p><a href='https://atmos.tools/quick-start/add-component'>Atmos Component Documentation</a></p>")
            append("</body></html>")
        }
    }

    private fun generateYamlFunctionDoc(element: PsiElement): String {
        val text = element.text

        val (functionName, description, example) = when {
            text.startsWith("!env") -> Triple(
                "!env",
                "References an environment variable. The value is resolved at runtime.",
                """
                    vars:
                      aws_region: !env AWS_REGION
                """.trimIndent()
            )
            text.startsWith("!exec") -> Triple(
                "!exec",
                "Executes a shell command and uses its output as the value.",
                """
                    vars:
                      git_sha: !exec git rev-parse --short HEAD
                """.trimIndent()
            )
            text.startsWith("!include") -> Triple(
                "!include",
                "Includes the contents of an external YAML file.",
                """
                    vars: !include ../shared/common-vars.yaml
                """.trimIndent()
            )
            text.startsWith("!terraform.output") -> Triple(
                "!terraform.output",
                "References an output from another Terraform component.",
                """
                    vars:
                      vpc_id: !terraform.output vpc.outputs.vpc_id
                """.trimIndent()
            )
            text.startsWith("!terraform.state") -> Triple(
                "!terraform.state",
                "References a value from another component's Terraform state.",
                """
                    vars:
                      db_host: !terraform.state database.host
                """.trimIndent()
            )
            text.startsWith("!atmos.Component") -> Triple(
                "!atmos.Component",
                "References configuration from another Atmos component.",
                """
                    vars:
                      vpc_config: !atmos.Component vpc
                """.trimIndent()
            )
            text.startsWith("!repo-root") -> Triple(
                "!repo-root",
                "Returns the path to the git repository root.",
                """
                    vars:
                      project_root: !repo-root
                """.trimIndent()
            )
            else -> return ""
        }

        return buildString {
            append("<html><body>")
            append("<h2>YAML Function: <code>$functionName</code></h2>")
            append("<p>$description</p>")
            append("<h3>Example:</h3>")
            append("<pre>$example</pre>")
            append("<hr/>")
            append("<p><a href='https://atmos.tools/yaml-functions'>Atmos YAML Functions Documentation</a></p>")
            append("</body></html>")
        }
    }

    private fun generateMetadataDoc(element: PsiElement): String {
        val keyValue = element as? YAMLKeyValue ?: return ""
        val key = keyValue.keyText

        val (description, values) = when (key) {
            "type" -> Pair(
                "Defines whether this is an abstract or real component.",
                listOf("abstract - Base component, not directly deployable", "real - Deployable component (default)")
            )
            "component" -> Pair(
                "Points to the actual Terraform/Helmfile component to use.",
                listOf("Path relative to components base directory")
            )
            "inherits" -> Pair(
                "List of components to inherit configuration from.",
                listOf("Values are merged from parents to child", "Later entries override earlier ones")
            )
            "terraform_workspace" -> Pair(
                "Override the Terraform workspace name.",
                listOf("By default, workspace is derived from the stack name")
            )
            else -> return ""
        }

        return buildString {
            append("<html><body>")
            append("<h2>Metadata: <code>$key</code></h2>")
            append("<p>$description</p>")
            append("<h3>Values:</h3>")
            append("<ul>")
            values.forEach { append("<li>$it</li>") }
            append("</ul>")
            append("<hr/>")
            append("<p><a href='https://atmos.tools/stacks/components'>Atmos Component Metadata Documentation</a></p>")
            append("</body></html>")
        }
    }

    private fun generateSettingsDoc(element: PsiElement): String {
        val keyValue = element as? YAMLKeyValue ?: return ""
        val key = keyValue.keyText

        val (description, link) = when (key) {
            "spacelift" -> Pair(
                "Spacelift integration settings for managing this component.",
                "https://atmos.tools/integrations/spacelift"
            )
            "atlantis" -> Pair(
                "Atlantis integration settings for PR-based workflows.",
                "https://atmos.tools/integrations/atlantis"
            )
            "validation" -> Pair(
                "Component validation settings using JSON Schema or OPA.",
                "https://atmos.tools/schemas"
            )
            else -> return ""
        }

        return buildString {
            append("<html><body>")
            append("<h2>Settings: <code>$key</code></h2>")
            append("<p>$description</p>")
            append("<hr/>")
            append("<p><a href='$link'>Documentation</a></p>")
            append("</body></html>")
        }
    }

    private fun resolveImportFile(
        importPath: String,
        element: PsiElement,
        projectService: AtmosProjectService,
        configService: AtmosConfigurationService
    ): VirtualFile? {
        val currentFile = element.containingFile?.virtualFile ?: return null
        val isRelative = importPath.startsWith(".") || importPath.startsWith("/")

        return if (isRelative) {
            val currentDir = currentFile.parent ?: return null
            val normalizedPath = if (importPath.startsWith("./")) {
                importPath.substring(2)
            } else {
                importPath
            }
            tryResolveWithExtensions(currentDir, normalizedPath)
        } else {
            val stacksBasePath = configService.getStacksBasePath() ?: return null
            val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return null
            tryResolveWithExtensions(stacksDir, importPath)
        }
    }

    private fun tryResolveWithExtensions(baseDir: VirtualFile, path: String): VirtualFile? {
        baseDir.findFileByRelativePath(path)?.let { return it }
        val extensions = listOf(".yaml", ".yml", ".yaml.tmpl", ".yml.tmpl")
        for (ext in extensions) {
            baseDir.findFileByRelativePath("$path$ext")?.let { return it }
        }
        return null
    }

    private fun getFilePreview(file: VirtualFile, maxLines: Int): String {
        return try {
            val content = String(file.contentsToByteArray())
            content.lines().take(maxLines).joinToString("\n")
        } catch (e: Exception) {
            "(Unable to read file)"
        }
    }

    private fun extractVariables(variablesTf: VirtualFile): String {
        return try {
            val content = String(variablesTf.contentsToByteArray())
            val pattern = """variable\s+"(\w+)"\s*\{[^}]*description\s*=\s*"([^"]*)"[^}]*\}""".toRegex(RegexOption.DOT_MATCHES_ALL)
            val matches = pattern.findAll(content).take(10)

            matches.map { match ->
                val name = match.groupValues[1]
                val desc = match.groupValues.getOrNull(2) ?: ""
                "- $name: $desc"
            }.joinToString("\n")
        } catch (e: Exception) {
            "(Unable to parse variables)"
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun getImportQuickInfo(element: PsiElement, projectService: AtmosProjectService): String {
        val scalar = element as? YAMLScalar ?: return ""
        val importPath = scalar.textValue
        val configService = AtmosConfigurationService.getInstance(element.project)
        val file = resolveImportFile(importPath, element, projectService, configService)

        return if (file != null) {
            "Import: ${file.name}"
        } else {
            "Import: $importPath (not found)"
        }
    }

    private fun getComponentQuickInfo(element: PsiElement, projectService: AtmosProjectService): String {
        val keyValue = element as? YAMLKeyValue ?: return ""
        return "Component: ${keyValue.keyText}"
    }
}
