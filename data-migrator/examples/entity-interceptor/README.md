# Entity Interceptor Example

This example demonstrates how to create an entity interceptor for the Data Migrator that can be packaged as a JAR and configured via YAML, without requiring Spring Boot annotations.

## Overview

The entity interceptor system allows you to:
- Customize the conversion of Camunda 7 historic entities to Camunda 8 database models
- Package entity interceptors in standalone JARs without Spring Boot dependencies
- Drop JARs in the `configuration/userlib` folder of the Data Migrator distribution
- Configure interceptors via `application.yml` instead of using `@Component` annotations
- Set custom properties for each interceptor instance
- Restrict interceptors to specific entity types (or handle all types)

## Building the Interceptor

```bash
mvn clean package
```

This creates a JAR file in the `target` directory that can be deployed to the Data Migrator.

## Deployment

1. Copy the generated JAR file to the `configuration/userlib` folder of your Data Migrator installation
2. Configure the interceptor in your `application.yml` file (see configuration example below)
3. Start the Data Migrator with history migration mode: `./start.sh --history`

## Configuration Example

Add the following to your `application.yml`:

```yaml
camunda:
  migrator:
    interceptors:
      - class-name: io.camunda.migration.data.example.MyCustomEntityInterceptor
        enabled: true
        properties:
          auditEnabled: true
          tenantPrefix: "PROD_"
      - class-name: io.camunda.migration.data.example.ProcessInstanceEnricher
        enabled: true
        properties:
          enrichMetadata: true
          enableLogging: true
      - class-name: io.camunda.migration.data.example.UserTaskAndVariableInterceptor
        enabled: true
        properties:
          normalizeAssignees: true
          validateVariables: true
          assigneePrefix: "user_"
```

## Interceptor Examples

This module provides three example interceptors that demonstrate different patterns:

### `MyCustomEntityInterceptor`
**Universal entity handler** - processes all entity types

- **Audit Logging**: Configurable logging of entity conversions
- **Tenant Transformation**: Adds a configurable prefix to tenant IDs
- **Property Injection**: Shows how config properties are automatically injected
- **Cross-cutting Concerns**: Demonstrates handling logic that applies to all entities

**Configuration properties:**
- `auditEnabled` (boolean): Enable/disable audit logging (default: true)
- `tenantPrefix` (string): Prefix to add to tenant IDs (default: "")

### `ProcessInstanceEnricher`
**Type-specific handler** - only processes `HistoricProcessInstance` entities

- **Type Restriction**: Shows how to handle specific entity types
- **Process Engine Access**: Demonstrates accessing Camunda 7 process engine for additional data
- **Builder Manipulation**: Shows how to modify the Camunda 8 entity builder
- **Custom Metadata Enrichment**: Adds deployment information to process instances
- **Lifecycle Methods**: Demonstrates both `presetParentProperties()` and `execute()`

**Configuration properties:**
- `enrichMetadata` (boolean): Enable/disable metadata enrichment (default: true)
- `enableLogging` (boolean): Enable/disable logging (default: true)

### `UserTaskAndVariableInterceptor`
**Multi-type handler** - processes both `HistoricTaskInstance` and `HistoricVariableInstance` entities

- **Multi-Type Handling**: Shows how to handle multiple entity types in one interceptor
- **Type Detection**: Demonstrates branching logic based on entity type
- **Assignee Normalization**: Normalizes user task assignees with configurable prefix
- **Variable Validation**: Validates variables and detects sensitive data
- **Custom Business Logic**: Shows task-specific and variable-specific processing

**Configuration properties:**
- `normalizeAssignees` (boolean): Enable/disable assignee normalization (default: true)
- `validateVariables` (boolean): Enable/disable variable validation (default: true)
- `assigneePrefix` (string): Prefix to add to normalized assignees (default: "")

## Entity Types

Entity interceptors can handle the following Camunda 7 historic entity types:

- `ProcessDefinition` - Process definitions
- `HistoricProcessInstance` - Process instances
- `HistoricActivityInstance` - Flow nodes/activities
- `HistoricTaskInstance` - User tasks
- `HistoricVariableInstance` - Process variables
- `HistoricIncident` - Process incidents
- `HistoricDecisionInstance` - Decision instances
- `HistoricDecisionDefinition` - Decision definitions
- `HistoricDecisionRequirementsDefinition` - Decision requirements definitions

## Creating Your Own Interceptor

1. Create a new Maven project with the provided `pom.xml` structure
2. Add a dependency on `camunda-7-to-8-data-migrator-core` (scope: provided)
3. Implement the `EntityInterceptor` interface
4. Override `getTypes()` to specify which entity types to handle (or return empty set for all types)
5. Implement `execute()` method with your conversion logic
6. Optionally override `presetParentProperties()` for hierarchical relationships
7. Add setter methods for any configurable properties
8. Package as JAR and deploy to the `configuration/userlib` folder
9. Configure in `application.yml`

## Interceptor Lifecycle

Entity interceptors have two lifecycle methods that are called during entity conversion:

1. **`presetParentProperties(context)`** - Called first, used to set parent-related properties like `processDefinitionKey` or `parentProcessInstanceKey`.
Data Migrator ensures that parent entities are processed before child entities, and those properties are set accordingly when information about them is available.
This method should be used set the properties when Data Migrator cannot automatically determine them.
2. **`execute(context)`** - Called second, performs the main conversion logic

Both methods are optional (you only need to implement what you need).

## Accessing the Camunda 7 Process Engine

You can access the Camunda 7 process engine from the context to retrieve additional information:

```java
@Override
public void execute(EntityConversionContext<?, ?> context) {
    ProcessEngine processEngine = context.getProcessEngine();
    
    // Example: Query for additional data
    String deploymentId = processEngine.getRepositoryService()
        .createProcessDefinitionQuery()
        .processDefinitionKey(processDefinitionKey)
        .singleResult()
        .getDeploymentId();
    
    // Use the retrieved data in your conversion logic
}
```

## Type Safety

When working with specific entity types, you can safely cast the entities and builders:

```java
@Override
public Set<Class<?>> getTypes() {
    return Set.of(HistoricProcessInstance.class);
}

@Override
public void execute(EntityConversionContext<?, ?> context) {
    // Safe to cast because getTypes() restricts to HistoricProcessInstance
    HistoricProcessInstance c7Instance = 
        (HistoricProcessInstance) context.getC7Entity();
    ProcessInstanceDbModel.Builder c8Builder = 
        (ProcessInstanceDbModel.Builder) context.getC8DbModelBuilder();
    
    // Now you can access type-specific methods
}
```

## See Also

- [History Migration Documentation](https://docs.camunda.io/docs/guides/migrating-from-camunda-7/data-migrator/history/)
- [Variable Interceptor Example](../variable-interceptor/)

