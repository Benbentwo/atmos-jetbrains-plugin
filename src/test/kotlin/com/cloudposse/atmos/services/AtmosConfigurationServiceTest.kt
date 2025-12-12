package com.cloudposse.atmos.services

import com.cloudposse.atmos.AtmosTestBase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals

/**
 * Tests for AtmosConfigurationService.
 */
class AtmosConfigurationServiceTest : AtmosTestBase() {

    fun `test config service parses atmos yaml`() {
        setupAtmosProject()

        val configService = AtmosConfigurationService.getInstance(project)
        val config = configService.getConfig()

        assertNotNull("Config should not be null", config)
        assertEquals("stacks", config?.stacks?.basePath)
        assertEquals("components/terraform", config?.components?.terraform?.basePath)
        assertEquals("components/helmfile", config?.components?.helmfile?.basePath)
    }

    fun `test stacks base path`() {
        setupAtmosProject()

        val configService = AtmosConfigurationService.getInstance(project)
        assertEquals("stacks", configService.getStacksBasePath())
    }

    fun `test terraform components base path`() {
        setupAtmosProject()

        val configService = AtmosConfigurationService.getInstance(project)
        assertEquals("components/terraform", configService.getTerraformComponentsBasePath())
    }

    fun `test stacks name pattern`() {
        setupAtmosProject()

        val configService = AtmosConfigurationService.getInstance(project)
        assertEquals("{tenant}-{environment}-{stage}", configService.getStacksNamePattern())
    }
}
