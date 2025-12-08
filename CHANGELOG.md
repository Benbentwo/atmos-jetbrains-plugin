# Changelog

## [Unreleased]

### Added
- Initial plugin structure
- Syntax highlighting for Atmos YAML functions (!env, !exec, !include, !repo-root, !terraform.output, !terraform.state, !atmos.Component)
- Cmd+Click navigation for import paths
- Basic plugin settings (Atmos executable path)
- Project detection via atmos.yaml

### Phase 4: CLI Integration
- **Run Configurations**: Create and run Atmos CLI commands directly from the IDE
  - Terraform plan/apply/destroy/init/validate
  - Describe stacks/components
  - Validate stacks/components
  - Execute workflows
  - Custom commands support
- **Atmos Tool Window**: Dedicated tool window with tabs for:
  - Stacks tree view (list all stacks)
  - Components tree view (list all components)
  - Workflows tree view (list workflow files)
  - Console output for command results
- **Context Actions**: Right-click menu actions in stack files
  - Describe Component - runs `atmos describe component`
  - Validate Component - runs `atmos validate component`
  - Terraform Plan - runs `atmos terraform plan`
  - Terraform Apply - runs `atmos terraform apply` with confirmation
  - Describe Stack - runs `atmos describe stacks`
- **Run Configuration Producer**: Automatically detect component context from cursor position
- **CLI Command Runner**: Background execution of Atmos commands with output streaming
