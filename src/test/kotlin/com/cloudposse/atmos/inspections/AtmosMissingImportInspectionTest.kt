package com.cloudposse.atmos.inspections

import com.cloudposse.atmos.AtmosTestBase
import org.junit.Assert.assertTrue

/**
 * Tests for the AtmosMissingImportInspection.
 */
class AtmosMissingImportInspectionTest : AtmosTestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(AtmosMissingImportInspection::class.java)
    }

    fun testValidImportNoWarning() {
        setupFullTestProject()

        myFixture.configureByText(
            "test-stack.yaml",
            """
            import:
              - catalog/vpc
            """.trimIndent()
        )

        // Should not have any warnings for existing import
        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.description?.contains("Cannot resolve import") == true }
        assertTrue("Should have no errors for valid imports", errors.isEmpty())
    }

    fun testMissingImportShowsError() {
        setupAtmosProject()

        myFixture.configureByText(
            "stacks/test-stack.yaml",
            """
            import:
              - nonexistent/file
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.description?.contains("Cannot resolve import") == true }
        assertTrue("Should have error for missing import", errors.isNotEmpty())
    }

    fun testRemoteImportNoWarning() {
        setupAtmosProject()

        myFixture.configureByText(
            "stacks/test-stack.yaml",
            """
            import:
              - https://example.com/stack.yaml
              - git://github.com/example/repo.git//stacks/base.yaml
              - s3://bucket/path/to/stack.yaml
            """.trimIndent()
        )

        val highlights = myFixture.doHighlighting()
        val errors = highlights.filter { it.description?.contains("Cannot resolve import") == true }
        assertTrue("Should not report errors for remote imports", errors.isEmpty())
    }
}
