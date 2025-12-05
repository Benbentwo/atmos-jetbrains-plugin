# Atmos Plugin for JetBrains IDEs

[![Build](https://github.com/cloudposse/atmos-jetbrains-plugin/actions/workflows/build.yml/badge.svg)](https://github.com/cloudposse/atmos-jetbrains-plugin/actions/workflows/build.yml)

First-class IDE support for [Atmos](https://atmos.tools/) - the ultimate DevOps framework for Terraform, OpenTofu, Packer, and Helmfile orchestration.

<!-- Plugin description -->
## Features

- **Syntax Highlighting** for Atmos YAML functions (`!env`, `!exec`, `!terraform.output`, etc.)
- **Navigation** - Cmd+Click on imports to navigate to referenced files
- **Code Completion** for imports, components, and variables
- **Inspections** for missing imports and unknown components
- **Gutter Icons** for inheritance relationships
- **Tool Window** for browsing stacks and components
- **Run Configurations** for Atmos CLI commands

## Getting Started

1. Open a project containing an `atmos.yaml` configuration file
2. The plugin will automatically detect the Atmos project
3. Navigate stack files with Cmd+Click on imports
4. Configure the Atmos executable path in **Settings > Tools > Atmos**

<!-- Plugin description end -->

## Installation

### From JetBrains Marketplace

1. Open your JetBrains IDE (IntelliJ IDEA, WebStorm, GoLand, PyCharm, etc.)
2. Go to **Settings/Preferences > Plugins > Marketplace**
3. Search for "Atmos"
4. Click **Install**

### Manual Installation

1. Download the latest release from the [Releases](https://github.com/cloudposse/atmos-jetbrains-plugin/releases) page
2. Go to **Settings/Preferences > Plugins > ⚙️ > Install Plugin from Disk...**
3. Select the downloaded `.zip` file

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
