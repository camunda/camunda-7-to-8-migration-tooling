# Variable Interceptor Example

This example demonstrates how to create a variable interceptor for the Data Migrator that can be packaged as a JAR and configured via YAML, without requiring Spring Boot annotations.

## Overview

The interceptor system allows you to:
- Package variable interceptors in standalone JARs without Spring Boot dependencies
- Drop JARs in the `userlib` folder of the Data Migrator distribution
- Configure interceptors via `application.yml` instead of using `@Component` annotations
- Set custom properties for each interceptor instance

## Building the Interceptor

```bash
mvn clean package
```

This creates a JAR file in the `target` directory that can be deployed to the Data Migrator.

## Deployment

1. Copy the generated JAR file to the `userlib` folder of your Data Migrator installation
2. Configure the interceptor in your `application.yml` file (see configuration example below)
3. Start the Data Migrator

## Configuration Example

Add the following to your `application.yml`:

```yaml
camunda:
  migrator:
    interceptors:
      - class-name: io.camunda.migrator.example.MyCustomVariableInterceptor
        properties:
          prefix: "CUSTOM_"
          enableLogging: true
      - class-name: io.camunda.migrator.example.AnotherInterceptor
        properties:
          someProperty: "someValue"
```

## Interceptor Features

The example `MyCustomVariableInterceptor` demonstrates:

- **Variable Name Transformation**: Adds a configurable prefix to variable names
- **Type Conversion**: Converts variable values to strings if configured
- **Configurable Logging**: Enable/disable detailed logging via configuration
- **Property Injection**: Shows how config properties are automatically injected

## Creating Your Own Interceptor

1. Create a new Maven project with the provided `pom.xml` structure
2. Add a dependency on `camunda-7-to-8-data-migrator-core` (scope: provided)
3. Implement the `VariableInterceptor` interface
4. Add setter methods for any configurable properties
5. Package as JAR and deploy to the `userlib` folder
6. Configure in `application.yml`

## Key Differences from Spring Boot Approach

- **No `@Component` annotation required**
- **No Spring Boot dependencies needed**
- **Configured via config data file instead of component scanning**
- **Can be packaged and deployed independently**
- **Properties injected automatically from config data file**
