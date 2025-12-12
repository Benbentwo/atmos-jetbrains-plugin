package com.cloudposse.atmos.services

import com.cloudposse.atmos.AtmosTestBase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse

/**
 * Tests for AtmosCommandRunner service.
 */
class AtmosCommandRunnerTest : AtmosTestBase() {

    fun `test service is registered`() {
        setupAtmosProject()
        val service = AtmosCommandRunner.getInstance(project)
        assertNotNull("AtmosCommandRunner service should be available", service)
    }

    fun `test command result properties`() {
        // Test successful result
        val successResult = AtmosCommandRunner.CommandResult(
            exitCode = 0,
            stdout = "Success output",
            stderr = ""
        )
        assertTrue(successResult.isSuccess)
        assertEquals("Success output", successResult.output)

        // Test failed result
        val failedResult = AtmosCommandRunner.CommandResult(
            exitCode = 1,
            stdout = "",
            stderr = "Error message"
        )
        assertFalse(failedResult.isSuccess)
        assertEquals("Error message", failedResult.output)
    }

    fun `test command result with both outputs`() {
        val result = AtmosCommandRunner.CommandResult(
            exitCode = 0,
            stdout = "Standard output",
            stderr = "Standard error"
        )
        assertTrue(result.isSuccess)
        // When stdout has content, output should be stdout
        assertEquals("Standard output", result.output)
    }

    fun `test command result with empty stdout`() {
        val result = AtmosCommandRunner.CommandResult(
            exitCode = 0,
            stdout = "",
            stderr = "Some warning"
        )
        assertTrue(result.isSuccess)
        // When stdout is empty, output falls back to stderr
        assertEquals("Some warning", result.output)
    }
}
