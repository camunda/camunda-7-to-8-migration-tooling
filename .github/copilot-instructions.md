# Copilot Instructions for Camunda 7 to 8 Data Migrator

## Project Overview

This repository contains the Camunda 7 to 8 Data Migrator, a tool that helps organizations migrate their Camunda 7 process instances and data to Camunda 8 while preserving execution state and variables.

**Key Points:**
- The runtime migrator is targeting production readiness with Camunda 8.8 release
- The history migrator is EXPERIMENTAL and not intended for production use
- This tool is critical for seamless migration with minimal disruption to business processes

## Technology Stack

- **Java:** 21 (required)
- **Maven:** 3.6+ for building
- **Spring Boot:** 3.x
- **Frontend:** React 18.x (Cockpit plugin only)
- **Databases:** H2, PostgreSQL, Oracle
- **Build Tool:** Maven with multi-module structure

## Module Structure

The project is organized as a multi-module Maven project:

- `core/` - Core migration logic and application
- `plugins/cockpit/` - Camunda Cockpit plugin (includes React frontend)
- `examples/` - Example implementations (e.g., variable interceptors)
- `qa/` - Quality assurance and integration tests
- `distro/` - Distribution assembly
- `assembly/` - Final packaging

## Code Quality Standards

### License Headers

**CRITICAL:** Every source file must include the Camunda license header. Use the exact format from `license/header.txt`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
```

### Java Conventions

- Follow standard Java coding conventions
- Use meaningful variable and method names
- Add JavaDoc comments for public APIs
- Keep methods focused and concise
- Prefer composition over inheritance where appropriate

### Testing

- Write unit tests for all new functionality
- Use JUnit Jupiter (version 6.0.1+) for tests
- Integration tests go in the `qa/` module
- Tests should be deterministic and isolated
- Follow existing test patterns in the codebase

## Build and Test Commands

### Maven Configuration

The project uses Camunda dependencies from private repositories. The repository configuration is already included in the `pom.xml`. Maven will automatically use these repositories when building:
- `https://artifacts.camunda.com/artifactory/zeebe-io-snapshots/` for snapshots
- `https://artifacts.camunda.com/artifactory/camunda-bpm-snapshots/` for Camunda BPM snapshots

No additional settings.xml configuration is required.

### Building the Project

```bash
# Full build with all tests
mvn clean install

# Build without tests (faster for iteration)
mvn clean install -DskipTests

# Build excluding Cockpit plugin (requires Node.js)
mvn clean install -pl '!plugins/cockpit'
```

### Running Tests

```bash
# All tests
mvn verify

# Unit tests only
mvn test

# Integration tests only
mvn integration-test

# Tests with specific database
mvn verify -Ppostgresql
mvn verify -Poracle
```

### Frontend Development (Cockpit Plugin)

```bash
cd plugins/cockpit/frontend

# Install dependencies
npm install

# Development build with watch
npm run dev

# Production build
npm run build
```

## Development Workflow

### Prerequisites

- Java 21 must be installed and set as JAVA_HOME
- Maven 3.6+ must be available
- Node.js is required only for the Cockpit plugin
- Docker is recommended for database testing

### Making Changes

1. **Module-Specific Builds:** You can build individual modules by running Maven commands in their directories to speed up development
2. **Incremental Testing:** Run tests relevant to your changes before running the full test suite
3. **Database Testing:** Use Docker to test with PostgreSQL or Oracle if making database-related changes
4. **Distribution Testing:** After significant changes, build the full distribution from `assembly/target/`

### Code Changes Guidelines

- Make minimal, focused changes that address the specific issue
- Don't modify working code unnecessarily
- Update documentation when adding or changing features:
  - For complex changes, add comments in the code explaining the approach
  - Update JavaDoc for all public API changes
  - Update README.md if the changes affect installation, usage, or features
  - If changes require updates to the official documentation at docs.camunda.io, note this in the PR description
- Ensure changes are backward compatible unless explicitly intended otherwise
- Be mindful of production readiness status (runtime vs. history migrator)

## Common Patterns

### Variable Interceptors

When creating or modifying variable interceptors:
- Extend the `VariableInterceptor` interface
- Package as standalone JAR when needed
- See `examples/variable-interceptor/` for reference implementation

### Database Support

When adding database-specific code:
- Test with H2, PostgreSQL, and Oracle profiles
- Use JDBC standards to maintain compatibility
- Add profile-specific tests in the `qa/` module

### Spring Boot Configuration

- Use `application.properties` or `application.yml` for configuration
- Support externalized configuration for deployment flexibility
- Document all configuration properties

## Pull Request Quality

### Commit Messages

Follow conventional commits format:
```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

**Types:**
- `feat`: New feature
- `fix`: Bug fix
- `refactor`: Code refactoring
- `chore`: Maintenance tasks (dependencies, build, etc.)
- `docs`: Documentation changes
- `test`: Test additions or modifications
- `perf`: Performance improvements

**Scope:** Module or component affected (e.g., `core`, `history`, `distro`, `deps`)

**Body:** Reference the related issue in the commit message body using `related to #<issue-number>`

**Examples:**
- `feat(runtime): add support for message correlation`
- `fix(core): resolve variable serialization issue`
- `chore(deps): update Spring Boot to 3.5.8`
- `refactor(history): move MyBatis mappers into DbClient`

### Pull Request Guidelines

- Include clear, descriptive titles using conventional commits format
- Provide detailed description explaining the changes and their purpose
- Reference related issues using `related to #<issue-number>` (not `closes #<issue-number>`)
- Keep PRs focused on a single feature or fix
- Ensure all tests pass locally before submitting
- Wait for CI checks to complete (H2, PostgreSQL, Oracle, Windows builds)

## CI/CD

The project uses GitHub Actions for CI with multiple database environments:
- `distro-h2`: Default build with H2 database
- `postgresql`: Tests with PostgreSQL
- `oracle`: Tests with Oracle
- `windows-h2`: Windows-specific builds

**Note:** You cannot merge pull requests. Wait for CI checks to complete and address any failures. A human reviewer will merge the PR after approval.

## Additional Resources

- [Official Documentation](https://docs.camunda.io/docs/next/guides/migrating-from-camunda-7/data-migrator/)
- [Camunda 8 Documentation](https://docs.camunda.io/)
- [Migration Guide](https://docs.camunda.io/docs/next/guides/migrating-from-camunda-7/)
- [Issue Tracker](https://github.com/camunda/camunda-bpm-platform/issues?q=is%3Aissue%20state%3Aopen%20label%3Ascope%3Adata-migrator)

## Code Review Philosophy

When reviewing code or providing suggestions:
- Focus on correctness, security, and maintainability
- Check for proper license headers
- Verify test coverage is adequate
- Ensure database compatibility is maintained
- Validate that changes don't break existing functionality
- Only suggest changes with high confidence in their value
