# Architecture Rules

This document describes the architectural constraints automatically enforced by `ArchitectureTest.java` in the `qa` module. All rules are validated during the build process.

**Note:** For testing-specific rules and guidelines, see [TESTING_GUIDELINES.md](TESTING_GUIDELINES.md).

## Table of Contents

1. [Visibility Rules](#visibility-rules)
2. [Package Organization Rules](#package-organization-rules)
3. [Logging Rules](#logging-rules)
4. [Running Architecture Tests](#running-architecture-tests)

---

## Visibility Rules

### VR-1: No Private Methods

**Rule:** Classes in `io.camunda.migration.data..` (excluding `..qa..`) must not have private methods.

**Rationale:** Private methods limit testability and extensibility. Use `protected` for methods that might be overridden.

**Exceptions:**
- Enums (synthetic methods are inherently private)
- Lambda expressions
- Classes annotated with `@UtilityClass`

**Enforcement:** `ArchitectureTest.shouldNotHavePrivateMethods()`

**Examples:**
```java
// ✅ GOOD - Protected method
protected void validateProcessInstance(ProcessInstance pi) { ... }

// ❌ BAD - Private method
private void migrateProcessInstances() { ... }
```

---

### VR-2: No Private Fields

**Rule:** Classes in `io.camunda.migration.data..` (excluding `..qa..`) must not have private fields.

**Rationale:** Use `protected` for fields that might be accessed by subclasses.

**Exceptions:** 
- Enums (synthetic fields are inherently private)

**Enforcement:** `ArchitectureTest.shouldNotHavePrivateFields()`

**Examples:**
```java
// ✅ GOOD - Protected field
protected String processDefinitionId;

// ❌ BAD - Private field
private String processInstanceId;
```

---

### VR-3: No Private Constructors

**Rule:** Classes in `io.camunda.migration.data..` (excluding `..qa..`) must not have private constructors.

**Rationale:** Private constructors prevent subclassing. Use `protected` constructors.

**Exceptions:**
- Enums (constructors are inherently private)

**Enforcement:** `ArchitectureTest.shouldNotHavePrivateConstructors()`

**Examples:**
```java
// ✅ GOOD - Protected constructor
protected MyComponent() { ... }

// ❌ BAD - Private constructor (except utility classes)
private MyComponent() { ... }
```

---

## Package Organization Rules

### PO-1: Components Must Reside in Impl Package or Top-Level

**Rule:** Classes annotated with `@Component` or `@Service` must either:
- Reside in the `impl` package (for internal implementations), OR
- Reside at top-level `io.camunda.migration.data` (for public APIs)

**Rationale:** Clear separation between public API and internal implementation details.

**Exceptions:**
- Classes in `..qa..` (test code)
- Classes in `..config..` (configuration)

**Enforcement:** `ArchitectureTest.shouldResideInImplPackageForComponents()`

**Examples:**
```java
// ✅ GOOD - Public API at top level
package io.camunda.migration.data;
@Component
public class HistoryMigrator { ... }

// ✅ GOOD - Internal implementation in impl package
package io.camunda.migration.data.impl.clients;
@Component
public class C7Client { ... }

// ❌ BAD - Component not in impl or top-level
package io.camunda.migration.data.clients;
@Component
public class C7Client { ... }
```

---

### PO-2: @Configuration Classes Must Reside in Config Package

**Rule:** Classes annotated with `@Configuration` in `io.camunda.migration.data..` (excluding `..qa..`) must reside in the `..config..` package.

**Rationale:** Centralizing configuration classes provides clear separation of concerns.

**Enforcement:** -

---

## Logging Rules

### LR-1: Log Classes Must Only Contain Constants

**Rule:** Classes ending with "Logs" in `..impl.logging..` must only contain `public static final` fields.

**Rationale:** Log classes serve as centralized message repositories and should only contain immutable string constants.

**Enforcement:** `ArchitectureTest.shouldOnlyHaveStaticFinalFieldsInLogClasses()`

**Examples:**
```java
// ✅ GOOD - Log class with constants only
public class HistoryMigratorLogs {
    public static final String MIGRATING_INSTANCE = "Migrating historic {} instance with C7 ID: [{}]";
    public static final String SKIP_REASON_MISSING = "Missing process definition";
}

// ❌ BAD - Non-final field
public class HistoryMigratorLogs {
    public static String MIGRATING_INSTANCE = "..."; // Not final
}
```

---

### LR-2: Log Constants Must Be Public Static Final

**Rule:** Static fields in log classes must be `final`.

**Enforcement:** `ArchitectureTest.shouldBePublicStaticFinalForLogConstants()`

---

### LR-3: No System.out or printStackTrace in Production Code

**Rule:** Production code (outside `..qa..` and `..test..` packages) must not use `System.out` or `Throwable.printStackTrace()`.

**Rationale:** Production code should use proper logging frameworks (SLF4J) for consistent log management.

**Enforcement:** `ArchitectureTest.shouldNotUseSystemOutOrPrintStackTrace()`

**Examples:**
```java
// ✅ GOOD - Using SLF4J logger
private static final Logger LOGGER = LoggerFactory.getLogger(MyClass.class);
LOGGER.info("Processing started");
LOGGER.error("Processing failed", e);

// ❌ BAD - Using System.out
System.out.println("Processing started");
e.printStackTrace();
```

---

## Running Architecture Tests

Execute all architecture tests:
```bash
mvn test -Dtest=ArchitectureTest -pl qa
```

Run with full build:
```bash
mvn verify
```

Architecture tests run automatically during the build and will fail if any rule is violated.

---

## Summary Table

| Rule ID | Category | Description | Enforcement Method |
|---------|----------|-------------|-------------------|
| VR-1 | Visibility | No private methods | `shouldNotHavePrivateMethods()` |
| VR-2 | Visibility | No private fields | `shouldNotHavePrivateFields()` |
| VR-3 | Visibility | No private constructors | `shouldNotHavePrivateConstructors()` |
| PO-1 | Package | Components in impl or top-level | `shouldResideInImplPackageForComponents()` |
| PO-2 | Package | Converters end with "Converter" | `shouldFollowConverterNamingConvention()` |
| LR-1 | Logging | Log classes only contain constants | `shouldOnlyHaveStaticFinalFieldsInLogClasses()` |
| LR-2 | Logging | Log constants are final | `shouldBePublicStaticFinalForLogConstants()` |
| LR-3 | Logging | No System.out or printStackTrace | `shouldNotUseSystemOutOrPrintStackTrace()` |

**Note:** Testing rules (TR-1 through TR-5) are documented in [TESTING_GUIDELINES.md](TESTING_GUIDELINES.md).

---

## Related Documentation

- [Testing Guidelines](TESTING_GUIDELINES.md) - Detailed testing philosophy and examples
- [Code Review Checklist](CODE_REVIEW_CHECKLIST.md) - Review checklist for maintainers

