package com.cloudposse.atmos.inspections

import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiManager
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YamlPsiElementVisitor

/**
 * Inspection that checks for unknown component references.
 * Reports an error when a component name doesn't match any component in the components directory.
 */
class AtmosUnknownComponentInspection : AtmosInspectionBase() {

    override fun getDisplayName(): String = "Unknown Atmos component"

    override fun getShortName(): String = "AtmosUnknownComponent"

    override fun getGroupDisplayName(): String = "Atmos"

    override fun createVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : YamlPsiElementVisitor() {
            override fun visitKeyValue(keyValue: YAMLKeyValue) {
                val project = keyValue.project
                val projectService = AtmosProjectService.getInstance(project)
                val configService = AtmosConfigurationService.getInstance(project)

                // Check if this is components.terraform or components.helmfile
                val componentType = getComponentType(keyValue) ?: return

                // Get the components under this key
                val componentsMapping = keyValue.value as? YAMLMapping ?: return

                val basePath = when (componentType) {
                    ComponentType.TERRAFORM -> configService.getTerraformComponentsBasePath()
                    ComponentType.HELMFILE -> configService.getHelmfileComponentsBasePath()
                } ?: return

                val componentsDir = projectService.resolveProjectPath(basePath) ?: return

                componentsMapping.keyValues.forEach { componentKv ->
                    val componentName = componentKv.keyText
                    val componentMapping = componentKv.value as? YAMLMapping

                    // Check for metadata.component override
                    val metadataComponent = componentMapping?.getKeyValueByKey("metadata")
                        ?.value?.let { it as? YAMLMapping }
                        ?.getKeyValueByKey("component")
                        ?.value?.let { it as? YAMLScalar }
                        ?.textValue

                    val componentPath = metadataComponent ?: componentName

                    // Check if this is an abstract component (doesn't need to exist)
                    val isAbstract = componentMapping?.getKeyValueByKey("metadata")
                        ?.value?.let { it as? YAMLMapping }
                        ?.getKeyValueByKey("type")
                        ?.value?.let { it as? YAMLScalar }
                        ?.textValue == "abstract"

                    if (!isAbstract && !componentExists(componentsDir, componentPath, componentType)) {
                        val key = componentKv.key ?: return@forEach
                        holder.registerProblem(
                            key,
                            "Cannot find component '$componentPath' in ${basePath}",
                            ProblemHighlightType.WARNING,
                            CreateComponentQuickFix(componentPath, componentType, basePath)
                        )
                    }
                }
            }
        }
    }

    private fun getComponentType(keyValue: YAMLKeyValue): ComponentType? {
        if (keyValue.keyText !in listOf("terraform", "helmfile")) return null

        val parent = keyValue.parent?.parent
        if (parent is YAMLKeyValue && parent.keyText == "components") {
            return when (keyValue.keyText) {
                "terraform" -> ComponentType.TERRAFORM
                "helmfile" -> ComponentType.HELMFILE
                else -> null
            }
        }
        return null
    }

    private fun componentExists(
        componentsDir: VirtualFile,
        componentPath: String,
        componentType: ComponentType
    ): Boolean {
        val componentDir = componentsDir.findFileByRelativePath(componentPath) ?: return false

        return when (componentType) {
            ComponentType.TERRAFORM -> componentDir.findChild("main.tf") != null ||
                    componentDir.findChild("variables.tf") != null ||
                    componentDir.findChild("outputs.tf") != null
            ComponentType.HELMFILE -> componentDir.findChild("helmfile.yaml") != null
        }
    }

    enum class ComponentType {
        TERRAFORM, HELMFILE
    }
}

/**
 * Quick fix to create a missing component directory with scaffold files.
 */
