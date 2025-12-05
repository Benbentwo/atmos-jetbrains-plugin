package com.cloudposse.atmos.run

/**
 * Types of Atmos commands that can be run.
 */
enum class AtmosCommandType(val displayName: String, val command: String) {
    TERRAFORM_PLAN("Terraform Plan", "terraform plan"),
    TERRAFORM_APPLY("Terraform Apply", "terraform apply"),
    TERRAFORM_DESTROY("Terraform Destroy", "terraform destroy"),
    TERRAFORM_INIT("Terraform Init", "terraform init"),
    TERRAFORM_VALIDATE("Terraform Validate", "terraform validate"),
    DESCRIBE_STACKS("Describe Stacks", "describe stacks"),
    DESCRIBE_COMPONENT("Describe Component", "describe component"),
    VALIDATE_COMPONENT("Validate Component", "validate component"),
    VALIDATE_STACKS("Validate Stacks", "validate stacks"),
    WORKFLOW("Workflow", "workflow"),
    CUSTOM("Custom", "");

    override fun toString(): String = displayName

    companion object {
        fun fromDisplayName(name: String): AtmosCommandType {
            return values().find { it.displayName == name } ?: CUSTOM
        }
    }
}
