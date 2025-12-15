# Atmos Plugin for JetBrains IDEs

[![Build](https://github.com/Benbentwo/atmos-jetbrains-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/Benbentwo/atmos-jetbrains-plugin/actions/workflows/build.yml)

First-class IDE support for [Atmos](https://atmos.tools/) - the ultimate DevOps framework for Terraform, OpenTofu, Packer, and Helmfile orchestration.

<!-- Plugin description -->
## Features

### Syntax Highlighting
- YAML function highlighting for `!env`, `!exec`, `!include`, `!repo-root`, `!terraform.output`, `!terraform.state`, `!atmos.Component`
- Distinct visual styling for each function type

### Navigation (Cmd/Ctrl+Click)
- **Import paths** - Navigate to referenced stack files
- **Component names** - Navigate to Terraform/Helmfile component directories
- **Variable keys** - Navigate to `variables.tf` definitions
- **metadata.component** - Navigate to actual component paths
- **metadata.inherits** - Navigate to inherited components

### Code Completion
- Import paths from stacks base directory
- Component names for Terraform and Helmfile
- Metadata keys (`type`, `component`, `inherits`, `terraform_workspace`)
- Settings keys (`spacelift`, `atlantis`, `validation`)
- YAML function tags

### Inspections & Quick Fixes
- **Missing imports** - With quick fix to create stack file
- **Unknown components** - With quick fix to create component scaffold
- **Circular imports** - Detect and report import cycles

### Gutter Icons
- Import count indicators
- Inheritance arrows for components using `metadata.inherits`
- Abstract vs. real component type indicators
- Variable override indicators

### Tool Windows
- **Atmos Explorer** - Browse stacks, components, and workflows in a tree view
- **Component Inspector** - Real-time resolved values for the component at cursor position

### CLI Integration
- **Run Configurations** for Atmos commands (terraform plan/apply, describe, validate, workflow)
- **Context Menu Actions** - Right-click to run Atmos commands on current file/component

### Quick Documentation (F1/Ctrl+Q)
- Documentation for imports (file preview)
- Documentation for components (files and variables)
- Documentation for YAML functions
- Documentation for metadata and settings keys

## Getting Started

1. Open a project containing an `atmos.yaml` configuration file
2. The plugin will automatically detect the Atmos project
3. Navigate stack files with Cmd+Click on imports
4. Configure the Atmos executable path in **Settings > Tools > Atmos**
5. Use the Atmos tool window to browse stacks and components
6. Right-click for context actions like "Describe Component" or "Terraform Plan"

<!-- Plugin description end -->

## Installation

### From JetBrains Marketplace

1. Open your JetBrains IDE (IntelliJ IDEA, WebStorm, GoLand, PyCharm, etc.)
2. Go to **Settings/Preferences > Plugins > Marketplace**
3. Search for "Atmos"
4. Click **Install**

### Manual Installation

1. Download the latest release from the [Releases](https://github.com/Benbentwo/atmos-jetbrains-plugin/releases) page
2. Go to **Settings/Preferences > Plugins > Settings icon > Install Plugin from Disk...**
3. Select the downloaded `.zip` file

## Keyboard Shortcuts

| Action | Mac | Windows/Linux |
|--------|-----|---------------|
| Navigate to Definition | Cmd+Click | Ctrl+Click |
| Quick Documentation | F1 | Ctrl+Q |
| Open Atmos Inspector | Cmd+Shift+I | Ctrl+Shift+I |

## Development

### Prerequisites

- JDK 17+
- IntelliJ IDEA (for development)

### Building

```bash
./gradlew buildPlugin
```

### Running in Sandbox IDE

```bash
./gradlew runIde
```

### Running Tests

```bash
./gradlew check
```

## Contributing

Contributions are welcome! Please read the [Contributing Guide](CONTRIBUTING.md) for details.

## License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Atmos](https://atmos.tools/) by Cloud Posse
- [JetBrains Helm Plugin](https://www.jetbrains.com/help/idea/helm.html) for UX inspiration
