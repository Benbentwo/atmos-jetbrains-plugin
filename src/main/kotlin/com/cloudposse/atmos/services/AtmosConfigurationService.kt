package com.cloudposse.atmos.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader

/**
 * Service for parsing and providing access to the Atmos configuration (atmos.yaml).
 */
@Service(Service.Level.PROJECT)
class AtmosConfigurationService(private val project: Project) {

    companion object {
        private val LOG = Logger.getInstance(AtmosConfigurationService::class.java)

        // Default paths as per Atmos conventions
        private const val DEFAULT_STACKS_BASE_PATH = "stacks"
        private const val DEFAULT_COMPONENTS_TERRAFORM_BASE_PATH = "components/terraform"
        private const val DEFAULT_COMPONENTS_HELMFILE_BASE_PATH = "components/helmfile"
        private const val DEFAULT_WORKFLOWS_BASE_PATH = "stacks/workflows"

        @JvmStatic
        fun getInstance(project: Project): AtmosConfigurationService = project.service()
    }

    private var cachedConfig: AtmosConfig? = null
    private var configFileModificationStamp: Long = -1

    /**
     * Gets the parsed Atmos configuration, reloading if the file has changed.
     */
    fun getConfig(): AtmosConfig? {
        val projectService = AtmosProjectService.getInstance(project)
        val configFile = projectService.findAtmosConfigFile() ?: return null

        // Check if we need to reload
        if (cachedConfig == null || configFile.modificationStamp != configFileModificationStamp) {
            cachedConfig = parseConfigFile(configFile)
            configFileModificationStamp = configFile.modificationStamp
        }

        return cachedConfig
    }

    /**
     * Gets the stacks base path from the configuration, or the default if not specified.
     */
    fun getStacksBasePath(): String? {
        return getConfig()?.stacks?.basePath ?: DEFAULT_STACKS_BASE_PATH
    }

    /**
     * Gets the Terraform components base path from the configuration.
     */
    fun getTerraformComponentsBasePath(): String? {
        return getConfig()?.components?.terraform?.basePath ?: DEFAULT_COMPONENTS_TERRAFORM_BASE_PATH
    }

    /**
     * Gets the Helmfile components base path from the configuration.
     */
    fun getHelmfileComponentsBasePath(): String? {
        return getConfig()?.components?.helmfile?.basePath ?: DEFAULT_COMPONENTS_HELMFILE_BASE_PATH
    }

    /**
     * Gets the workflows base path from the configuration.
     */
    fun getWorkflowsBasePath(): String? {
        return getConfig()?.workflows?.basePath ?: DEFAULT_WORKFLOWS_BASE_PATH
    }

    /**
     * Gets the included paths for stack files.
     */
    fun getStacksIncludedPaths(): List<String> {
        return getConfig()?.stacks?.includedPaths ?: listOf("**/*")
    }

    /**
     * Gets the excluded paths for stack files.
     */
    fun getStacksExcludedPaths(): List<String> {
        return getConfig()?.stacks?.excludedPaths ?: listOf("**/_defaults.yaml")
    }

    /**
     * Gets the stack name pattern for deriving stack names from file paths.
     */
    fun getStacksNamePattern(): String? {
        return getConfig()?.stacks?.namePattern
    }

    /**
     * Invalidates the cached configuration, forcing a reload on next access.
     */
    fun invalidateCache() {
        cachedConfig = null
        configFileModificationStamp = -1
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseConfigFile(configFile: VirtualFile): AtmosConfig? {
        return try {
            val yaml = Yaml()
            configFile.inputStream.use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val data = yaml.load<Map<String, Any?>>(reader) ?: return null
                    parseAtmosConfig(data)
                }
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse Atmos configuration file: ${configFile.path}", e)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseAtmosConfig(data: Map<String, Any?>): AtmosConfig {
        val basePath = data["base_path"] as? String ?: ""

        val stacks = (data["stacks"] as? Map<String, Any?>)?.let { stacksData ->
            StacksConfig(
                basePath = stacksData["base_path"] as? String ?: DEFAULT_STACKS_BASE_PATH,
                includedPaths = (stacksData["included_paths"] as? List<String>) ?: listOf("**/*"),
                excludedPaths = (stacksData["excluded_paths"] as? List<String>) ?: emptyList(),
                namePattern = stacksData["name_pattern"] as? String
            )
        } ?: StacksConfig(basePath = DEFAULT_STACKS_BASE_PATH)

        val components = (data["components"] as? Map<String, Any?>)?.let { componentsData ->
            val terraform = (componentsData["terraform"] as? Map<String, Any?>)?.let { tfData ->
                ComponentTypeConfig(basePath = tfData["base_path"] as? String ?: DEFAULT_COMPONENTS_TERRAFORM_BASE_PATH)
            } ?: ComponentTypeConfig(basePath = DEFAULT_COMPONENTS_TERRAFORM_BASE_PATH)

            val helmfile = (componentsData["helmfile"] as? Map<String, Any?>)?.let { hfData ->
                ComponentTypeConfig(basePath = hfData["base_path"] as? String ?: DEFAULT_COMPONENTS_HELMFILE_BASE_PATH)
            } ?: ComponentTypeConfig(basePath = DEFAULT_COMPONENTS_HELMFILE_BASE_PATH)

            ComponentsConfig(terraform = terraform, helmfile = helmfile)
        } ?: ComponentsConfig()

        val workflows = (data["workflows"] as? Map<String, Any?>)?.let { workflowsData ->
            WorkflowsConfig(basePath = workflowsData["base_path"] as? String ?: DEFAULT_WORKFLOWS_BASE_PATH)
        } ?: WorkflowsConfig(basePath = DEFAULT_WORKFLOWS_BASE_PATH)

        return AtmosConfig(
            basePath = basePath,
            stacks = stacks,
            components = components,
            workflows = workflows
        )
    }
}

/**
 * Data class representing the Atmos configuration structure.
 */
data class AtmosConfig(
    val basePath: String = "",
    val stacks: StacksConfig = StacksConfig(),
    val components: ComponentsConfig = ComponentsConfig(),
    val workflows: WorkflowsConfig = WorkflowsConfig()
)

data class StacksConfig(
    val basePath: String = "stacks",
    val includedPaths: List<String> = listOf("**/*"),
    val excludedPaths: List<String> = emptyList(),
    val namePattern: String? = null
)

data class ComponentsConfig(
    val terraform: ComponentTypeConfig = ComponentTypeConfig(basePath = "components/terraform"),
    val helmfile: ComponentTypeConfig = ComponentTypeConfig(basePath = "components/helmfile")
)

data class ComponentTypeConfig(
    val basePath: String
)

data class WorkflowsConfig(
    val basePath: String = "stacks/workflows"
)
