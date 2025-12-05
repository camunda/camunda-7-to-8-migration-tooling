# Testing Guidelines

This document provides comprehensive guidance for writing tests in the Camunda 7 to 8 Data Migrator project.

## Table of Contents

1. [Philosophy: Black-Box Testing](#philosophy-black-box-testing)
2. [Testing Rules (Enforced by Architecture Tests)](#testing-rules-enforced-by-architecture-tests)
   - [TR-1: Black-Box Testing - No Access to Migrator Implementation](#tr-1-black-box-testing---no-access-to-migrator-implementation)
   - [TR-2: No Direct Access to Camunda BPM Engine Implementation](#tr-2-no-direct-access-to-camunda-bpm-engine-implementation)
   - [TR-3: Test Method Naming Convention](#tr-3-test-method-naming-convention)
   - [TR-4: Tests Must Extend Appropriate Abstract Test Class](#tr-4-tests-must-extend-appropriate-abstract-test-class)
   - [TR-5: Test Classes Must End with "Test" Suffix](#tr-5-test-classes-must-end-with-test-suffix)
3. [DO: Use Observable Outputs](#-do-use-observable-outputs)
4. [DON'T: Use Internal Implementation Details](#-dont-use-internal-implementation-details)
5. [Test Structure](#test-structure)
6. [Examples by Test Type](#examples-by-test-type)
7. [White-Box Testing Exception](#white-box-testing-exception)
8. [Helper Methods](#helper-methods)
9. [Common Pitfalls](#common-pitfalls)
10. [Architecture Test Enforcement](#architecture-test-enforcement)
11. [Migration Guide](#migration-guide)
12. [Summary of Testing Rules](#summary-of-testing-rules)
13. [Summary Checklist](#summary-checklist)

---

## Philosophy: Black-Box Testing

All tests in the `qa` module should follow a **black-box testing approach**. This means tests verify behavior through **observable outputs** rather than internal implementation details.

### Why Black-Box Testing?

1. **Reduced coupling** - Tests don't break when internal implementation changes
2. **Better design** - Forces thinking about observable behavior
3. **Realistic scenarios** - Tests use real-world conditions
4. **Maintainability** - Tests are easier to understand and maintain
5. **Encapsulation** - Internal implementation details remain protected

---

## Testing Rules (Enforced by Architecture Tests)

### TR-1: Black-Box Testing - No Access to Migrator Implementation

**Rule:** Test classes in the `..qa..` package must not access classes in the `io.camunda.migration.data.impl` package.

**Rationale:** Tests should verify behavior through observable outputs (logs, API responses) rather than internal implementation details. This ensures tests remain stable when refactoring internal implementations.

**Exceptions:**
1. **Constants Access:** Tests may access `public static final` fields and enums from impl classes (e.g., `TYPE.HISTORY_PROCESS_INSTANCE`, logging constants)
2. **Enum Methods:** Method calls on enum types are allowed (e.g., `enum.getDisplayName()`)
3. **Test Setup/Cleanup:** Methods annotated with `@BeforeEach` or `@AfterEach` may access impl classes for test infrastructure setup
4. **White-Box Tests:** Classes or methods annotated with `@WhiteBox` are exempt (for persistence/infrastructure testing)

**Enforcement:** `ArchitectureTest.shouldNotAccessImplClasses()`

**Examples:**
```java
// ❌ BAD - Accessing internal implementation
List<IdKeyDbModel> skipped = dbClient.findSkippedProcessInstances();

// ❌ BAD - Log message hardcoded instead of using log constant
logs.assertContains("Migration of historic user task with C7 ID [" + taskId + "] skipped");

// ✅ GOOD - Log message using constant
logs.assertContains(formatMessage(SKIPPING_INSTANCE_MISSING_DEFINITION, "process", processInstance.getId(), "process"));

// ✅ GOOD - Using constants
historyMigrator.setRequestedEntityTypes(List.of(TYPE.HISTORY_PROCESS_INSTANCE));

// ✅ GOOD - White-box test for infrastructure
@WhiteBox
@Test
void shouldSaveSkipReasonToDatabase() {
    // Can access IdKeyMapper for testing persistence layer
}
```

---

### TR-2: No Direct Access to Camunda BPM Engine Implementation

**Rule:** Test classes must not access `org.camunda.bpm.engine.impl` package classes, except `ClockUtil`.

**Rationale:** Using internal Camunda BPM engine classes couples tests to implementation details. Only `ClockUtil` is permitted for time manipulation in tests.

**Exceptions:**
- `org.camunda.bpm.engine.impl.util.ClockUtil` - allowed for time manipulation
- Classes or methods annotated with `@WhiteBox` are exempt

**Enforcement:** `ArchitectureTest.shouldNotAccessCamundaBpmEngineImplClasses()`

**Examples:**
```java
// ✅ GOOD - Using ClockUtil for time manipulation
ClockUtil.setCurrentTime(new Date());

// ❌ BAD - Using other impl classes
EnsureUtil.ensureTrue("message", condition);
```

---

### TR-3: Test Method Naming Convention

**Rule:** Test methods annotated with `@Test` must start with "should" prefix.

**Rationale:** The "should" prefix creates behavior-driven test names that clearly express expected behavior and improve test readability.

**Enforcement:** `ArchitectureTest.shouldFollowNamingConventionForTestMethods()`

**Examples:**
```java
// ✅ GOOD
@Test
void shouldSkipProcessInstanceWhenDefinitionMissing() { ... }

@Test
void shouldMigrateUserTaskWithVariables() { ... }

// ❌ BAD
@Test
void testProcessInstance() { ... }

@Test
void migrateUserTask() { ... }
```

---

### TR-4: Tests Must Extend Appropriate Abstract Test Class

**Rule:** Concrete test classes in the `..qa..` package must extend an appropriate abstract test class.

**Rationale:** Extending abstract test classes ensures proper test setup, dependency injection, and access to required services.

**Allowed Base Classes:**
- `io.camunda.migration.date.AbstractMigratorTest`
- `history.io.camunda.migration.date.HistoryMigrationAbstractTest`
- `runtime.io.camunda.migration.date.RuntimeMigrationAbstractTest`
- Any class ending with `AbstractTest`

**Enforcement:** -

---

### TR-5: Test Classes Must End with "Test" Suffix

**Rule:** Classes in the `..qa..` package that contain methods annotated with `@Test` must have a simple name ending with "Test".

**Rationale:** Following the standard JUnit naming convention ensures test classes are recognized by test runners and build tools.

**Enforcement:** `ArchitectureTest.shouldHaveTestSuffixForTestClasses()`

---

## ✅ DO: Use Observable Outputs

### 1. Log Assertions

Use `LogCapturer` to verify that the correct behavior occurred:

```java
@RegisterExtension
protected LogCapturer logs = LogCapturer.create()
    .captureForType(RuntimeMigrator.class);

@Test
void shouldSkipProcessInstanceWhenDefinitionMissing() {
    // given
    deployer.deployCamunda7Process("process.bpmn");
    var instance = runtimeService.startProcessInstanceByKey("processId");
    
    // when
    runtimeMigrator.start();
    
    // then: verify via logs
    logs.assertContains(formatMessage(
        SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR,
        instance.getId(),
        "No C8 deployment found"));
    assertThatProcessInstanceCountIsEqualTo(0);
}
```

**Best Practices:**
- Always use log constants from `*Logs` classes (never string literals)
- Use the `formatMessage()` helper to substitute placeholders
- Verify both that the skip occurred AND that no instance was migrated

### 2. C8 API Queries

Verify migration results by querying the C8 API:

```java
@Test
void shouldMigrateProcessInstanceSuccessfully() {
    // given
    deployer.deployProcessInC7AndC8("process.bpmn");
    var c7Instance = runtimeService.startProcessInstanceByKey("processId");
    
    // when
    runtimeMigrator.start();
    
    // then: verify via C8 API
    List<ProcessInstance> instances = camundaClient
        .newProcessInstanceSearchRequest()
        .execute()
        .items();
    assertThat(instances).hasSize(1);
    assertThat(instances.get(0).getProcessDefinitionId()).isEqualTo("processId");
}
```

**Available Query Methods:**
- `camundaClient.new*earchRequest()`
- `rdbmsService.get*Reader().search(...)`
- Helper methods in abstract test classes (e.g., `searchHistoricProcessInstances()`)

### 3. Real-World Skip Scenarios

Create realistic scenarios where migration skips:

```java
@Test
void shouldSkipUserTaskWhenProcessInstanceNotMigrated() {
    // given: user task without its parent process instance
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    var c7Instance = runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    
    // when: migrate user tasks WITHOUT migrating process instances first
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateUserTasks(); // Real-World skip - no parent
    
    // then: verify skip via logs
    logs.assertContains(formatMessage(
        SKIPPING_USER_TASK_MISSING_PROCESS,
        task.getId()));
}
```

**Common Real-World Skip Scenarios:**
- Missing parent entities (migrate children without parents)
- Missing C8 deployment (C7 process without C8 equivalent)
- Missing BPMN elements (C7 element not in C8 model)
- Validation errors (multi-instance, unsupported features)

---

## ❌ DON'T: Use Internal Implementation Details

### Don't Access DbClient

```java
// ❌ BAD - White-box testing
List<IdKeyDbModel> skipped = dbClient.findSkippedProcessInstances();
assertThat(skipped).hasSize(1);
assertThat(skipped.get(0).getC7Id()).isEqualTo(instanceId);
```

**Why this is bad:**
- Couples tests to database schema
- Tests internal state instead of observable behavior
- Makes refactoring of implementation difficult
- Doesn't reflect how users interact with the system

### Don't Query IdKeyMapper Directly

```java
// ❌ BAD - Accessing internal mapping
Long c8Key = idKeyMapper.findC8KeyByC7IdAndType(c7Id, TYPE.RUNTIME_PROCESS_INSTANCE);
assertThat(c8Key).isNotNull();
```

**Better approach:** Query C8 API to verify the instance exists in C8.

### Don't Manipulate Internal State

```java
// ❌ BAD - Artificially creating skip state
dbClient.insert(instanceId, null, TYPE.RUNTIME_PROCESS_INSTANCE);
```

**Better approach:** Create real-world skip scenarios by setting up realistic conditions.

### Don't Use String Literals in Log Assertions

```java
// ❌ BAD - String literal
logs.assertContains("Migration of historic process instance with C7 ID [" + id + "] skipped");

// ✅ GOOD - Using constant
logs.assertContains(formatMessage(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, id, reason));
```

**Why:** Using constants ensures consistency and makes refactoring safer.

---

## Test Structure

### Given-When-Then Pattern

All tests should follow the Given-When-Then structure:

```java
@Test
void shouldMigrateProcessWithUserTask() {
    // given: describe the initial state
    deployer.deployProcessInC7AndC8("userTaskProcess.bpmn");
    var c7Instance = runtimeService.startProcessInstanceByKey("processId");
    var task = taskService.createTaskQuery().singleResult();
    
    // when: perform the action being tested
    runtimeMigrator.start();
    
    // then: verify the expected outcome
    var c8Instances = searchProcessInstances();
    assertThat(c8Instances).hasSize(1);
    logs.assertContains(formatMessage(MIGRATED_PROCESS_INSTANCE, c7Instance.getId()));
}
```

### Test Naming

Test methods must start with "should" followed by the expected behavior:

```java
// ✅ GOOD
void shouldSkipProcessInstanceWhenDefinitionMissing()
void shouldMigrateUserTaskWithVariables()
void shouldRetryFailedProcessInstanceMigration()

// ❌ BAD
void testProcessInstance()
void migrateUserTask()
void processInstanceMigration()
```

---

## Examples by Test Type

### Runtime Migration Tests

**Base class:** `RuntimeMigrationAbstractTest`

**Focus:** Migrating active process instances with their execution state

```java
public class ProcessInstanceMigrationTest extends RuntimeMigrationAbstractTest {
    
    @RegisterExtension
    protected LogCapturer logs = LogCapturer.create()
        .captureForType(RuntimeMigrator.class);
    
    @Test
    void shouldMigrateActiveProcessInstance() {
        // given
        deployer.deployProcessInC7AndC8("simpleProcess.bpmn");
        var c7Instance = runtimeService.startProcessInstanceByKey("simpleProcess");
        
        // when
        runtimeMigrator.start();
        
        // then: verify via C8 API
        assertThatProcessInstanceCountIsEqualTo(1);
        var c8Instances = searchProcessInstances();
        assertThat(c8Instances.get(0).getProcessDefinitionId()).isEqualTo("simpleProcess");
        
        // and: verify via logs
        logs.assertContains(formatMessage(MIGRATED_PROCESS_INSTANCE, c7Instance.getId()));
    }
}
```

### History Migration Tests

**Base class:** `HistoryMigrationAbstractTest`

**Focus:** Migrating completed process instances and their history

```java
public class HistoryProcessInstanceTest extends HistoryMigrationAbstractTest {
    
    @RegisterExtension
    protected LogCapturer logs = LogCapturer.create()
        .captureForType(HistoryMigrator.class);
    
    @Test
    void shouldMigrateCompletedProcessInstance() {
        // given
        deployer.deployProcessInC7AndC8("process.bpmn");
        var c7Instance = runtimeService.startProcessInstanceByKey("processId");
        completeProcessInstance(c7Instance.getId());
        
        // when
        historyMigrator.start();
        
        // then
        var c8History = searchHistoricProcessInstances();
        assertThat(c8History).hasSize(1);
        assertThat(c8History.get(0).getProcessDefinitionId()).isEqualTo("processId");
    }
}
```

### Skip/Retry Tests

**Focus:** Verifying skip behavior and retry mechanisms

```java
@Test
void shouldSkipAndThenRetryAfterFix() {
    // given: create skip scenario
    deployer.deployCamunda7Process("process.bpmn"); // No C8 deployment
    var c7Instance = runtimeService.startProcessInstanceByKey("processId");
    
    // when: first migration attempt
    runtimeMigrator.start();
    
    // then: verify skip
    logs.assertContains(formatMessage(
        SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR,
        c7Instance.getId(),
        "No C8 deployment found"));
    assertThatProcessInstanceCountIsEqualTo(0);
    
    // given: fix the issue
    deployer.deployCamunda8Process("process.bpmn");
    logs.reset();
    
    // when: retry migration
    runtimeMigrator.start();
    
    // then: verify success
    assertThatProcessInstanceCountIsEqualTo(1);
    logs.assertContains(formatMessage(MIGRATED_PROCESS_INSTANCE, c7Instance.getId()));
}
```

---

## White-Box Testing Exception

For tests that **must** access internal implementation (e.g., testing database schema, persistence layer), use the `@WhiteBox` annotation:

```java
// Class-level annotation - all methods exempt
@WhiteBox
public class SaveSkipReasonTest extends RuntimeMigrationAbstractTest {
    
    @Autowired
    protected IdKeyMapper idKeyMapper; // Allowed in @WhiteBox tests
    
    @Test
    void shouldSaveSkipReasonToDatabase() {
        // Can access internal components
        var skipped = idKeyMapper.findSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE, 0, 100);
        assertThat(skipped).hasSize(1);
    }
}

// Method-level annotation - only specific method exempt
public class MigrationTest extends RuntimeMigrationAbstractTest {
    
    @Test
    void shouldFollowBlackBoxRules() {
        // Must use logs and C8 API
    }
    
    @Test
    @WhiteBox
    void shouldTestInternalMapping() {
        // This specific test can access impl classes
    }
}
```

**When to use `@WhiteBox`:**
- Testing database schema operations
- Testing persistence layer behavior
- Testing internal component integration

**Important:** Use sparingly. Most tests should follow black-box principles.

---

## Helper Methods

### AbstractMigratorTest

Base class for all migrator tests, provides:

- `deployer` - Deploy BPMN processes to C7 and/or C8
- `runtimeService`, `historyService`, `taskService` - C7 engine services
- `camundaClient` - C8 API client
- `formatMessage(template, args...)` - Format log messages with placeholders

### RuntimeMigrationAbstractTest

Extends `AbstractMigratorTest`, adds:

- `runtimeMigrator` - Runtime migration component
- `assertThatProcessInstanceCountIsEqualTo(count)` - Verify C8 instance count
- `searchProcessInstances()` - Query C8 process instances
- Automatic cleanup after each test

### HistoryMigrationAbstractTest

Extends `AbstractMigratorTest`, adds:

- `historyMigrator` - History migration component
- `searchHistoricProcessInstances()` - Query C8 history
- `completeProcessInstance(id)` - Complete a C7 process instance
- Various entity-specific search methods

---

## Common Pitfalls

### ❌ Counting Skips via Database
```java
// BAD
long skipCount = dbClient.countSkippedByType(TYPE.RUNTIME_PROCESS_INSTANCE);
```

**Fix:** Count log events instead:
```java
// GOOD
long skipCount = logs.getEvents().stream()
    .filter(event -> event.getMessage().contains("skipped"))
    .count();
```

### ❌ Verifying Mapping via Database
```java
// BAD
boolean hasMapp ing = dbClient.checkHasC8KeyByC7IdAndType(c7Id, type);
```

**Fix:** Query C8 API to verify entity exists:
```java
// GOOD
var c8Instances = searchProcessInstances();
assertThat(c8Instances).hasSize(1);
```

### ❌ Artificial Skip Scenarios
```java
// BAD - manually inserting skip record
dbClient.insert(c7Id, null, TYPE.RUNTIME_PROCESS_INSTANCE);
```

**Fix:** Create real-world skip conditions:
```java
// GOOD - real-world skip from missing deployment
deployer.deployCamunda7Process("process.bpmn"); // No C8 deployment
runtimeService.startProcessInstanceByKey("processId");
runtimeMigrator.start(); // Will skip
```

---

## Architecture Test Enforcement

The `ArchitectureTest` class automatically enforces these guidelines:

- **Fails** if tests access `DbClient` (compilation error - it's package-private)
- **Fails** if tests access `IdKeyMapper` (except in abstract classes or `@WhiteBox` tests)
- **Fails** if tests access `..impl..` package classes (except logging constants, or `@WhiteBox` tests)
- **Fails** if tests don't follow naming conventions
- **Fails** if tests don't extend proper base class

Run architecture tests:
```bash
mvn test -Dtest=ArchitectureTest -pl qa
```

---

## Migration Guide

If you find existing white-box tests that need refactoring:

### Step 1: Identify the Behavior
What is this test actually verifying? Focus on the observable outcome, not the internal state.

### Step 2: Find Observable Outputs
- What logs does the migrator produce?
- What changes in C8 can be queried?
- What user-visible behavior occurs?

### Step 3: Create Real-World Scenarios
Instead of manipulating internal state, set up realistic conditions that produce the behavior.

### Step 4: Rewrite Assertions
Replace database queries with log assertions and C8 API queries.

### Step 5: Remove Internal Dependencies
Remove imports of `DbClient`, `IdKeyMapper`, and other `..impl..` classes.

---

## Getting Help

- **Architecture Rules:** See [ARCHITECTURE_RULES.md](ARCHITECTURE_RULES.md) for enforced constraints
- **Code Review:** See [CODE_REVIEW_CHECKLIST.md](CODE_REVIEW_CHECKLIST.md) for review guidelines
- **Questions:** Ask in team channels or create a discussion issue

---

## Summary of Testing Rules

| Rule ID | Description | Enforcement Method |
|---------|-------------|-------------------|
| TR-1 | No access to impl package | `shouldNotAccessImplClasses()` |
| TR-2 | No Camunda BPM impl access (except ClockUtil) | `shouldNotAccessCamundaBpmEngineImplClasses()` |
| TR-3 | Test methods start with "should" | `shouldFollowNamingConventionForTestMethods()` |
| TR-4 | Tests extend abstract test class | `testsShouldExtendAbstractTestClass()` |
| TR-5 | Test classes end with "Test" suffix | `shouldHaveTestSuffixForTestClasses()` |

For non-testing architecture rules (visibility, package organization, logging), see [ARCHITECTURE_RULES.md](ARCHITECTURE_RULES.md).

---

## Summary Checklist

Before submitting a test PR, verify:

- [ ] Tests use `LogCapturer` for behavioral verification
- [ ] Tests query C8 API to verify migration results
- [ ] Tests create real-world skip scenarios
- [ ] Tests use log constants (no string literals)
- [ ] Tests follow Given-When-Then structure
- [ ] Test methods start with "should"
- [ ] Tests extend appropriate abstract base class
- [ ] No imports of `DbClient`, `IdKeyMapper`, or other `..impl..` classes (unless `@WhiteBox`)
- [ ] `ArchitectureTest` passes locally

