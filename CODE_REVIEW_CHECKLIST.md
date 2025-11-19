# Code Review Checklist for Test Code

When reviewing pull requests that add or modify tests, use this checklist to ensure tests follow our black-box testing principles.

## ✅ Black-Box Testing Compliance

### Test Structure
- [ ] Test names clearly describe the behavior being tested (not implementation details)
- [ ] Tests are organized by behavior/feature, not by internal class structure
- [ ] Test setup creates realistic scenarios (not artificial internal state)

### Observable Outputs Only
- [ ] Tests verify behavior through logs (`LogCapturer`)
- [ ] Tests verify behavior through C8 API queries (`camundaClient.*`)
- [ ] Tests verify behavior through C7 API queries when appropriate
- [ ] No assertions on internal database state (DbClient, IdKeyMapper)

### Natural Skip Scenarios
- [ ] Skip scenarios are created by natural dependencies (e.g., missing parent entities)
- [ ] No direct manipulation of skip state via `dbClient.insert()`
- [ ] Tests migrate entities in realistic order to trigger skips

### Forbidden Patterns
- [ ] ❌ No `dbClient.findSkippedProcessInstances()` usage
- [ ] ❌ No `dbClient.countSkipped()` usage
- [ ] ❌ No `dbClient.findC8KeyByC7IdAndType()` usage
- [ ] ❌ No `dbClient.checkHasC8KeyByC7IdAndType()` usage
- [ ] ❌ No `idKeyMapper.findSkippedByType()` usage (except in abstract test classes)
- [ ] ❌ No direct access to classes in `..impl..` package (except logging constants)

### Allowed Patterns
- [ ] ✅ `logs.assertContains()` for verifying skip reasons
- [ ] ✅ `logs.getEvents().stream().filter(...).count()` for counting occurrences
- [ ] ✅ `camundaClient.newProcessInstanceSearchRequest()` for verifying migrations
- [ ] ✅ Natural entity relationships (deploy parent, migrate child)
- [ ] ✅ Logging constants from `RuntimeMigratorLogs`, `HistoryMigratorLogs`

## 🏗️ Test Quality

### Readability
- [ ] Test follows Given-When-Then structure
- [ ] Comments explain *why*, not *what*
- [ ] Variable names are descriptive
- [ ] No magic numbers or strings (use constants)

### Maintainability
- [ ] Test is focused on a single behavior
- [ ] Test doesn't depend on execution order
- [ ] Test cleans up after itself (or relies on base class cleanup)
- [ ] No hardcoded environment-specific values

### Correctness
- [ ] Test actually fails if the behavior is broken (verify by temporarily breaking code)
- [ ] Assertions are precise (not overly broad or specific)
- [ ] No flaky behavior (timing issues, random data)
- [ ] Proper use of `await()` for async operations

## 📦 Test Categories

### Runtime Migration Tests (`..runtime..`)
- [ ] Uses `RuntimeMigrationAbstractTest` base class
- [ ] Verifies C8 process instances via `camundaClient`
- [ ] Uses `LogCapturer` for RuntimeMigrator logs
- [ ] Does NOT access DbClient

### History Migration Tests (`..history..`)
- [ ] Uses `HistoryMigrationAbstractTest` base class
- [ ] Verifies C8 history via search helpers
- [ ] Uses `LogCapturer` for HistoryMigrator logs
- [ ] Does NOT access DbClient

### Persistence Tests (`..persistence..`)
- [ ] Legitimately tests infrastructure/schema behavior
- [ ] Isolated in `persistence` package
- [ ] May access DbClient/IdKeyMapper if necessary for infrastructure testing
- [ ] Clearly documents why internal access is needed

## 🔍 Review Examples

### ✅ Good Example
```java
@Test
void shouldSkipInstanceWhenDefinitionMissing() {
    // given: no C8 deployment
    deployer.deployCamunda7Process("process.bpmn");
    var instance = runtimeService.startProcessInstanceByKey("processId");
    
    // when
    runtimeMigrator.start();
    
    // then: verify skip via observable output
    logs.assertContains(String.format(
        SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR,
        instance.getId(),
        "No C8 deployment found"));
    assertThatProcessInstanceCountIsEqualTo(0);
}
```

### ❌ Bad Example
```java
@Test
void shouldSkipInstanceWhenDefinitionMissing() {
    // given
    deployer.deployCamunda7Process("process.bpmn");
    var instance = runtimeService.startProcessInstanceByKey("processId");
    
    // when
    runtimeMigrator.start();
    
    // then: ❌ WHITE-BOX - accessing internal state
    var skipped = dbClient.findSkippedProcessInstances();
    assertThat(skipped).hasSize(1);
    assertThat(skipped.get(0).getC7Id()).isEqualTo(instance.getId());
}
```

## 🚨 Red Flags

Immediate rejection if PR contains:
- [ ] 🚫 New imports of `DbClient` in test classes (outside persistence package)
- [ ] 🚫 New calls to `dbClient.*` methods in tests
- [ ] 🚫 Manually inserting skip records via `dbClient.insert()`
- [ ] 🚫 Architecture tests commented out or disabled
- [ ] 🚫 Tests in wrong package to bypass architecture rules

## 📚 Resources

- [TESTING_GUIDELINES.md](TESTING_GUIDELINES.md) - Comprehensive testing guide
- [MIGRATION_TEST_REFACTORING.md](MIGRATION_TEST_REFACTORING.md) - Examples of migrated tests
- `ArchitectureTest.java` - Automated enforcement of these rules

## 💬 Reviewer Comments Templates

### When tests access internal state:
```
❌ This test accesses internal implementation details via DbClient. 
Please refactor to use black-box testing:
1. Verify skip via logs: logs.assertContains(SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR, ...)
2. Verify migration success via C8 API: camundaClient.newProcessInstanceSearchRequest()
3. See TESTING_GUIDELINES.md for examples
```

### When tests manipulate internal state:
```
❌ This test artificially creates skip state via dbClient.insert(). 
Please create a natural skip scenario instead:
- Migrate child entities without migrating parent entities first
- Deploy C7 process without C8 deployment
- See TESTING_GUIDELINES.md section "Natural Skip Scenarios"
```

### When architecture tests fail:
```
❌ ArchitectureTest is failing, indicating violation of black-box testing principles.
Run `mvn test -Dtest=ArchitectureTest` locally to see specific violations.
The architecture tests enforce that tests only access public APIs and observable outputs.
```

