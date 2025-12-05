package com.cloudposse.atmos.gutter

import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.daemon.NavigateAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import javax.swing.Icon

/**
 * Provides gutter icons for Atmos stack files.
 * Shows visual indicators for:
 * - Components that inherit from other components
 * - Components that are inherited by others
 * - Import chains
 * - Abstract vs. real components
 */
class AtmosLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        // Only process leaf elements to avoid duplicate markers
        if (element.firstChild != null && element !is YAMLKeyValue) return null

        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return null

        val containingFile = element.containingFile?.virtualFile
        if (containingFile == null ||
            (!projectService.isStackFile(containingFile) && !projectService.isStackTemplateFile(containingFile))) {
            return null
        }

        if (element is YAMLKeyValue) {
            // Check for import block
            if (element.keyText == "import") {
                return createImportMarker(element)
            }

            // Check for component with inheritance
            if (isComponentKey(element)) {
                val inheritanceMarker = createInheritanceMarker(element)
                if (inheritanceMarker != null) return inheritanceMarker

                return createComponentTypeMarker(element)
            }

            // Check for metadata.inherits
            if (element.keyText == "inherits" && isWithinMetadata(element)) {
                return createInheritsFromMarker(element)
            }
        }

        return null
    }

    private fun isComponentKey(element: YAMLKeyValue): Boolean {
        val parent = element.parent?.parent
        if (parent is YAMLKeyValue) {
            val grandParent = parent.parent?.parent
            if (grandParent is YAMLKeyValue && grandParent.keyText == "components") {
                return parent.keyText in listOf("terraform", "helmfile")
            }
        }
        return false
    }

    private fun isWithinMetadata(element: YAMLKeyValue): Boolean {
        val parent = element.parent?.parent
        return parent is YAMLKeyValue && parent.keyText == "metadata"
    }

    private fun createImportMarker(element: YAMLKeyValue): LineMarkerInfo<*>? {
        val importList = element.value as? YAMLSequence ?: return null
        val importCount = importList.items.size

        if (importCount == 0) return null

        val key = element.key ?: return null

        return LineMarkerInfo(
            key,
            key.textRange,
            AtmosIcons.IMPORT,
            { "Imports $importCount file${if (importCount > 1) "s" else ""}" },
            null,
            GutterIconRenderer.Alignment.LEFT
        ) { "Atmos Imports" }
    }

    private fun createInheritanceMarker(element: YAMLKeyValue): LineMarkerInfo<*>? {
        val componentMapping = element.value as? YAMLMapping ?: return null
        val metadataKv = componentMapping.getKeyValueByKey("metadata") ?: return null
        val metadata = metadataKv.value as? YAMLMapping ?: return null

        // Check for inherits
        val inheritsKv = metadata.getKeyValueByKey("inherits")
        if (inheritsKv != null) {
            val key = element.key ?: return null
            val inherits = getInheritsList(inheritsKv)
            val inheritCount = inherits.size

            return LineMarkerInfo(
                key,
                key.textRange,
                AtmosIcons.INHERIT_UP,
                { "Inherits from: ${inherits.joinToString(", ")}" },
                { _, _ ->
                    // Navigate to first inherited component
                    // This would need implementation to find the actual files
                },
                GutterIconRenderer.Alignment.LEFT
            ) { "Inherits from $inheritCount component${if (inheritCount > 1) "s" else ""}" }
        }

        return null
    }

    private fun createComponentTypeMarker(element: YAMLKeyValue): LineMarkerInfo<*>? {
        val componentMapping = element.value as? YAMLMapping ?: return null
        val metadataKv = componentMapping.getKeyValueByKey("metadata")
        val metadata = metadataKv?.value as? YAMLMapping

        val typeValue = metadata?.getKeyValueByKey("type")?.value as? YAMLScalar
        val isAbstract = typeValue?.textValue == "abstract"

        val icon: Icon = if (isAbstract) AtmosIcons.ABSTRACT_COMPONENT else AtmosIcons.REAL_COMPONENT
        val typeLabel = if (isAbstract) "Abstract" else "Real"

        val key = element.key ?: return null

        return LineMarkerInfo(
            key,
            key.textRange,
            icon,
            { "$typeLabel component: ${element.keyText}" },
            null,
            GutterIconRenderer.Alignment.RIGHT
        ) { "$typeLabel Component" }
    }

    private fun createInheritsFromMarker(element: YAMLKeyValue): LineMarkerInfo<*>? {
        val inherits = getInheritsList(element)
        if (inherits.isEmpty()) return null

        val key = element.key ?: return null

        return LineMarkerInfo(
            key,
            key.textRange,
            AtmosIcons.INHERIT_UP,
            { "Inherits from: ${inherits.joinToString(", ")}" },
            null,
            GutterIconRenderer.Alignment.LEFT
        ) { "Inheritance" }
    }

    private fun getInheritsList(element: YAMLKeyValue): List<String> {
        return when (val value = element.value) {
            is YAMLSequence -> value.items.mapNotNull { item ->
                (item.value as? YAMLScalar)?.textValue
            }
            is YAMLScalar -> listOf(value.textValue)
            else -> emptyList()
        }
    }
}

/**
 * Provides additional gutter icons for variables that override inherited values.
 */
class AtmosVariableOverrideLineMarkerProvider : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element !is YAMLKeyValue) return null

        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return null

        val containingFile = element.containingFile?.virtualFile
        if (containingFile == null ||
            (!projectService.isStackFile(containingFile) && !projectService.isStackTemplateFile(containingFile))) {
            return null
        }

        // Check if this is a var within a component's vars block
        if (!isWithinVarsBlock(element)) return null

        // Check if this variable overrides a parent value
        // This is a simplified check - a full implementation would trace the inheritance chain
        val componentContext = getComponentContext(element) ?: return null
        if (componentContext.hasInheritance) {
            val key = element.key ?: return null

            return LineMarkerInfo(
                key,
                key.textRange,
                AtmosIcons.INHERIT_UP,
                { "May override inherited value for '${element.keyText}'" },
                null,
                GutterIconRenderer.Alignment.LEFT
            ) { "Variable Override" }
        }

        return null
    }

    private fun isWithinVarsBlock(element: YAMLKeyValue): Boolean {
        var current: PsiElement? = element.parent
        while (current != null) {
            if (current is YAMLKeyValue && current.keyText == "vars") {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun getComponentContext(element: YAMLKeyValue): ComponentContext? {
        var current: PsiElement? = element.parent
        var foundVars = false

        while (current != null) {
            if (current is YAMLKeyValue) {
                when (current.keyText) {
                    "vars" -> foundVars = true
                    "terraform", "helmfile" -> break
                    else -> {
                        if (foundVars) {
                            // This is the component
                            val componentMapping = current.value as? YAMLMapping ?: return null
                            val metadataKv = componentMapping.getKeyValueByKey("metadata")
                            val metadata = metadataKv?.value as? YAMLMapping

                            val inheritsKv = metadata?.getKeyValueByKey("inherits")
                            return ComponentContext(
                                componentName = current.keyText,
                                hasInheritance = inheritsKv != null
                            )
                        }
                    }
                }
            }
            current = current.parent
        }
        return null
    }

    data class ComponentContext(
        val componentName: String,
        val hasInheritance: Boolean
    )
}
