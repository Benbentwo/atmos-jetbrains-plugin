package com.cloudposse.atmos.language.yaml

import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLScalar
import org.jetbrains.yaml.psi.YAMLValue

/**
 * Annotator that highlights Atmos YAML functions (!env, !exec, !terraform.output, etc.)
 * with distinct visual styling.
 */
class AtmosYamlFunctionAnnotator : Annotator {

    companion object {
        // Atmos YAML function tags
        private val YAML_FUNCTIONS = setOf(
            "!env",
            "!exec",
            "!include",
            "!repo-root",
            "!terraform.output",
            "!terraform.state",
            "!atmos.Component"
        )

        // Text attribute keys for different function types
        val ATMOS_FUNCTION_TAG: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "ATMOS_FUNCTION_TAG",
            DefaultLanguageHighlighterColors.METADATA
        )

        val ATMOS_FUNCTION_ENV: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "ATMOS_FUNCTION_ENV",
            DefaultLanguageHighlighterColors.CONSTANT
        )

        val ATMOS_FUNCTION_EXEC: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "ATMOS_FUNCTION_EXEC",
            DefaultLanguageHighlighterColors.FUNCTION_CALL
        )

        val ATMOS_FUNCTION_TERRAFORM: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "ATMOS_FUNCTION_TERRAFORM",
            DefaultLanguageHighlighterColors.KEYWORD
        )

        val ATMOS_FUNCTION_INCLUDE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "ATMOS_FUNCTION_INCLUDE",
            DefaultLanguageHighlighterColors.STRING
        )

        val ATMOS_FUNCTION_ATMOS: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
            "ATMOS_FUNCTION_ATMOS",
            DefaultLanguageHighlighterColors.STATIC_METHOD
        )
    }

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Only process in Atmos projects
        val project = element.project
        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isAtmosProject) return

        // Only process YAML scalar values (where function tags appear)
        if (element !is YAMLScalar) return

        val text = element.text
        val functionTag = findYamlFunctionTag(text) ?: return

        // Highlight the function tag
        val tagRange = findTagRange(element, functionTag)
        if (tagRange != null) {
            val attributes = getAttributesForFunction(functionTag)
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(tagRange)
                .textAttributes(attributes)
                .create()
        }
    }

    private fun findYamlFunctionTag(text: String): String? {
        // YAML function tags start with ! at the beginning of the value
        for (function in YAML_FUNCTIONS) {
            if (text.startsWith(function) || text.contains(" $function")) {
                return function
            }
        }
        return null
    }

    private fun findTagRange(element: PsiElement, tag: String): TextRange? {
        val text = element.text
        val startOffset = text.indexOf(tag)
        if (startOffset < 0) return null

        val elementStart = element.textRange.startOffset
        return TextRange(elementStart + startOffset, elementStart + startOffset + tag.length)
    }

    private fun getAttributesForFunction(functionTag: String): TextAttributesKey {
        return when {
            functionTag == "!env" -> ATMOS_FUNCTION_ENV
            functionTag == "!exec" -> ATMOS_FUNCTION_EXEC
            functionTag.startsWith("!terraform") -> ATMOS_FUNCTION_TERRAFORM
            functionTag == "!include" -> ATMOS_FUNCTION_INCLUDE
            functionTag == "!repo-root" -> ATMOS_FUNCTION_INCLUDE
            functionTag.startsWith("!atmos") -> ATMOS_FUNCTION_ATMOS
            else -> ATMOS_FUNCTION_TAG
        }
    }
}
