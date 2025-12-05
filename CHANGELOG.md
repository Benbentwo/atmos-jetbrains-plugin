# Changelog

## [Unreleased]

## [0.2.0] - Phase 2: Enhanced IDE Support

### Added

#### Code Completion
- Auto-complete import paths from stacks base directory
- Component name completion for Terraform and Helmfile components
- Metadata key completion (type, component, inherits, terraform_workspace)
- Settings key completion (spacelift, atlantis, validation)
- YAML function tag completion (!env, !exec, etc.)

#### Navigation
- Cmd+Click on component names to navigate to Terraform/Helmfile directories
- Cmd+Click on variable keys to navigate to variables.tf definitions
- Cmd+Click on metadata.component to navigate to the actual component path
- Support for metadata.inherits navigation

#### Gutter Icons
- Import indicators showing number of imported files
- Inheritance indicators for components with metadata.inherits
- Abstract vs. real component type indicators
- Variable override indicators for inherited values

#### Inspections & Quick Fixes
- Missing import inspection with quick fix to create stack file
- Unknown component inspection with quick fix to create component scaffold
- Circular import detection
- Terraform component scaffold generation (main.tf, variables.tf, outputs.tf, versions.tf, context.tf)
- Helmfile component scaffold generation

#### CLI Integration
- Run configurations for Atmos commands (terraform plan/apply, describe stacks/component, validate, workflow)
- Atmos tool window with tree view of stacks, components, and workflows
- Context menu actions for common Atmos commands
- Support for custom additional arguments

#### Component Inspector
- Real-time component inspection tool window
- Cursor tracking to show resolved values for current component
- Tree view of vars, settings, metadata, and backend configuration
- Integration with `atmos describe component` CLI command
- Copy as YAML/JSON functionality

#### Quick Documentation (F1/Ctrl+Q)
- Documentation for import paths (file location and contents preview)
- Documentation for component names (variables and files)
- Documentation for YAML functions (!env, !exec, !terraform.output, etc.)
- Documentation for metadata keys
- Documentation for settings keys
- Links to Atmos documentation

### Changed
- Updated plugin.xml with all new extension points
- Enhanced AtmosIcons with additional icons using IntelliJ AllIcons

## [0.1.0] - Phase 1: MVP

### Added
- Initial plugin structure
- Syntax highlighting for Atmos YAML functions (!env, !exec, !include, !repo-root, !terraform.output, !terraform.state, !atmos.Component)
- Cmd+Click navigation for import paths
- Basic plugin settings (Atmos executable path)
- Project detection via atmos.yaml
- Configuration service for parsing atmos.yaml
- Support for both .yaml and .yml file extensions
- Support for .yaml.tmpl and .yml.tmpl template files
