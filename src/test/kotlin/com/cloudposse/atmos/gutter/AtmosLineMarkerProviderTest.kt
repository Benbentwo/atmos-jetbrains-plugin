package com.cloudposse.atmos.gutter

import com.cloudposse.atmos.AtmosTestBase
import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import org.junit.Assert.assertTrue

/**
 * Tests for Atmos gutter icons (line markers).
 */
class AtmosLineMarkerProviderTest : AtmosTestBase() {

    fun testImportBlockHasGutterIcon() {
        setupFullTestProject()

        val file = myFixture.configureByText(
            "stacks/test-stack.yaml",
            """
            import:
              - catalog/vpc
              - catalog/eks

            components:
              terraform:
                vpc:
                  vars:
                    vpc_cidr: "10.0.0.0/16"
            """.trimIndent()
        )

        myFixture.doHighlighting()

        // Get all line markers
        val document = myFixture.editor.document
        val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project)

        // Check for import marker
        val importMarkers = lineMarkers.filter {
            it.element?.text == "import"
        }

        assertTrue("Should have gutter icon for import block", importMarkers.isNotEmpty())
    }

    fun testAbstractComponentHasGutterIcon() {
        setupFullTestProject()

        myFixture.configureByText(
            "stacks/test-stack.yaml",
            """
            components:
              terraform:
                vpc-base:
                  metadata:
                    type: abstract
                  vars:
                    vpc_cidr: "10.0.0.0/16"
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val document = myFixture.editor.document
        val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project)

        // Check for component type marker
        assertTrue("Should have line markers", lineMarkers.isNotEmpty())
    }

    fun testInheritingComponentHasGutterIcon() {
        setupFullTestProject()

        myFixture.configureByText(
            "stacks/test-stack.yaml",
            """
            components:
              terraform:
                vpc:
                  metadata:
                    inherits:
                      - vpc-base
                  vars:
                    vpc_cidr: "10.0.0.0/16"
            """.trimIndent()
        )

        myFixture.doHighlighting()

        val document = myFixture.editor.document
        val lineMarkers = DaemonCodeAnalyzerImpl.getLineMarkers(document, project)

        // Check for inheritance marker
        val inheritsMarkers = lineMarkers.filter {
            it.element?.text == "inherits"
        }

        assertTrue("Should have gutter icon for inherits", inheritsMarkers.isNotEmpty())
    }
}
