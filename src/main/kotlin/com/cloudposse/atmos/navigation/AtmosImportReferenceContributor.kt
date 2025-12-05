package com.cloudposse.atmos.navigation

import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.*
import com.intellij.util.ProcessingContext
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLSequenceItem
import org.jetbrains.yaml.psi.YAMLScalar

/**
 * Provides references for import paths in Atmos stack files.
 * Enables Cmd+Click navigation on import entries to open the referenced file.
 */
class AtmosImportReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        // Register provider for YAML scalar values (string values in YAML)
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(YAMLScalar::class.java),
            AtmosImportReferenceProvider()
        )
    }
}

/**
 * Provides references for import paths when the element is within an 'import' block.
 */
class AtmosImportReferenceProvider : PsiReferenceProvider() {

    override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
        if (element !is YAMLScalar) return PsiReference.EMPTY_ARRAY

        // Check if this is within an 'import' block
        if (!isWithinImportBlock(element)) return PsiReference.EMPTY_ARRAY

        // Verify this is an Atmos project
        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return PsiReference.EMPTY_ARRAY

        // Also check if the current file is a stack file
        val containingFile = element.containingFile?.virtualFile
        if (containingFile != null && !projectService.isStackFile(containingFile) &&
            !projectService.isStackTemplateFile(containingFile)) {
            return PsiReference.EMPTY_ARRAY
        }

        val importPath = element.textValue
        if (importPath.isBlank()) return PsiReference.EMPTY_ARRAY

        return arrayOf(AtmosImportReference(element, importPath))
    }

    private fun isWithinImportBlock(element: PsiElement): Boolean {
        // Walk up the tree to find if we're in an import block
        var current: PsiElement? = element

        while (current != null) {
            // Check if parent is a sequence item under an 'import' key
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
}

/**
 * Reference for an import path that resolves to a stack file.
 */
class AtmosImportReference(
    element: YAMLScalar,
    private val importPath: String
) : PsiReferenceBase<YAMLScalar>(element, TextRange(0, element.textLength), true) {

    override fun resolve(): PsiElement? {
        val project = element.project
        val file = resolveImportFile() ?: return null
        return PsiManager.getInstance(project).findFile(file)
    }

    override fun getVariants(): Array<Any> {
        // Completion will be handled by a separate completion contributor
        return emptyArray()
    }

    /**
     * Resolves the import path to a VirtualFile.
     * Supports:
     * - Relative paths (starting with . or ..)
     * - Base-relative paths (resolved from stacks.base_path)
     */
    fun resolveImportFile(): VirtualFile? {
        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        val configService = AtmosConfigurationService.getInstance(project)

        val currentFile = element.containingFile?.virtualFile ?: return null

        // Determine if this is a relative or base-relative path
        val isRelative = importPath.startsWith(".") || importPath.startsWith("/")

        return if (isRelative) {
            resolveRelativeImport(currentFile)
        } else {
            resolveBaseRelativeImport(projectService, configService)
        }
    }

    private fun resolveRelativeImport(currentFile: VirtualFile): VirtualFile? {
        val currentDir = currentFile.parent ?: return null

        // Handle relative paths
        val normalizedPath = if (importPath.startsWith("./")) {
            importPath.substring(2)
        } else {
            importPath
        }

        return tryResolveWithExtensions(currentDir, normalizedPath)
    }

    private fun resolveBaseRelativeImport(
        projectService: AtmosProjectService,
        configService: AtmosConfigurationService
    ): VirtualFile? {
        val stacksBasePath = configService.getStacksBasePath() ?: return null
        val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return null

        return tryResolveWithExtensions(stacksDir, importPath)
    }

    private fun tryResolveWithExtensions(baseDir: VirtualFile, path: String): VirtualFile? {
        // First try the exact path
        baseDir.findFileByRelativePath(path)?.let { return it }

        // Try with common extensions
        val extensions = listOf(".yaml", ".yml", ".yaml.tmpl", ".yml.tmpl")
        for (ext in extensions) {
            baseDir.findFileByRelativePath("$path$ext")?.let { return it }
        }

        return null
    }
}
