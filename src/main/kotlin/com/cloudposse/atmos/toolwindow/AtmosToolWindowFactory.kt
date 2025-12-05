package com.cloudposse.atmos.toolwindow

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the Atmos tool window.
 */
class AtmosToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val atmosPanel = AtmosToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(atmosPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return AtmosProjectService.getInstance(project).isAtmosProject
    }
}
