# Testing Guidelines for Camunda 7 to 8 Data Migrator

## Philosophy: Black-Box Testing

All tests in the `qa` module should follow a **black-box testing approach**. This means tests verify behavior through **observable outputs** rather than internal implementation details.

## 🔒 Visibility Guidelines

### No Private Modifiers (Except Utility Classes)

**Rule:** Methods, fields, and constructors should NOT be private. Use **protected** or **package-private** instead.

**Rationale:** 
- Improves testability without reflection
- Allows package-level testing when needed
- Enables extensibility through subclassing
- Forces clearer API design

**Visibility Levels:**
- ✅ **protected** - For members that subclasses might need
- ✅ **package-private** (no modifier) - For internal members that tests might need
- ✅ **public** - For the public API
- ❌ **private** - ONLY for utility class constructors

**Exception:** Utility classes (ending with Utils, Helper, Constants) may have private constructors.

```java
// ✅ GOOD - Protected method for subclasses
protected void validateProcessInstance(ProcessInstance pi) { ... }

// ✅ GOOD - Package-private for tests in same package
void migrateProcessInstances() { ... }

// ❌ BAD - Private method limits testability
private void migrateProcessInstances() { ... }

// ✅ GOOD - Private constructor for utility class
public class StringUtils {
    private StringUtils() {} // OK for utility classes
}
```

**Architecture Test:** The `ArchitectureTest` class enforces these rules automatically.

## ✅ DO: Use Observable Outputs

### 1. Log Assertions
Use `LogCapturer` to verify that the correct behavior occurred:

```java
@RegisterExtension
protected LogCapturer logs = LogCapturer.create().captureForType(RuntimeMigrator.class);

@Test
void shouldSkipProcessInstance() {
    // given: natural skip scenario
    deployer.deployCamunda7Process("process.bpmn"); // No C8 deployment
    var instance = runtimeService.startProcessInstanceByKey("processId");
    
    // when
    runtimeMigrator.start();
    
    // then: verify via logs
    logs.assertContains(String.format(
        SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, 
        instance.getId(), 
        "No C8 deployment found"));
    assertThatProcessInstanceCountIsEqualTo(0);
}
```

### 2. C8 API Queries
Verify migration results by querying the C8 API:

```java
@Test
void shouldMigrateProcessInstance() {
    // given
    deployer.deployProcessInC7AndC8("process.bpmn");
    runtimeService.startProcessInstanceByKey("processId");
    
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

### 3. Natural Skip Scenarios
Create realistic scenarios where migration naturally skips:

```java
@Test
void shouldSkipUserTaskWhenProcessInstanceNotMigrated() {
    // given: user task without its parent process instance
    deployer.deployCamunda7Process("userTaskProcess.bpmn");
    runtimeService.startProcessInstanceByKey("userTaskProcessId");
    var task = taskService.createTaskQuery().singleResult();
    taskService.complete(task.getId());
    
    // when: migrate user tasks WITHOUT migrating process instances first
    historyMigrator.migrateProcessDefinitions();
    historyMigrator.migrateUserTasks(); // Natural skip - no parent
    
    // then: verify via logs
    logs.assertContains("Migration of historic user task with C7 ID [" + 
        task.getId() + "] skipped. Process instance yet not available.");
}
```

## ❌ DON'T: Use Internal Implementation Details

### Don't Access DbClient
```java
// ❌ BAD - White-box testing
List<IdKeyDbModel> skipped = dbClient.findSkippedProcessInstances();
assertThat(skipped).hasSize(1);
assertThat(skipped.get(0).getC7Id()).isEqualTo(instanceId);
```

The `DbClient` class is **package-private** and will cause compilation errors if used in tests.

### Don't Query IdKeyMapper Directly
```java
// ❌ BAD - Accessing internal mapping
Long c8Key = dbClient.findC8KeyByC7IdAndType(c7Id, TYPE.RUNTIME_PROCESS_INSTANCE);
assertThat(c8Key).isNotNull();
```

Instead, verify through C8 API that the instance exists.

### Don't Manipulate Internal State
```java
// ❌ BAD - Artificially creating skip state
dbClient.insert(instanceId, null, TYPE.RUNTIME_PROCESS_INSTANCE);
```

Create natural skip scenarios instead.

## Architecture Tests

We use **ArchUnit** to automatically enforce these rules. The tests will fail if:

1. Any test in `..qa..` package accesses `DbClient`
2. Any test (except abstract base classes) accesses `IdKeyMapper`
3. Any test accesses classes in `..impl..` package (except logging constants)

Exception: Tests in `..qa.persistence..` package are infrastructure tests and may access internal classes.

## Examples by Test Type

### Runtime Migration Tests
- ✅ Use `camundaClient.newProcessInstanceSearchRequest()` to verify migration
- ✅ Use `LogCapturer` to verify skip reasons
- ❌ Don't use `dbClient.findSkippedProcessInstances()`

### History Migration Tests
- ✅ Use `searchHistoricProcessInstances()` helper method
- ✅ Use `LogCapturer` to verify skip reasons
- ❌ Don't use `dbClient.findC8KeyByC7IdAndType()`

### Skip/Retry Tests
- ✅ Create natural skip scenarios (missing parent entities)
- ✅ Use log event counting to verify retry behavior
- ❌ Don't use `dbClient.countSkipped()`

## Benefits of Black-Box Testing

1. **Reduced coupling**: Tests don't break when internal implementation changes
2. **Better design**: Forces you to think about observable behavior
3. **Realistic scenarios**: Tests use real-world skip conditions
4. **Maintainability**: Tests are easier to understand and maintain
5. **Encapsulation**: Internal implementation details remain private

## Migration Path

If you find existing white-box tests:

1. Identify what behavior they're testing
2. Determine the observable output for that behavior
3. Create natural skip scenarios instead of artificial ones
4. Rewrite assertions to use logs or C8 API queries
5. Remove DbClient/IdKeyMapper usage

See `MIGRATION_TEST_REFACTORING.md` for examples of migrated tests.

## Questions?

If you're unsure how to test something without accessing internal state:

1. Ask: "What observable output does this behavior produce?"
2. Consider: "How would a user detect this behavior?"
3. Remember: If it's not observable, maybe it doesn't need testing

For persistence/infrastructure tests that legitimately need internal access, place them in the `io.camunda.migrator.qa.persistence` package.

