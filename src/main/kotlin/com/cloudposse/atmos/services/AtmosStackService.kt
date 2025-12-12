package com.cloudposse.atmos.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

/**
 * Service for deriving stack names and understanding stack structure.
 * 
 * A stack in Atmos is defined by:
 * - tenant (optional)
 * - environment 
 * - stage
 * 
 * Multiple files can define the same logical stack by having the same
 * tenant/environment/stage combination.
 */
@Service(Service.Level.PROJECT)
class AtmosStackService(private val project: Project) {

    companion object {
        @JvmStatic
        fun getInstance(project: Project): AtmosStackService = project.service()
    }

    /**
     * Derives the stack name from a stack file path using the atmos.yaml configuration.
     * 
     * The stack name is derived from the file path relative to the stacks base path,
     * and may use a name pattern defined in atmos.yaml.
     */
    fun deriveStackName(file: VirtualFile): String? {
        val projectService = AtmosProjectService.getInstance(project)
        val configService = AtmosConfigurationService.getInstance(project)
        
        val stacksBasePath = configService.getStacksBasePath() ?: return null
        val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return null
        
        // Get relative path from stacks directory
        val stacksDirPath = Path.of(stacksDir.path)
        val filePath = Path.of(file.path)
        
        if (!filePath.startsWith(stacksDirPath)) {
            return null
        }
        
        val relativePath = filePath.relativeTo(stacksDirPath)
        val namePattern = configService.getStacksNamePattern()
        
        return if (namePattern != null) {
            deriveStackNameWithPattern(relativePath, namePattern)
        } else {
            deriveStackNameFromPath(relativePath)
        }
    }
    
    /**
     * Derives stack name using a configured name pattern.
     * 
     * Name patterns can include placeholders like:
     * - {tenant}
     * - {environment} 
     * - {stage}
     * 
     * These are extracted from the file path structure.
     */
    private fun deriveStackNameWithPattern(relativePath: Path, namePattern: String): String? {
        val pathParts = relativePath.pathString.split('/').filter { it.isNotEmpty() }
        
        // Remove file extension from the last part
        val lastPart = pathParts.lastOrNull()?.let {
            when {
                it.endsWith(".yaml") -> it.removeSuffix(".yaml")
                it.endsWith(".yml") -> it.removeSuffix(".yml")
                it.endsWith(".yaml.tmpl") -> it.removeSuffix(".yaml.tmpl")
                it.endsWith(".yml.tmpl") -> it.removeSuffix(".yml.tmpl")
                else -> it
            }
        } ?: return null
        
        val modifiedParts = pathParts.dropLast(1) + listOf(lastPart)
        
        // Try to extract tenant/environment/stage from path
        // This is a simplified implementation - in practice, this would need
        // more sophisticated pattern matching based on the actual namePattern
        return when (modifiedParts.size) {
            1 -> modifiedParts[0] // Just the filename
            2 -> "${modifiedParts[0]}-${modifiedParts[1]}" // environment-stage
            3 -> "${modifiedParts[0]}-${modifiedParts[1]}-${modifiedParts[2]}" // tenant-environment-stage
            else -> modifiedParts.joinToString("-")
        }
    }
    
    /**
     * Derives stack name from file path without a specific pattern.
     * Uses the file path structure relative to stacks base path.
     */
    private fun deriveStackNameFromPath(relativePath: Path): String {
        val pathString = relativePath.pathString
        
        // Remove file extension
        val withoutExtension = when {
            pathString.endsWith(".yaml") -> pathString.removeSuffix(".yaml")
            pathString.endsWith(".yml") -> pathString.removeSuffix(".yml")
            pathString.endsWith(".yaml.tmpl") -> pathString.removeSuffix(".yaml.tmpl")
            pathString.endsWith(".yml.tmpl") -> pathString.removeSuffix(".yml.tmpl")
            else -> pathString
        }
        
        // Replace path separators with dashes to create a flat stack name
        return withoutExtension.replace('/', '-').replace('\\', '-')
    }
    
    /**
     * Checks if the given file path should be considered a stack file
     * based on the inclusion and exclusion patterns in atmos.yaml.
     */
    fun isStackFile(file: VirtualFile): Boolean {
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isStackFile(file)) {
            return false
        }
        
        val configService = AtmosConfigurationService.getInstance(project)
        val stacksBasePath = configService.getStacksBasePath() ?: return false
        val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return false
        
        // Get relative path from stacks directory
        val stacksDirPath = Path.of(stacksDir.path)
        val filePath = Path.of(file.path)
        
        if (!filePath.startsWith(stacksDirPath)) {
            return false
        }
        
        val relativePath = filePath.relativeTo(stacksDirPath).pathString
        
        // Check inclusion patterns
        val includedPaths = configService.getStacksIncludedPaths()
        val included = includedPaths.any { pattern ->
            matchesPattern(relativePath, pattern)
        }
        
        if (!included) return false
        
        // Check exclusion patterns
        val excludedPaths = configService.getStacksExcludedPaths()
        val excluded = excludedPaths.any { pattern ->
            matchesPattern(relativePath, pattern)
        }
        
        return !excluded
    }
    
    /**
     * Simple glob pattern matching.
     * Supports ** for recursive directory matching and * for single level matching.
     */
    private fun matchesPattern(path: String, pattern: String): Boolean {
        // Convert glob pattern to regex
        val regexPattern = pattern
            .replace("**", "DOUBLE_STAR")
            .replace("*", "[^/]*")
            .replace("DOUBLE_STAR", ".*")
        
        return Regex("^$regexPattern$").matches(path)
    }
    
    /**
     * Extracts stack metadata (tenant, environment, stage) from a stack name.
     * This is used for better stack organization and understanding.
     */
    fun extractStackMetadata(stackName: String): StackMetadata {
        // Split on dashes and try to identify tenant/environment/stage
        val parts = stackName.split('-')
        
        return when (parts.size) {
            1 -> StackMetadata(
                tenant = null,
                environment = null,
                stage = parts[0]
            )
            2 -> StackMetadata(
                tenant = null,
                environment = parts[0],
                stage = parts[1]
            )
            3 -> StackMetadata(
                tenant = parts[0],
                environment = parts[1],
                stage = parts[2]
            )
            else -> StackMetadata(
                tenant = parts.dropLast(2).joinToString("-"),
                environment = parts[parts.size - 2],
                stage = parts.last()
            )
        }
    }
}

/**
 * Represents the metadata that defines a stack in Atmos.
 */
data class StackMetadata(
    val tenant: String?,
    val environment: String?,
    val stage: String
) {
    /**
     * Returns the full stack identifier.
     */
    fun getFullName(): String {
        return listOfNotNull(tenant, environment, stage).joinToString("-")
    }
    
    /**
     * Returns a human-readable description of the stack.
     */
    fun getDisplayName(): String {
        val parts = mutableListOf<String>()
        tenant?.let { parts.add("Tenant: $it") }
        environment?.let { parts.add("Environment: $it") }
        parts.add("Stage: $stage")
        return parts.joinToString(", ")
    }
}