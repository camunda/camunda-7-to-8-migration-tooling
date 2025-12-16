With the release of 0.2.0 the following breaking changes take effect:

## Code Conversion

Code conversion is now officially supported by Camunda. The following changes have been made:

### Repository Location

- **Old**: `camunda-community-hub/camunda-7-to-8-code-conversion`
- **New**: `camunda/camunda-7-to-8-migration-tooling`

### Package Naming

- **Old**: `org.camunda.migration.rewrite.*`
- **New**: `io.camunda.migration.code.*`

### Module Naming

The following Maven modules have been renamed:

- **Parent Module**:
  - **Old**: `org.camunda.migration:camunda-7-to-8-rewrite-recipes-parent`
  - **New**: `io.camunda:camunda-7-to-8-code-conversion-parent`

- **Recipes Module**:
  - **Old**: `org.camunda.migration:camunda-7-to-8-rewrite-recipes`
  - **New**: `io.camunda:camunda-7-to-8-code-conversion-recipes`

### GroupId Change

All artifacts now use the `io.camunda` groupId instead of `org.camunda.migration`.

## Model Converter

Model converter is now officially supported by Camunda. The following changes have been made:

### Repository Location

- **Old**: `camunda-community-hub/camunda-7-to-8-model-conversion`
- **New**: `camunda/camunda-7-to-8-migration-tooling`

### Documentation Location

- **Old**: `https://camunda-community-hub.github.io/camunda-7-to-8-code-conversion/`
- **New**: `https://camunda.github.io/camunda-7-to-8-migration-tooling/`

### Module Naming

The following Maven modules are now available:

- **Parent Module**: `io.camunda:camunda-7-to-8-model-converter-parent`
- **Core Module**: `io.camunda:camunda-7-to-8-model-converter-core`
- **Web Application**: `io.camunda:camunda-7-to-8-model-converter-webapp`
- **CLI**: `io.camunda:camunda-7-to-8-model-converter-cli`

### GroupId Change

All artifacts now use the `io.camunda` groupId.

## Migration Guide

### For Code Conversion Users

If you are using the OpenRewrite recipes in your project, update your dependencies:

**Maven**:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-7-to-8-code-conversion-recipes</artifactId>
  <version>0.2.0</version>
</dependency>
```

**Update Imports**: Change all imports from:

```java
import org.camunda.migration.rewrite.*;
```

to:

```java
import io.camunda.migration.code.*;
```

### For Model Converter Users

If you are embedding the model converter as a library, update your dependencies:

**Maven**:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>camunda-7-to-8-model-converter-core</artifactId>
  <version>0.2.0</version>
</dependency>
```

For the web application or CLI, download the latest releases from:
`https://github.com/camunda/camunda-7-to-8-migration-tooling/releases`