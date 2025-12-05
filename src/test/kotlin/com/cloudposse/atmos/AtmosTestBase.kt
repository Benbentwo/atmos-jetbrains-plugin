package com.cloudposse.atmos

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Base class for Atmos plugin tests.
 * Provides common setup and utilities for testing.
 */
abstract class AtmosTestBase : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    /**
     * Sets up a minimal Atmos project structure for testing.
     */
    protected fun setupAtmosProject() {
        // Copy the atmos.yaml configuration file
        myFixture.copyFileToProject("atmos.yaml", "atmos.yaml")
    }

    /**
     * Sets up the full test project with stacks and components.
     */
    protected fun setupFullTestProject() {
        setupAtmosProject()

        // Copy stacks
        myFixture.copyDirectoryToProject("stacks", "stacks")

        // Copy components
        myFixture.copyDirectoryToProject("components", "components")
    }
}
