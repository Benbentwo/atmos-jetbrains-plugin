package com.cloudposse.atmos.completion

import com.cloudposse.atmos.AtmosTestBase

/**
 * Tests for Atmos code completion.
 */
class AtmosCompletionTest : AtmosTestBase() {

    fun testMetadataKeyCompletion() {
        setupFullTestProject()

        myFixture.configureByText(
            "stacks/test-stack.yaml",
            """
            components:
              terraform:
                vpc:
                  metadata:
                    <caret>
            """.trimIndent()
        )

        val completions = myFixture.completeBasic()
        val completionStrings = completions?.map { it.lookupString } ?: emptyList()

        assertTrue("Should suggest 'type'", completionStrings.contains("type"))
        assertTrue("Should suggest 'component'", completionStrings.contains("component"))
        assertTrue("Should suggest 'inherits'", completionStrings.contains("inherits"))
    }

    fun testMetadataTypeCompletion() {
        setupFullTestProject()

        myFixture.configureByText(
            "stacks/test-stack.yaml",
            """
            components:
              terraform:
                vpc:
                  metadata:
                    type: <caret>
            """.trimIndent()
        )

        val completions = myFixture.completeBasic()
        val completionStrings = completions?.map { it.lookupString } ?: emptyList()

        assertTrue("Should suggest 'abstract'", completionStrings.contains("abstract"))
        assertTrue("Should suggest 'real'", completionStrings.contains("real"))
    }

    fun testSettingsKeyCompletion() {
        setupFullTestProject()

        myFixture.configureByText(
            "stacks/test-stack.yaml",
            """
            components:
              terraform:
                vpc:
                  settings:
                    <caret>
            """.trimIndent()
        )

        val completions = myFixture.completeBasic()
        val completionStrings = completions?.map { it.lookupString } ?: emptyList()

        assertTrue("Should suggest 'spacelift'", completionStrings.contains("spacelift"))
        assertTrue("Should suggest 'atlantis'", completionStrings.contains("atlantis"))
    }
}