class CreateComponentQuickFix(
    private val componentPath: String,
    private val componentType: AtmosUnknownComponentInspection.ComponentType,
    private val basePath: String
) : LocalQuickFix {

    override fun getName(): String = "Create component '$componentPath'"

    override fun getFamilyName(): String = "Create Atmos component"

    override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
        val projectService = AtmosProjectService.getInstance(project)
        val componentsDir = projectService.resolveProjectPath(basePath) ?: return

        // Create parent directories if needed
        val pathParts = componentPath.split("/")
        var currentDir = componentsDir

        for (part in pathParts) {
            currentDir = currentDir.findChild(part) ?: run {
                currentDir.createChildDirectory(this, part)
            }
        }

        // Create scaffold files based on component type
        when (componentType) {
            AtmosUnknownComponentInspection.ComponentType.TERRAFORM -> {
                createTerraformScaffold(currentDir)
            }
            AtmosUnknownComponentInspection.ComponentType.HELMFILE -> {
                createHelmfileScaffold(currentDir)
            }
        }

        // Open the main file in editor
        val mainFile = when (componentType) {
            AtmosUnknownComponentInspection.ComponentType.TERRAFORM -> currentDir.findChild("main.tf")
            AtmosUnknownComponentInspection.ComponentType.HELMFILE -> currentDir.findChild("helmfile.yaml")
        }

        mainFile?.let {
            PsiManager.getInstance(project).findFile(it)?.navigate(true)
        }
    }

    private fun createTerraformScaffold(componentDir: VirtualFile) {
        // main.tf
        val mainTf = componentDir.createChildData(this, "main.tf")
        mainTf.setBinaryContent("""
            |# Component: ${componentPath.split("/").last()}
            |# Created by Atmos plugin
            |
            |locals {
            |  enabled = module.this.enabled
            |}
            |
            |# Add your resources here
            |
        """.trimMargin().toByteArray())

        // variables.tf
        val variablesTf = componentDir.createChildData(this, "variables.tf")
        variablesTf.setBinaryContent("""
            |# Component variables
            |# Add your variable definitions here
            |
            |variable "region" {
            |  type        = string
            |  description = "AWS region"
            |}
            |
        """.trimMargin().toByteArray())

        // outputs.tf
        val outputsTf = componentDir.createChildData(this, "outputs.tf")
        outputsTf.setBinaryContent("""
            |# Component outputs
            |# Add your output definitions here
            |
        """.trimMargin().toByteArray())

        // versions.tf
        val versionsTf = componentDir.createChildData(this, "versions.tf")
        versionsTf.setBinaryContent("""
            |terraform {
            |  required_version = ">= 1.0.0"
            |
            |  required_providers {
            |    aws = {
            |      source  = "hashicorp/aws"
            |      version = ">= 4.0"
            |    }
            |  }
            |}
            |
        """.trimMargin().toByteArray())

        // context.tf - using Cloud Posse's null-label module
        val contextTf = componentDir.createChildData(this, "context.tf")
        contextTf.setBinaryContent("""
            |module "this" {
            |  source  = "cloudposse/label/null"
            |  version = "0.25.0"
            |
            |  enabled             = var.enabled
            |  namespace           = var.namespace
            |  tenant              = var.tenant
            |  environment         = var.environment
            |  stage               = var.stage
            |  name                = var.name
            |  delimiter           = var.delimiter
            |  attributes          = var.attributes
            |  tags                = var.tags
            |  additional_tag_map  = var.additional_tag_map
            |  regex_replace_chars = var.regex_replace_chars
            |  label_order         = var.label_order
            |  id_length_limit     = var.id_length_limit
            |  label_key_case      = var.label_key_case
            |  label_value_case    = var.label_value_case
            |  descriptor_formats  = var.descriptor_formats
            |  labels_as_tags      = var.labels_as_tags
            |}
            |
            |variable "enabled" {
            |  type        = bool
            |  default     = true
            |  description = "Set to false to prevent the module from creating any resources"
            |}
            |
            |# Additional context variables from null-label
            |variable "namespace" { type = string; default = null; description = "ID element" }
            |variable "tenant" { type = string; default = null; description = "ID element" }
            |variable "environment" { type = string; default = null; description = "ID element" }
            |variable "stage" { type = string; default = null; description = "ID element" }
            |variable "name" { type = string; default = null; description = "ID element" }
            |variable "delimiter" { type = string; default = null; description = "Delimiter between ID elements" }
            |variable "attributes" { type = list(string); default = []; description = "ID element" }
            |variable "tags" { type = map(string); default = {}; description = "Additional tags" }
            |variable "additional_tag_map" { type = map(string); default = {}; description = "Additional tag map" }
            |variable "regex_replace_chars" { type = string; default = null; description = "Regex chars to replace" }
            |variable "label_order" { type = list(string); default = null; description = "Order of ID elements" }
            |variable "id_length_limit" { type = number; default = null; description = "Max ID length" }
            |variable "label_key_case" { type = string; default = null; description = "Label key case" }
            |variable "label_value_case" { type = string; default = null; description = "Label value case" }
            |variable "descriptor_formats" { type = any; default = {}; description = "Descriptor formats" }
            |variable "labels_as_tags" { type = set(string); default = ["default"]; description = "Labels as tags" }
            |
        """.trimMargin().toByteArray())
    }

    private fun createHelmfileScaffold(componentDir: VirtualFile) {
        // helmfile.yaml
        val helmfile = componentDir.createChildData(this, "helmfile.yaml")
        helmfile.setBinaryContent("""
            |# Component: ${componentPath.split("/").last()}
            |# Created by Atmos plugin
            |
            |repositories:
            |  # Add your Helm repositories here
            |  # - name: example
            |  #   url: https://example.github.io/charts
            |
            |releases:
            |  # Add your Helm releases here
            |  # - name: example
            |  #   namespace: default
            |  #   chart: example/example
            |  #   version: "1.0.0"
            |  #   values:
            |  #     - values.yaml
            |
        """.trimMargin().toByteArray())
    }
}
