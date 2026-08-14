# Camunda 7 to 8 Migration Tooling

This repository contains a collection of tools to help you migrate from Camunda 7 to Camunda 8.

## CI Status

| Branch           | CI Status |
|------------------|-----------|
| **main**         | [![CI main](https://github.com/camunda/camunda-7-to-8-migration-tooling/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/camunda/camunda-7-to-8-migration-tooling/actions/workflows/ci.yml?query=branch%3Amain) |
| **maintenance/0.1** | [![CI maintenance/0.1](https://github.com/camunda/camunda-7-to-8-migration-tooling/actions/workflows/ci.yml/badge.svg?branch=maintenance/0.1)](https://github.com/camunda/camunda-7-to-8-migration-tooling/actions/workflows/ci.yml?query=branch%3Amaintenance%2F0.1) |

## Available Tools

### [Data Migrator](./data-migrator/)
A tool for migrating Camunda 7 process instances and related data to Camunda 8. This migrator helps organizations seamlessly transition their process instances while preserving execution state and variables ensuring minimal disruption to ongoing business processes.

Please see the official documentation for more details: [Camunda 7 to 8 Migration Guide](https://docs.camunda.io/docs/next/guides/migrating-from-camunda-7/data-migrator/).

### [Diagram Converter](./diagram-converter/)
A tool for analyzing and converting Camunda 7 models (BPMN & DMN) to Camunda 8 format. The Diagram Converter identifies migration tasks and automatically converts process models, with support for custom extensions to accommodate special requirements.

For detailed information, see the [Diagram Converter README](./diagram-converter/README.md).

### [Code Conversion](./code-conversion/)
A comprehensive toolkit for converting Camunda 7 code to Camunda 8. This module provides:

- **[Code Conversion Patterns](./code-conversion/patterns/)** - Best practices and patterns for migrating Java (Spring) client code and glue code
- **[OpenRewrite Recipes](./code-conversion/recipes/)** - Automated refactoring recipes to streamline Java code migration
- **[API Mapping WebApp](https://camunda.github.io/camunda-7-to-8-migration-tooling/)** - Interactive tool showing how Camunda 7 API endpoints map to Camunda 8

For detailed information, see the [Code Conversion README](./code-conversion/README.md).


## Documentation

- **[Camunda 7 to 8 Migration Guide](https://docs.camunda.io/docs/next/guides/migrating-from-camunda-7/)** - Official migration documentation
- **[Data Migrator Documentation](./data-migrator/README.md)** - Detailed data migrator documentation
- **[Diagram Converter Documentation](./diagram-converter/README.md)** - Model analysis and conversion tools
- **[Code Conversion Documentation](./code-conversion/README.md)** - Code conversion patterns and tools
- **[Camunda 8 Documentation](https://docs.camunda.io/)** - Official Camunda 8 documentation

## Contributing

We welcome contributions to the Data Migrator! Here's how you can help:

### Ways to Contribute

1. **Report bugs** - Create detailed issue reports
2. **Suggest features** - Propose new functionality
3. **Submit code** - Fix bugs or implement features
4. **Improve documentation** - Help others understand the tool
5. **Test and provide feedback** - Try the tool and share your experience

See our [issue tracker](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/).

### Before Contributing

1. **Read the [Contributions Guide](https://github.com/camunda/camunda-bpm-platform/blob/master/CONTRIBUTING.md)**
2. **Check existing issues** to avoid duplicates [link](https://github.com/camunda/camunda-7-to-8-migration-tooling/issues/)
3. **Discuss major changes** in an issue before implementing

### Development Guidelines

- **Follow Java coding standards** and existing code style
- **Write tests** for new functionality
- **Update documentation** when adding features
- **Use meaningful commit messages**
- **Keep changes focused** - one feature/fix per pull request

### License Headers

Every source file must contain the license header. See [license header template](./license/header.txt) for the exact format required.

### Pull Request Process

1. Fork the repository
2. Create a feature branch from `main`
3. Make your changes with tests
4. Ensure all tests pass
5. Update documentation if needed
6. Submit a pull request with a clear description

## Releasing

The `Release` GitHub Actions workflow is the supported way to publish release artifacts.

### Standard release

1. Create a `release/<version>` branch from the commit you want to release, or run from the relevant `maintenance/<line>` branch when releasing an existing maintenance line.
2. Run the `Release` workflow from that `release/<version>` or `maintenance/<line>` branch with:
   - `RELEASE_VERSION=<version>`
   - `DEVELOPMENT_VERSION=<next-version>-SNAPSHOT`
   - `IS_DRY_RUN=false`
   - `ONLY_PUSH_TO_MAVEN_CENTRAL=false`
3. The workflow now auto-publishes the validated Sonatype Central deployment, so no manual "Publish" step is required in the Central UI.
4. The workflow waits until the deployment is published and verifies that all Maven Central artifacts are available before the release succeeds.

### Backfill Maven Central for an existing tag

If a GitHub release/tag already exists but Maven Central is missing the artifacts, rerun the `Release` workflow with:

- `RELEASE_VERSION=<existing-tag>`
- `DEVELOPMENT_VERSION=<next-version>-SNAPSHOT` (required workflow input; ignored in backfill mode)
- `IS_DRY_RUN=false`
- `ONLY_PUSH_TO_MAVEN_CENTRAL=true`

Run the workflow from the current `maintenance/0.2` branch so it uses the fixed publication workflow, not the old workflow definition stored in the release tag. This mode skips `release:prepare`, checks out the release tag, rebuilds the artifacts from that source, and publishes them to Maven Central only.
It performs the same publication verification and fails if any expected artifact is still unavailable after the Central propagation timeout.

## License

The source files in this repository are made available under the [Camunda License Version 1.0](./CAMUNDA-LICENSE-1.0.txt).

---

## Additional Resources

- **[Camunda 8 Documentation](https://docs.camunda.io/)** - Official Camunda 8 documentation
- **[Migration Guide](https://docs.camunda.io/docs/next/guides/migrating-from-camunda-7/)** - General migration guidance
- **[Migration Analyzer](https://migration-analyzer.consulting-sandbox.camunda.cloud/)** - Tool for migrating BPMN models
- **[Community Forum](https://forum.camunda.io/)** - Get help from the community
- **[GitHub Issues](https://github.com/camunda/camunda-bpm-platform/issues)** - Report bugs and request features in the issue tracker
