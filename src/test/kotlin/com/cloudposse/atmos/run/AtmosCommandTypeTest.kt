package com.cloudposse.atmos.run

import com.cloudposse.atmos.AtmosTestBase

/**
 * Tests for AtmosCommandType enum.
 */
class AtmosCommandTypeTest : AtmosTestBase() {

    fun `test all command types have display names`() {
        for (type in AtmosCommandType.values()) {
            assertNotNull("Command type ${type.name} should have a display name", type.displayName)
            assertTrue("Display name should not be empty for ${type.name}", type.displayName.isNotBlank())
        }
    }

    fun `test from display name`() {
        assertEquals(AtmosCommandType.TERRAFORM_PLAN, AtmosCommandType.fromDisplayName("Terraform Plan"))
        assertEquals(AtmosCommandType.TERRAFORM_APPLY, AtmosCommandType.fromDisplayName("Terraform Apply"))
        assertEquals(AtmosCommandType.TERRAFORM_DESTROY, AtmosCommandType.fromDisplayName("Terraform Destroy"))
        assertEquals(AtmosCommandType.DESCRIBE_STACKS, AtmosCommandType.fromDisplayName("Describe Stacks"))
        assertEquals(AtmosCommandType.DESCRIBE_COMPONENT, AtmosCommandType.fromDisplayName("Describe Component"))
        assertEquals(AtmosCommandType.VALIDATE_COMPONENT, AtmosCommandType.fromDisplayName("Validate Component"))
        assertEquals(AtmosCommandType.WORKFLOW, AtmosCommandType.fromDisplayName("Workflow"))
        assertEquals(AtmosCommandType.CUSTOM, AtmosCommandType.fromDisplayName("Custom"))
    }

    fun `test from display name unknown`() {
        // Unknown display name should return CUSTOM
        assertEquals(AtmosCommandType.CUSTOM, AtmosCommandType.fromDisplayName("Unknown Command"))
        assertEquals(AtmosCommandType.CUSTOM, AtmosCommandType.fromDisplayName(""))
    }

    fun `test command strings`() {
        assertEquals("terraform plan", AtmosCommandType.TERRAFORM_PLAN.command)
        assertEquals("terraform apply", AtmosCommandType.TERRAFORM_APPLY.command)
        assertEquals("terraform destroy", AtmosCommandType.TERRAFORM_DESTROY.command)
        assertEquals("describe stacks", AtmosCommandType.DESCRIBE_STACKS.command)
        assertEquals("describe component", AtmosCommandType.DESCRIBE_COMPONENT.command)
        assertEquals("validate component", AtmosCommandType.VALIDATE_COMPONENT.command)
        assertEquals("workflow", AtmosCommandType.WORKFLOW.command)
        assertEquals("", AtmosCommandType.CUSTOM.command)
    }
}
