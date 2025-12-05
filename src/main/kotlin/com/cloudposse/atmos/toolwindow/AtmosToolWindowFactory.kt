package com.cloudposse.atmos.toolwindow

import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Atmos tool window.
 *
 * The tool window displays:
 * - Stacks tree: Hierarchical view of all stacks and their components
 * - Components tree: All available components with their types
 * - Workflows: Available workflows from workflows.base_path
 * - Console: Output from recent Atmos commands
 */
class AtmosToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AtmosToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return AtmosProjectService.getInstance(project).isAtmosProject
    }
}
