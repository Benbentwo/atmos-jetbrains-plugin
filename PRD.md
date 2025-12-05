# Product Requirements Document: Atmos Plugin for JetBrains IDEs

## Overview

This document outlines the requirements for an Atmos plugin for JetBrains-based IDEs (IntelliJ IDEA, WebStorm, GoLand, PyCharm, etc.). The plugin aims to provide first-class IDE support for [Atmos](https://atmos.tools/), a CLI and workflow automation tool for DevOps that orchestrates Terraform, OpenTofu, Packer, and Helmfile.

## Background

### What is Atmos?

Atmos is an Infrastructure as Code orchestration framework that:
- Treats environments as configuration to reduce code duplication
- Uses YAML-based stack definition system with deep-merge inheritance
- Separates **components** (Terraform root modules) from **stacks** (environment configuration)
- Supports complex multi-org, multi-tenant, multi-account, multi-region infrastructures
- Provides a powerful import system, templating via Go templates, and multiple inheritance patterns

### Inspiration: JetBrains Helm Plugin

The [Helm plugin for JetBrains](https://www.jetbrains.com/help/idea/helm.html) serves as the primary UX inspiration, offering:
- Rendered template values displayed with grey background
- Navigation between value usages and declarations
- Code completion for values and built-in objects
- Gutter icons for overridden/overriding values
- Template preview in diff viewer

---

## Goals

1. **Improve Developer Experience**: Reduce context-switching between IDE and CLI
2. **Enable Rapid Navigation**: Allow instant navigation to imports, components, and variable definitions
3. **Provide Visual Clarity**: Show resolved/computed values inline with visual distinction
4. **Prevent Errors**: Validate configurations before execution with inspections and quick-fixes
5. **Support Complex Inheritance**: Help developers understand configuration inheritance chains

---

## Target Users

- DevOps Engineers managing multi-environment infrastructure
- Platform Engineers building reusable component catalogs
- SRE teams using Atmos for Terraform orchestration
- Developers working with infrastructure-as-code in JetBrains IDEs

---

## Core Features

### 1. Syntax Highlighting & Language Support

#### 1.1 Stack File Support (`.yaml`, `.yml`)
- Recognize Atmos stack files based on:
  - Location within configured `stacks.base_path`
  - Presence of Atmos-specific keys: `import`, `components`, `vars`, `settings`, `metadata`
- Syntax highlighting for:
  - Import paths
  - Component references
  - Variable references
  - Go template expressions in `.yaml.tmpl` files

#### 1.2 YAML Function Highlighting
Highlight Atmos YAML functions with distinct styling:
- `!env` - Environment variable references
- `!exec` - Shell command execution
- `!include` - External YAML inclusion
- `!repo-root` - Git repository root reference
- `!terraform.output` - Terraform output references
- `!terraform.state` - Terraform state references
- `!atmos.Component` - Cross-component references

#### 1.3 Go Template Support (`.yaml.tmpl`, `.yml.tmpl`)
- Integration with Go Template plugin for template files
- Context-aware completion within template expressions
- Syntax highlighting for `{{ .variableName }}` patterns

---

### 2. Rendered Value Preview (Grey Background Display)

#### 2.1 Inline Value Resolution
Similar to the Helm plugin, display resolved values with a **grey background**:
- Show the resolved value inline next to the reference
- Toggle between showing reference and resolved value
- Visual distinction between:
  - Static values (normal display)
  - Inherited values (grey background, from parent/base)
  - Template-resolved values (grey background, from Go templates)
  - Function-resolved values (grey background, from YAML functions)

#### 2.2 Deep-Merge Preview
- Display the final merged configuration for a component
- Show value inheritance chain on hover
- Indicate which stack manifest contributed each value (using `atmos describe component --provenance`)

#### 2.3 Folding & Expansion
- Code folding for template expressions (Ctrl+NumPad +/-)
- Hover to expand and show the underlying template/directive
- Collapsible sections for large `vars` blocks

---

### 3. Navigation (Cmd/Ctrl+Click)

#### 3.1 Import Navigation
**Cmd+Click (Mac) / Ctrl+Click (Windows)** on an `import` entry navigates to that file:
```yaml
import:
  - catalog/vpc           # Click → opens stacks/catalog/vpc.yaml
  - mixins/region/us-east-2  # Click → opens stacks/mixins/region/us-east-2.yaml
  - ./_defaults           # Click → opens relative _defaults.yaml
```

Navigation supports:
- Base-relative paths (resolved from `stacks.base_path`)
- File-relative paths (`.` and `..` prefixes)
- Automatic extension detection (`.yaml`, `.yml`, `.yaml.tmpl`, `.yml.tmpl`)
- Remote imports (open URL in browser for git://, https://, s3:// schemes)

#### 3.2 Component Variable Navigation
**Cmd+Click / Ctrl+Click** on a variable under a component navigates to its definition:

```yaml
components:
  terraform:
    vpc:
      vars:
        vpc_cidr: "10.0.0.0/16"  # Click → opens components/terraform/vpc/variables.tf
```

Navigation targets:
- Terraform `variable` blocks in `variables.tf`
- Variable definitions in base/inherited components
- Default values in `_defaults.yaml` files

#### 3.3 Component Definition Navigation
Click on component names to navigate to the Terraform module:
```yaml
components:
  terraform:
    vpc:              # Click → opens components/terraform/vpc/main.tf
      metadata:
        component: networking/vpc  # Click → opens the actual component path
```

#### 3.4 Inheritance Navigation
Click on `metadata.inherits` entries to navigate to base components:
```yaml
metadata:
  inherits:
    - vpc/defaults    # Click → navigates to vpc/defaults component definition
```

#### 3.5 Cross-Reference Navigation
Navigate through YAML function references:
```yaml
vars:
  vpc_id: !terraform.output vpc.outputs.vpc_id  # Click → vpc component outputs
```

---

### 4. Code Completion & IntelliSense

#### 4.1 Import Path Completion
- Auto-complete import paths from `stacks.base_path`
- Show available stack files matching typed prefix
- Include both direct files and catalog subdirectories

#### 4.2 Component Name Completion
- Complete component names from `components/terraform/` and `components/helmfile/`
- Show component descriptions from README files if available
- Filter by component type (terraform, helmfile)

#### 4.3 Variable Completion
- Complete variable names from component's `variables.tf`
- Show variable types, descriptions, and default values
- Context-aware: only show vars valid for the current component

#### 4.4 Settings Completion
- Complete known settings keys (`spacelift`, `atlantis`, etc.)
- Show documentation for each setting

#### 4.5 Metadata Completion
- Complete `metadata` keys: `type`, `component`, `inherits`, `terraform_workspace`
- Suggest abstract component names for `inherits`

#### 4.6 YAML Function Completion
- Complete YAML function tags (`!env`, `!exec`, etc.)
- For `!terraform.output`: complete available outputs from referenced component
- For `!env`: suggest common environment variable names

---

### 5. Gutter Icons & Visual Indicators

#### 5.1 Inheritance Indicators
- **Upward arrow** (↑): Value overrides a parent value
- **Downward arrow** (↓): Value is overridden by a child
- Click gutter icon to navigate to parent/child definition

#### 5.2 Import Chain Visualization
- Gutter icon showing import depth (e.g., 📥 with badge count)
- Click to see full import chain in popup

#### 5.3 Component Status
- **Green checkmark**: Component validated successfully
- **Yellow warning**: Component has warnings
- **Red X**: Component has validation errors

#### 5.4 Abstract Component Indicator
- Special icon for abstract components (`metadata.type: abstract`)
- Visual distinction from deployable components

---

### 6. Inspections & Quick Fixes

#### 6.1 Validation Inspections
| Inspection | Description | Quick Fix |
|------------|-------------|-----------|
| Missing import | Import path doesn't resolve to a file | Create file / Fix path |
| Unknown component | Component not found in components directory | Create component scaffold |
| Unknown variable | Variable not defined in component | Add variable definition |
| Type mismatch | Variable value doesn't match expected type | Fix value type |
| Circular import | Import creates a cycle | Show cycle, suggest fix |
| Duplicate component | Same component defined multiple times | Merge or remove duplicate |
| Missing required var | Required variable has no value | Add variable with default |
| Invalid inheritance | Inherits from non-existent component | Fix reference |

#### 6.2 Best Practice Inspections
| Inspection | Description |
|------------|-------------|
| Non-abstract base | Base component not marked as abstract |
| Unused import | Imported file not referenced |
| Hardcoded values | Values that should be variables |
| Missing metadata | Component missing recommended metadata |

#### 6.3 Quick Fixes
- Auto-generate missing `_defaults.yaml` files
- Extract inline values to variables
- Convert component to abstract
- Add missing `metadata.component` pointer

---

### 7. CLI Integration

#### 7.1 Run Configurations
Create run configurations for:
- `atmos terraform plan <component> -s <stack>`
- `atmos terraform apply <component> -s <stack>`
- `atmos describe stacks`
- `atmos describe component <component> -s <stack>`
- `atmos validate component <component> -s <stack>`
- `atmos workflow <name>`

#### 7.2 Tool Window
Dedicated Atmos tool window showing:
- **Stacks Tree**: Hierarchical view of all stacks and their components
- **Components Tree**: All available components with their types
- **Workflows**: Available workflows from `workflows.base_path`
- **Recent Commands**: History of executed Atmos commands

#### 7.3 Context Actions
Right-click context menu actions:
- "Describe This Stack" - runs `atmos describe stacks --stack <current>`
- "Describe Component" - runs `atmos describe component`
- "Validate Component" - runs `atmos validate component`
- "Render Stack" - shows fully resolved stack in diff view
- "Show Inheritance Chain" - visualizes component inheritance

#### 7.4 Terminal Integration
- Auto-detect Atmos project and configure PATH
- Shell completion for Atmos commands in embedded terminal

---

### 8. Component Inspector

A dedicated tool window that provides real-time, fully-resolved component configuration based on cursor position. When the Inspector is open and the cursor is positioned on a component in a stack file, it automatically renders all resolved values for that component.

#### 8.1 Inspector Tool Window

**Location & Activation**
- Dockable tool window (default: right side panel)
- Toggle via: View → Tool Windows → Atmos Inspector
- Keyboard shortcut: `Cmd+Shift+I` (Mac) / `Ctrl+Shift+I` (Windows)
- Auto-hide option when not actively inspecting

**Window Layout**
```
┌─────────────────────────────────────────┐
│ 🔍 Atmos Inspector                    ─ □ ×│
├─────────────────────────────────────────┤
│ Component: vpc                          │
│ Stack: acme-prod-use2                   │
│ Status: ✓ Valid                         │
├─────────────────────────────────────────┤
│ ▼ vars (12)                             │
│   vpc_cidr: "10.0.0.0/16"        [local]│
│   region: "us-east-2"         [inherited]│
│   tenant: "acme"              [inherited]│
│   enable_nat: true            [inherited]│
│   ...                                   │
├─────────────────────────────────────────┤
│ ▼ settings (3)                          │
│   spacelift.workspace_enabled: true     │
│   ...                                   │
├─────────────────────────────────────────┤
│ ▼ env (2)                               │
│   AWS_REGION: "us-east-2"               │
│   ...                                   │
├─────────────────────────────────────────┤
│ ▼ metadata                              │
│   component: networking/vpc             │
│   inherits: [vpc/defaults]              │
│   type: real                            │
├─────────────────────────────────────────┤
│ ▼ backend                               │
│   type: s3                              │
│   bucket: "acme-terraform-state"        │
│   ...                                   │
└─────────────────────────────────────────┘
```

#### 8.2 Real-Time Cursor Tracking

**Automatic Detection**
- Inspector monitors cursor position in active editor
- When cursor enters a component block, Inspector updates automatically
- Debounced updates (250ms delay) to prevent excessive CLI calls
- Loading indicator while fetching component data

**Component Detection Logic**
- Detects cursor within `components.terraform.<name>` or `components.helmfile.<name>` blocks
- Determines the current stack from the file path and `stacks.name_pattern`
- Falls back to prompting for stack selection if ambiguous

**Cursor Context Display**
```yaml
components:
  terraform:
    vpc:           # ← Cursor here triggers Inspector for "vpc"
      vars:
        vpc_cidr: "10.0.0.0/16"  # ← Still in "vpc" context
    eks:           # ← Cursor here switches Inspector to "eks"
```

#### 8.3 Resolved Value Display

**Value Sources**
Each value displays its source with visual indicators:
- `[local]` - Defined in current file (normal text)
- `[inherited]` - From base component via `metadata.inherits` (italic, grey)
- `[imported]` - From an imported stack file (grey background)
- `[default]` - From Terraform variable default (dimmed)
- `[computed]` - From YAML function or template (blue, monospace)

**Value Sections**
| Section | Description |
|---------|-------------|
| `vars` | All resolved Terraform variables |
| `settings` | Integration settings (spacelift, atlantis, etc.) |
| `env` | Environment variables passed to Terraform |
| `metadata` | Component metadata (inherits, type, component path) |
| `backend` | Terraform backend configuration |
| `providers` | Provider overrides |
| `overrides` | Any override configurations |

#### 8.4 Inspector Actions

**Toolbar Actions**
| Icon | Action | Description |
|------|--------|-------------|
| 🔄 | Refresh | Force re-fetch component data |
| 📋 | Copy as YAML | Copy resolved config to clipboard |
| 📋 | Copy as JSON | Copy resolved config as JSON |
| 📂 | Open Component | Navigate to Terraform component directory |
| 🔗 | Show Provenance | Toggle provenance annotations |
| 📊 | Show Inheritance | Open inheritance chain visualization |
| ⚙️ | Settings | Inspector preferences |

**Context Menu (Right-Click on Value)**
- Copy Value
- Copy Key Path (e.g., `vars.vpc_cidr`)
- Go to Definition - Navigate to where this value is defined
- Go to Variable - Navigate to `variables.tf` definition
- Find Usages - Find other stacks using this value
- Compare with... - Compare value across stacks

#### 8.5 Inheritance Chain View

When "Show Inheritance" is enabled, display the full resolution chain:
```
vpc_cidr: "10.0.0.0/16"
├── acme-prod-use2.yaml (current) ──────── "10.0.0.0/16" ✓
├── catalog/vpc/defaults.yaml ─────────── "10.0.0.0/8"
└── mixins/networking/_defaults.yaml ──── (not defined)
```

#### 8.6 Performance & Caching

**Optimization Strategies**
- Cache `atmos describe component` results per file
- Invalidate cache on file save or external change
- Background fetch with stale-while-revalidate pattern
- Option to disable auto-refresh for large projects

**Settings**
| Setting | Description | Default |
|---------|-------------|---------|
| Auto-refresh | Update Inspector on cursor move | Enabled |
| Refresh delay | Debounce delay in milliseconds | 250ms |
| Cache TTL | How long to cache component data | 30 seconds |
| Show on startup | Open Inspector when project opens | Disabled |

#### 8.7 Error Handling

**When Component Cannot Be Resolved**
- Display error message in Inspector panel
- Show partial data if available
- Link to relevant validation errors
- Suggest quick fixes when possible

```
┌─────────────────────────────────────────┐
│ ⚠️ Component Resolution Error           │
├─────────────────────────────────────────┤
│ Could not resolve component "vpc" in    │
│ stack "acme-prod-use2"                  │
│                                         │
│ Error: Missing required variable        │
│ "availability_zones"                    │
│                                         │
│ [Show Full Error] [Go to Definition]    │
└─────────────────────────────────────────┘
```

#### 8.8 Keyboard Navigation

| Shortcut | Action |
|----------|--------|
| `↑` / `↓` | Navigate between values |
| `Enter` | Expand/collapse section |
| `Cmd+C` / `Ctrl+C` | Copy selected value |
| `F4` / `Cmd+↓` | Go to definition |
| `Escape` | Return focus to editor |

---

### 9. Diff & Preview

#### 9.1 Stack Diff View
Compare resolved configurations:
- Original template vs. rendered output
- Two stacks side-by-side (e.g., dev vs. prod)
- Current vs. proposed changes

#### 9.2 Merge Preview
Preview the result of deep-merge operations:
- Show how imports combine
- Visualize inheritance resolution
- Highlight overridden values

#### 9.3 Change Impact Analysis
Before applying changes:
- Show which components would be affected
- Display the diff in Terraform plan format
- Integrate with `atmos describe affected` for PR analysis

---

### 10. Configuration & Settings

#### 10.1 Plugin Settings
| Setting | Description | Default |
|---------|-------------|---------|
| Atmos Executable Path | Path to atmos binary | Auto-detect |
| Configuration File | Path to atmos.yaml | Auto-detect from project root |
| Render on Save | Auto-render templates on file save | Disabled |
| Show Resolved Values | Display resolved values inline | Enabled |
| Validate on Save | Run validation on file save | Enabled |
| Telemetry | Enable/disable usage analytics | Disabled |

#### 10.2 Project-Level Settings
- Override global settings per project
- Custom component paths
- Custom stack patterns
- Integration with `.atmos.d/` auto-imports

---

### 11. Documentation & Help

#### 11.1 Quick Documentation (F1 / Ctrl+Q)
- Show documentation for components
- Display variable descriptions and types
- Show import file path and contents preview

#### 11.2 External Documentation
- Link to Atmos documentation
- Context-sensitive help links

---

## Technical Requirements

### Platform Support
- IntelliJ Platform version 2023.1+
- Compatible with all JetBrains IDEs based on IntelliJ Platform
- Works with both Community and Ultimate editions where applicable

### Dependencies
- YAML plugin (bundled in most JetBrains IDEs)
- Go Template plugin (for `.tmpl` file support)
- Terminal plugin (for CLI integration)

### Performance
- Lazy loading of component metadata
- Background indexing of stack files
- Cached resolution of imports and inheritance
- Incremental validation on file changes

### Integration Points
- `atmos describe config` - Get project configuration
- `atmos describe stacks` - Get all stack configurations
- `atmos describe component` - Get component details with provenance
- `atmos validate component` - Validate configurations
- `atmos list stacks/components` - Enumerate available resources

---

## Future Enhancements (Phase 2)

1. **Visual Inheritance Editor**: Drag-and-drop interface for building inheritance hierarchies
2. **Component Marketplace**: Browse and vendor components from registries
3. **GitOps Integration**: Direct integration with GitHub Actions for Atmos
4. **Atlantis Integration**: Preview Atlantis plans from IDE
5. **Drift Detection**: Show infrastructure drift indicators
6. **AI-Assisted Suggestions**: Suggest component configurations based on patterns
7. **Collaborative Features**: Share stack configurations with team members
8. **Schema Registry**: Custom JSON Schema validation support

---

## Success Metrics

| Metric | Target |
|--------|--------|
| Navigation accuracy | 95%+ of Cmd+Click navigations resolve correctly |
| Completion relevance | 90%+ of top-3 completions are useful |
| Validation accuracy | 99%+ of validation errors are true positives |
| Render performance | <500ms for stack rendering |
| User satisfaction | 4.5+ star rating in JetBrains Marketplace |

---

## Appendix A: Atmos Configuration Reference

### atmos.yaml Structure
```yaml
base_path: ""
components:
  terraform:
    base_path: components/terraform
  helmfile:
    base_path: components/helmfile
stacks:
  base_path: stacks
  included_paths:
    - "**/*"
  excluded_paths:
    - "**/_defaults.yaml"
  name_pattern: "{tenant}-{environment}-{stage}"
workflows:
  base_path: stacks/workflows
```

### Stack File Structure
```yaml
import:
  - catalog/vpc
  - mixins/region/us-east-2

vars:
  tenant: acme
  environment: prod
  stage: use2

components:
  terraform:
    vpc:
      metadata:
        component: networking/vpc
        inherits:
          - vpc/defaults
      vars:
        vpc_cidr: "10.0.0.0/16"
      settings:
        spacelift:
          workspace_enabled: true
      env:
        AWS_REGION: us-east-2
```

---

## Appendix B: Research Sources

- [Atmos Documentation](https://atmos.tools/)
- [Atmos Quick Start](https://atmos.tools/quick-start)
- [Atmos Stack Imports](https://atmos.tools/stacks/imports)
- [Atmos Component Inheritance](https://atmos.tools/howto/inheritance)
- [Atmos CLI Commands](https://atmos.tools/cli/commands/)
- [Atmos GitHub Actions](https://atmos.tools/integrations/github-actions)
- [JetBrains Helm Plugin](https://www.jetbrains.com/help/idea/helm.html)
- [IntelliJ IDEA Helm Support Blog](https://blog.jetbrains.com/idea/2018/10/intellij-idea-2018-3-helm-support/)
