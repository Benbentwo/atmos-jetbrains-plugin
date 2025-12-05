package com.cloudposse.atmos.inspections

import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementVisitor
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLSequence
import org.jetbrains.yaml.psi.YamlPsiElementVisitor
import org.yaml.snakeyaml.Yaml
import java.io.InputStreamReader

/**
 * Inspection that detects circular imports in Atmos stack files.
 * Reports an error when an import chain creates a cycle.
 */
class AtmosCircularImportInspection : AtmosInspectionBase() {

    override fun getDisplayName(): String = "Circular Atmos import"

    override fun getShortName(): String = "AtmosCircularImport"

    override fun getGroupDisplayName(): String = "Atmos"

    override fun createVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitKeyValue(keyValue: YAMLKeyValue) {
                if (keyValue.keyText != "import") return

                val sequence = keyValue.value as? YAMLSequence ?: return

                val project = keyValue.project
                val projectService = AtmosProjectService.getInstance(project)
                val configService = AtmosConfigurationService.getInstance(project)

                val currentFile = keyValue.containingFile?.virtualFile ?: return

                sequence.items.forEach { item ->
                    val scalar = item.value as? YAMLScalar ?: return@forEach
                    val importPath = scalar.textValue

                    if (importPath.isBlank()) return@forEach

                    // Skip remote imports
                    if (isRemoteImport(importPath)) return@forEach

                    // Check for circular imports
                    val cycle = detectCircularImport(
                        importPath,
                        currentFile,
                        projectService,
                        configService,
                        mutableSetOf(currentFile.path)
                    )

                    if (cycle != null) {
                        holder.registerProblem(
                            scalar,
                            "Circular import detected: ${formatCycle(cycle)}",
                            ProblemHighlightType.ERROR
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

    private fun detectCircularImport(
        importPath: String,
        currentFile: VirtualFile,
        projectService: AtmosProjectService,
        configService: AtmosConfigurationService,
        visited: MutableSet<String>
    ): List<String>? {
        val resolvedFile = resolveImportFile(importPath, currentFile, projectService, configService)
            ?: return null

        if (resolvedFile.path in visited) {
            // Found a cycle
            return listOf(currentFile.name, resolvedFile.name)
        }

        visited.add(resolvedFile.path)

        // Parse the imported file and check its imports
        val imports = parseImports(resolvedFile)
        for (nestedImport in imports) {
            if (isRemoteImport(nestedImport)) continue

            val cycle = detectCircularImport(nestedImport, resolvedFile, projectService, configService, visited)
            if (cycle != null) {
                return listOf(currentFile.name) + cycle
            }
        }

        visited.remove(resolvedFile.path)
        return null
    }

    private fun resolveImportFile(
        importPath: String,
        currentFile: VirtualFile,
        projectService: AtmosProjectService,
        configService: AtmosConfigurationService
    ): VirtualFile? {
        val isRelative = importPath.startsWith(".") || importPath.startsWith("/")

        return if (isRelative) {
            val currentDir = currentFile.parent ?: return null
            val normalizedPath = if (importPath.startsWith("./")) {
                importPath.substring(2)
            } else {
                importPath
            }
            tryResolveWithExtensions(currentDir, normalizedPath)
        } else {
            val stacksBasePath = configService.getStacksBasePath() ?: return null
            val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return null
            tryResolveWithExtensions(stacksDir, importPath)
        }
    }

    private fun tryResolveWithExtensions(baseDir: VirtualFile, path: String): VirtualFile? {
        baseDir.findFileByRelativePath(path)?.let { return it }

        val extensions = listOf(".yaml", ".yml", ".yaml.tmpl", ".yml.tmpl")
        for (ext in extensions) {
            baseDir.findFileByRelativePath("$path$ext")?.let { return it }
        }

        return null
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseImports(file: VirtualFile): List<String> {
        return try {
            val yaml = Yaml()
            file.inputStream.use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val data = yaml.load<Map<String, Any?>>(reader) ?: return emptyList()
                    val imports = data["import"] ?: return emptyList()

                    when (imports) {
                        is List<*> -> imports.filterIsInstance<String>()
                        is String -> listOf(imports)
                        else -> emptyList()
                    }
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun formatCycle(cycle: List<String>): String {
        return cycle.joinToString(" → ") + " → ${cycle.first()}"
    }
}
