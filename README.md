# Camunda 7 to 8 Data Migrator

[![Java Version](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Status](https://img.shields.io/badge/Status-In%20Development-yellow)](https://github.com/camunda/camunda-7-to-8-data-migrator)

A tool for migrating Camunda 7 process instances and related data to Camunda 8. This migrator helps organizations seamlessly transition their process instances while preserving execution state and variables ensuring minimal disruption to ongoing business processes.

> [!WARNING]  
> **Production Readiness Status:**
> - **Runtime Migrator**: Will be production-ready and officially supported with the Camunda 8.8 release
> - **History Migrator**: Currently **EXPERIMENTAL** and **NOT intended for production use**. It will remain in experimental status even after the 8.8 release.
> 
> We encourage users to try the runtime migrator in development/testing environments and provide feedback to help us improve the tool before the 8.8 release.

Please see the official documentation for more details: [Camunda 7 to 8 Migration Guide](https://docs.camunda.io/docs/next/guides/migrating-from-camunda-7/data-migrator/).

## Table of Contents

- [Key Features](#key-features)
- [Prerequisites](#prerequisites)
- [Installation & Setup](#installation--setup)
- [Development](#development)
- [Contributing](#contributing)
- [License](#license)

## Key Features

- **State-preserving migration**: Maintains exact execution state of running process instances
- **Variable data migration**: Converts and migrates process variables with proper type handling
- **Validation and verification**: Pre-migration validation to ensure successful migration
- **Skip and retry capabilities**: Handle problematic instances gracefully with retry options
- **Detailed logging and reporting**: Comprehensive logging for monitoring migration progress
- **Database flexibility**: Support for multiple database vendors (H2, PostgreSQL, Oracle)

## Prerequisites

Before using the Data Migrator, ensure you have:

- **Java 21 or higher** - Required for running the migrator
- **Maven 3.6+** - For building from source (if not using pre-built releases)
- **Running Camunda 8 instance** - Target platform for migration
- **Access to Camunda 7 database** - Source database with process instances to migrate
- **Migrated BPMN models** - Process definitions already converted from C7 to C8 format
- **Network connectivity** - Between migrator, C7 database, and C8 platform

## Installation & Setup

### Option 1: Download Pre-built Release
1. Download the latest release from the [releases page](https://github.com/camunda/camunda-7-to-8-data-migrator/releases)
2. Extract the archive to your preferred directory
3. Navigate to the extracted directory

### Option 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/camunda/camunda-7-to-8-data-migrator.git
cd camunda-7-to-8-data-migrator

# Build the project
mvn clean install -DskipTests

# Navigate to the distribution
cd assembly/target
# Extract the generated archive (tar.gz or zip)
```

### Quick Start

```bash
# On Linux/macOS
./start.sh --help

# On Windows
start.bat --help
```

## Development

### Building from Source

1. **Clone the repository**:
   ```bash
   git clone https://github.com/camunda/camunda-7-to-8-data-migrator.git
   cd camunda-7-to-8-data-migrator
   ```

2. **Build the project**:
   ```bash
   mvn clean install
   ```
   This will build all submodules and execute unit tests. You can restrict the build to the module you are changing by running the same command in the corresponding directory.
   
   The Cockpit plugin module requires Node.js. You can exclude building it by running:
   ```bash
   mvn clean install -pl '!plugins/cockpit'
   ```

3. **Find distribution** in `assembly/target/` directory

4. The **Migrator Cockpit Plugin** can be found in `plugins/cockpit/target` directory

### Running Tests

Execute the full test suite:
```bash
mvn verify
```

Run specific test categories:
```bash
# Unit tests only
mvn test

# Integration tests only
mvn integration-test

# Architecture tests (enforce testing best practices)
mvn test -Dtest=ArchitectureTest
```

### Testing Guidelines

We follow a **black-box testing approach** where tests verify behavior through observable outputs rather than internal implementation details. 

**Key Principles:**
- ✅ Use `LogCapturer` to verify skip reasons and behavior via logs
- ✅ Use C8 API queries to verify successful migrations
- ✅ Create natural skip scenarios (e.g., migrate children without parents)
- ❌ Don't access `DbClient` or `IdKeyMapper` in tests
- ❌ Don't manipulate internal database state

**Resources:**
- 📖 [TESTING_GUIDELINES.md](TESTING_GUIDELINES.md) - Comprehensive testing guide with examples
- 📋 [CODE_REVIEW_CHECKLIST.md](CODE_REVIEW_CHECKLIST.md) - Checklist for code reviewers
- 🏗️ `ArchitectureTest.java` - Automated enforcement of testing rules

**Example:**
```java
@Test
void shouldSkipInstanceWhenDefinitionMissing() {
    // given: natural skip scenario - no C8 deployment
    deployer.deployCamunda7Process("process.bpmn");
    var instance = runtimeService.startProcessInstanceByKey("processId");
    
    // when
    runtimeMigrator.start();
    
    // then: verify via observable outputs
    logs.assertContains("Skipping process instance with C7 ID");
    assertThatProcessInstanceCountIsEqualTo(0);
}
```

### Development Environment Setup

1. **Install prerequisites**:
   - Java 21+
   - Maven 3.6+
   - Docker (for testing with different databases)

2. **Set up IDE** (IntelliJ IDEA/Eclipse):
   - Import as Maven project
   - Configure Java 21 as project SDK
   - Install Spring Boot plugin (recommended)

3. **Local development database**:
   ```bash
   # Start PostgreSQL with Docker
   docker run --name postgres-dev \
     -e POSTGRES_DB=camunda7 \
     -e POSTGRES_USER=camunda \
     -e POSTGRES_PASSWORD=camunda \
     -p 5432:5432 -d postgres:17
   ```

## Contributing

We welcome contributions to the Data Migrator! Here's how you can help:

### Ways to Contribute

1. **Report bugs** - Create detailed issue reports
2. **Suggest features** - Propose new functionality
3. **Submit code** - Fix bugs or implement features
4. **Improve documentation** - Help others understand the tool
5. **Test and provide feedback** - Try the tool and share your experience

See our [issue tracker](https://github.com/camunda/camunda-bpm-platform/issues).

### Before Contributing

1. **Read the [Contributions Guide](https://github.com/camunda/camunda-bpm-platform/blob/master/CONTRIBUTING.md)**
2. **Check existing issues** to avoid duplicates [link](https://github.com/camunda/camunda-bpm-platform/issues?q=is%3Aissue%20state%3Aopen%20label%3Ascope%3Adata-migrator)
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

## License

The source files in this repository are made available under the [Camunda License Version 1.0](./CAMUNDA-LICENSE-1.0.txt).

---

## Additional Resources

- **[Camunda 8 Documentation](https://docs.camunda.io/)** - Official Camunda 8 documentation
- **[Migration Guide](https://docs.camunda.io/docs/next/guides/migrating-from-camunda-7/)** - General migration guidance
- **[Migration Analyzer](https://migration-analyzer.consulting-sandbox.camunda.cloud/)** - Tool for migrating BPMN models
- **[Community Forum](https://forum.camunda.io/)** - Get help from the community
- **[GitHub Issues](https://github.com/camunda/camunda-bpm-platform/issues)** - Report bugs and request features in the issue tracker
