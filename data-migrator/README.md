# Camunda 7 to 8 Data Migrator

[![Java Version](https://img.shields.io/badge/Java-21-blue)](https://www.oracle.com/java/technologies/downloads/#java21)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)](https://spring.io/projects/spring-boot)
[![Status](https://img.shields.io/badge/Status-In%20Development-yellow)](https://github.com/camunda/camunda-7-to-8-migration-tooling)

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
1. Download the latest release from the [releases page](https://github.com/camunda/camunda-7-to-8-migration-tooling/releases)
2. Extract the archive to your preferred directory
3. Navigate to the extracted directory

### Option 2: Build from Source
```bash
# Clone the repository
git clone https://github.com/camunda/camunda-7-to-8-migration-tooling.git
cd camunda-7-to-8-migration-tooling/data-migrator

# Build the data-migrator module
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
   git clone https://github.com/camunda/camunda-7-to-8-migration-tooling.git
   cd camunda-7-to-8-migration-tooling/data-migrator
   ```

2. **Build the project**:
   ```bash
   mvn clean install
   ```
   This will build all submodules and execute unit tests. You can restrict the build to the module you are changing by running the same command in the corresponding directory.
   
   The Cockpit plugin module requires Node.js. You can exclude building it by running:
   ```bash
   mvn clean install -pl '!data-migrator/plugins/cockpit'
   ```

3. **Find distribution** in `data-migrator/assembly/target/` directory

4. The **Migrator Cockpit Plugin** can be found in `data-migrator/plugins/cockpit/target` directory

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