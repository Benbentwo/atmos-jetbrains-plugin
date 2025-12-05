package com.cloudposse.atmos.actions

import com.cloudposse.atmos.services.AtmosProjectService
import com.cloudposse.atmos.settings.AtmosSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.Icon

/**
 * Base class for Atmos actions.
 * Provides common functionality like checking if we're in an Atmos project.
 */
abstract class AtmosBaseAction(
    text: String? = null,
    description: String? = null,
    icon: Icon? = null
) : AnAction(text, description, icon) {

    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.BGT
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null &&
            AtmosProjectService.getInstance(project).isAtmosProject &&
            AtmosSettings.getInstance().isAtmosAvailable()
    }

    protected fun isAtmosAvailable(e: AnActionEvent): Boolean {
        val project = e.project ?: return false
        return AtmosProjectService.getInstance(project).isAtmosProject &&
            AtmosSettings.getInstance().isAtmosAvailable()
    }
}
