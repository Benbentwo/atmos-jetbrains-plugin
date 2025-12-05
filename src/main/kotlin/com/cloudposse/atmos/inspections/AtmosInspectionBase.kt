package com.cloudposse.atmos.inspections

import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

/**
 * Base class for Atmos inspections.
 * Provides common functionality for checking if a file is an Atmos stack file.
 */
abstract class AtmosInspectionBase : LocalInspectionTool() {

    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        val file = holder.file
        if (!isAtmosStackFile(file)) {
            return PsiElementVisitor.EMPTY_VISITOR
        }

        return createVisitor(holder, isOnTheFly)
    }

    protected abstract fun createVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor

    private fun isAtmosStackFile(file: PsiFile): Boolean {
        val project = file.project
        val projectService = AtmosProjectService.getInstance(project)

        if (!projectService.isAtmosProject) return false

        val virtualFile = file.virtualFile ?: return false
        return projectService.isStackFile(virtualFile) || projectService.isStackTemplateFile(virtualFile)
    }
}
