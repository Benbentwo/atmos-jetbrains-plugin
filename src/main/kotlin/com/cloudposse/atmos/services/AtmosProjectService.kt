package com.cloudposse.atmos.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import java.nio.file.Path

/**
 * Service for detecting and managing Atmos projects.
 * An Atmos project is identified by the presence of an atmos.yaml file.
 */
@Service(Service.Level.PROJECT)
class AtmosProjectService(private val project: Project) {

    companion object {
        private const val ATMOS_CONFIG_FILE = "atmos.yaml"
        private const val ATMOS_CONFIG_FILE_ALT = "atmos.yml"

        @JvmStatic
        fun getInstance(project: Project): AtmosProjectService = project.service()
    }

    /**
     * Returns true if this project contains an Atmos configuration file.
     */
    val isAtmosProject: Boolean
        get() = findAtmosConfigFile() != null

    /**
     * Gets the Atmos configuration file (atmos.yaml or atmos.yml) if it exists.
     */
    fun findAtmosConfigFile(): VirtualFile? {
        val basePath = project.basePath ?: return null
        val baseDir = VirtualFileManager.getInstance().findFileByNioPath(Path.of(basePath))
            ?: return null

        // Check for atmos.yaml first, then atmos.yml
        return baseDir.findChild(ATMOS_CONFIG_FILE)
            ?: baseDir.findChild(ATMOS_CONFIG_FILE_ALT)
    }

    /**
     * Gets the project base directory as a VirtualFile.
     */
    fun getProjectBaseDir(): VirtualFile? {
        val basePath = project.basePath ?: return null
        return VirtualFileManager.getInstance().findFileByNioPath(Path.of(basePath))
    }

    /**
     * Resolves a path relative to the project base directory.
     */
    fun resolveProjectPath(relativePath: String): VirtualFile? {
        val baseDir = getProjectBaseDir() ?: return null
        return baseDir.findFileByRelativePath(relativePath)
    }

    /**
     * Checks if a file is within the stacks directory.
     */
    fun isStackFile(file: VirtualFile): Boolean {
        val configService = AtmosConfigurationService.getInstance(project)
        val stacksBasePath = configService.getStacksBasePath() ?: return false
        val stacksDir = resolveProjectPath(stacksBasePath) ?: return false

        return isChildOf(file, stacksDir) && (file.extension == "yaml" || file.extension == "yml")
    }

    /**
     * Checks if a file is a stack template file (.yaml.tmpl or .yml.tmpl).
     */
    fun isStackTemplateFile(file: VirtualFile): Boolean {
        val configService = AtmosConfigurationService.getInstance(project)
        val stacksBasePath = configService.getStacksBasePath() ?: return false
        val stacksDir = resolveProjectPath(stacksBasePath) ?: return false

        return isChildOf(file, stacksDir) && file.name.let {
            it.endsWith(".yaml.tmpl") || it.endsWith(".yml.tmpl")
        }
    }

    private fun isChildOf(file: VirtualFile, parent: VirtualFile): Boolean {
        var current: VirtualFile? = file
        while (current != null) {
            if (current == parent) return true
            current = current.parent
        }
        return false
    }
}
