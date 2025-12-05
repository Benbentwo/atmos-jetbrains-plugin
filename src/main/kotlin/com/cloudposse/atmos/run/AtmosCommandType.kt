package com.cloudposse.atmos.run

/**
 * Types of Atmos commands that can be run.
 */
enum class AtmosCommandType(val displayName: String, val command: String) {
    TERRAFORM_PLAN("Terraform Plan", "terraform plan"),
    TERRAFORM_APPLY("Terraform Apply", "terraform apply"),
    TERRAFORM_DESTROY("Terraform Destroy", "terraform destroy"),
    DESCRIBE_STACKS("Describe Stacks", "describe stacks"),
    DESCRIBE_COMPONENT("Describe Component", "describe component"),
    VALIDATE_COMPONENT("Validate Component", "validate component"),
    WORKFLOW("Workflow", "workflow"),
    CUSTOM("Custom", "");

    companion object {
        fun fromDisplayName(name: String): AtmosCommandType {
            return values().find { it.displayName == name } ?: CUSTOM
        }
    }
}
