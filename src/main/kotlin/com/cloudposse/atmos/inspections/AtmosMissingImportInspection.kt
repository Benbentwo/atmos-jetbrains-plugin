package com.cloudposse.atmos.inspections

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

/**
 * Inspection that checks for missing import files.
 * Reports an error when an import path doesn't resolve to an existing file.
 */
class AtmosMissingImportInspection : AtmosInspectionBase() {

    override fun getDisplayName(): String = "Missing Atmos import"

    override fun getShortName(): String = "AtmosMissingImport"

    override fun getGroupDisplayName(): String = "Atmos"

    override fun createVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitKeyValue(keyValue: YAMLKeyValue) {
                if (keyValue.keyText != "import") return

                val sequence = keyValue.value as? YAMLSequence ?: return

                val project = keyValue.project
                val projectService = AtmosProjectService.getInstance(project)
                val configService = AtmosConfigurationService.getInstance(project)

                sequence.items.forEach { item ->
                    val scalar = item.value as? YAMLScalar ?: return@forEach
                    val importPath = scalar.textValue

                    if (importPath.isBlank()) return@forEach

                    // Skip remote imports (git://, https://, s3://)
                    if (isRemoteImport(importPath)) return@forEach

                    if (!resolveImportFile(importPath, scalar, projectService, configService)) {
                        holder.registerProblem(
                            scalar,
                            "Cannot resolve import '$importPath'",
                            ProblemHighlightType.ERROR,
                            CreateStackFileQuickFix(importPath)
                        )
                    }
                }
            }
        }
    }

    private fun isRemoteImport(path: String): Boolean {
        return path.startsWith("git://") ||
                path.startsWith("https://") ||
                path.startsWith("http://") ||
                path.startsWith("s3://")
    }

    private fun resolveImportFile(
        importPath: String,
        element: PsiElement,
        projectService: AtmosProjectService,
        configService: AtmosConfigurationService
    ): Boolean {
        val currentFile = element.containingFile?.virtualFile ?: return false
        val isRelative = importPath.startsWith(".") || importPath.startsWith("/")

        return if (isRelative) {
            resolveRelativeImport(importPath, currentFile)
        } else {
            resolveBaseRelativeImport(importPath, projectService, configService)
        }
    }

    private fun resolveRelativeImport(importPath: String, currentFile: VirtualFile): Boolean {
        val currentDir = currentFile.parent ?: return false
        val normalizedPath = if (importPath.startsWith("./")) {
            importPath.substring(2)
        } else {
            importPath
        }
        return tryResolveWithExtensions(currentDir, normalizedPath)
    }

    private fun resolveBaseRelativeImport(
        importPath: String,
        projectService: AtmosProjectService,
        configService: AtmosConfigurationService
    ): Boolean {
        val stacksBasePath = configService.getStacksBasePath() ?: return false
        val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return false
        return tryResolveWithExtensions(stacksDir, importPath)
    }

    private fun tryResolveWithExtensions(baseDir: VirtualFile, path: String): Boolean {
        if (baseDir.findFileByRelativePath(path) != null) return true

        val extensions = listOf(".yaml", ".yml", ".yaml.tmpl", ".yml.tmpl")
        return extensions.any { ext ->
            baseDir.findFileByRelativePath("$path$ext") != null
        }
    }
}

/**
 * Quick fix to create a missing stack file.
 */
class CreateStackFileQuickFix(private val importPath: String) : LocalQuickFix {

    override fun getName(): String = "Create stack file '$importPath.yaml'"

    override fun getFamilyName(): String = "Create Atmos stack file"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val configService = AtmosConfigurationService.getInstance(project)
        val projectService = AtmosProjectService.getInstance(project)

        val stacksBasePath = configService.getStacksBasePath() ?: return
        val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return

        // Determine the full path for the new file
        val isRelative = importPath.startsWith(".") || importPath.startsWith("/")
        val baseDir = if (isRelative) {
            descriptor.psiElement.containingFile?.virtualFile?.parent
        } else {
            stacksDir
        } ?: return

        val normalizedPath = if (importPath.startsWith("./")) {
            importPath.substring(2)
        } else {
            importPath
        }

        // Create parent directories if needed
        val pathParts = normalizedPath.split("/")
        var currentDir = baseDir

        for (i in 0 until pathParts.size - 1) {
            val dirName = pathParts[i]
            currentDir = currentDir.findChild(dirName) ?: run {
                currentDir.createChildDirectory(this, dirName)
            }
        }

        // Create the file
        val fileName = "${pathParts.last()}.yaml"
        val newFile = currentDir.createChildData(this, fileName)

        // Add default content
        val defaultContent = """
            |# Stack file: $importPath
            |# Created by Atmos plugin
            |
            |vars: {}
            |
            |components:
            |  terraform: {}
        """.trimMargin()

        newFile.setBinaryContent(defaultContent.toByteArray())

        // Open the file in editor
        val psiFile = PsiManager.getInstance(project).findFile(newFile)
        psiFile?.navigate(true)
    }
}
