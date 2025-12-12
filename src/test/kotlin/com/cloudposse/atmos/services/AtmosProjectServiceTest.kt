package com.cloudposse.atmos.services

import com.cloudposse.atmos.AtmosTestBase
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals

/**
 * Tests for AtmosProjectService.
 */
class AtmosProjectServiceTest : AtmosTestBase() {

    fun `test project detection with atmos yaml`() {
        setupAtmosProject()

        val projectService = AtmosProjectService.getInstance(project)
        assertTrue("Should detect Atmos project", projectService.isAtmosProject)
    }

    fun `test finds atmos config file`() {
        setupAtmosProject()

        val projectService = AtmosProjectService.getInstance(project)
        val configFile = projectService.findAtmosConfigFile()

        assertNotNull("Should find atmos.yaml", configFile)
        assertEquals("atmos.yaml", configFile?.name)
    }

    fun `test stack file detection`() {
        setupFullTestProject()

        val projectService = AtmosProjectService.getInstance(project)
        val stackFile = projectService.resolveProjectPath("stacks/catalog/vpc.yaml")

        assertNotNull("Should find stack file", stackFile)
        assertTrue("Should recognize as stack file", projectService.isStackFile(stackFile!!))
    }
}
