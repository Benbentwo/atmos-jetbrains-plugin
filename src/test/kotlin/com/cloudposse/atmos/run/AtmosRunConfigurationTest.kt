package com.cloudposse.atmos.run

import com.cloudposse.atmos.AtmosTestBase
import com.intellij.execution.RunManager
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue

/**
 * Tests for Atmos run configurations.
 */
class AtmosRunConfigurationTest : AtmosTestBase() {

    fun `test run configuration type is registered`() {
        val configType = AtmosRunConfigurationType.getInstance()
        assertNotNull("Atmos run configuration type should be registered", configType)
        assertEquals("AtmosRunConfiguration", configType.id)
        assertEquals("Atmos", configType.displayName)
    }

    fun `test create terraform plan configuration`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Test Plan", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.TERRAFORM_PLAN
        config.component = "vpc"
        config.stack = "dev-us-east-1"

        val args = config.buildCommandLine()
        assertEquals(listOf("terraform", "plan", "vpc", "-s", "dev-us-east-1"), args)
    }

    fun `test create terraform apply configuration`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Test Apply", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.TERRAFORM_APPLY
        config.component = "vpc"
        config.stack = "dev-us-east-1"

        val args = config.buildCommandLine()
        assertEquals(listOf("terraform", "apply", "vpc", "-s", "dev-us-east-1"), args)
    }

    fun `test create describe stacks configuration`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Describe Stacks", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.DESCRIBE_STACKS

        val args = config.buildCommandLine()
        assertEquals(listOf("describe", "stacks"), args)
    }

    fun `test create describe component configuration`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Describe Component", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.DESCRIBE_COMPONENT
        config.component = "eks"
        config.stack = "prod-us-west-2"

        val args = config.buildCommandLine()
        assertEquals(listOf("describe", "component", "eks", "-s", "prod-us-west-2"), args)
    }

    fun `test create workflow configuration`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Run Workflow", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.WORKFLOW
        config.workflowName = "deploy-all"

        val args = config.buildCommandLine()
        assertEquals(listOf("workflow", "deploy-all"), args)
    }

    fun `test create custom command configuration`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Custom Command", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.CUSTOM
        config.customCommand = "version"

        val args = config.buildCommandLine()
        assertEquals(listOf("version"), args)
    }

    fun `test additional arguments`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Test with Args", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.TERRAFORM_PLAN
        config.component = "vpc"
        config.stack = "dev"
        config.additionalArguments = "--no-color"

        val args = config.buildCommandLine()
        assertEquals(listOf("terraform", "plan", "vpc", "-s", "dev", "--no-color"), args)
    }

    fun `test configuration validation missing component`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Invalid", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.TERRAFORM_PLAN
        config.stack = "dev"
        // component is empty

        try {
            config.checkConfiguration()
            fail("Expected RuntimeConfigurationError for missing component")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Component") == true)
        }
    }

    fun `test configuration validation missing stack`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Invalid", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.TERRAFORM_PLAN
        config.component = "vpc"
        // stack is empty

        try {
            config.checkConfiguration()
            fail("Expected RuntimeConfigurationError for missing stack")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Stack") == true)
        }
    }

    fun `test configuration validation missing workflow name`() {
        setupAtmosProject()

        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration("Invalid", configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration

        config.commandType = AtmosCommandType.WORKFLOW
        // workflowName is empty

        try {
            config.checkConfiguration()
            fail("Expected RuntimeConfigurationError for missing workflow name")
        } catch (e: Exception) {
            assertTrue(e.message?.contains("Workflow") == true)
        }
    }
}
