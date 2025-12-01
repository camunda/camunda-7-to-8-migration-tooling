# Code Review Checklist

Use this checklist when reviewing pull requests that add or modify tests.

## ✅ Black-Box Testing Compliance

### Test Approach
- [ ] Tests verify behavior through **observable outputs** (logs, C8 API queries)
- [ ] Tests use **real-world skip scenarios** (missing parents, missing deployments)
- [ ] Tests follow **Given-When-Then** structure
- [ ] Test names clearly describe the **expected behavior** (start with "should")

### Observable Outputs
- [ ] ✅ Uses `LogCapturer` for verifying behavior
- [ ] ✅ Uses C8 API queries (`camundaClient.*SearchRequest()`)
- [ ] ✅ Uses log constants from `*Logs` classes (not string literals)
- [ ] ✅ Uses `formatMessage()` helper for log assertions

### Forbidden Patterns
- [ ] ❌ No `dbClient.*` method calls (compilation should fail)
- [ ] ❌ No `idKeyMapper.*` queries in concrete test classes
- [ ] ❌ No direct access to classes in `..impl..` package (except logging constants)
- [ ] ❌ No string literals in `logs.assertContains()` calls
- [ ] ❌ No artificial state manipulation

### White-Box Exception
- [ ] If `@WhiteBox` is used, is it justified? (persistence/infrastructure testing only)
- [ ] Is the test in the appropriate package? (`qa.persistence` for infrastructure tests)

---

## 🏗️ Test Quality

### Structure
- [ ] Test follows Given-When-Then pattern with clear comments
- [ ] Each section (given/when/then) is clearly separated
- [ ] Test focuses on a **single behavior**
- [ ] Test is **independent** (doesn't depend on execution order)

### Readability
- [ ] Variable names are descriptive
- [ ] Comments explain **why**, not **what**
- [ ] No magic numbers or strings (use constants)
- [ ] Code is concise and easy to understand

### Correctness
- [ ] Test actually **fails** if the behavior is broken (verify by temporarily breaking code)
- [ ] Assertions are **precise** (not overly broad or specific)
- [ ] Proper use of `await()` for async operations
- [ ] Test data is realistic

---

## 📦 Test Organization

### Base Classes
- [ ] Test extends appropriate base class:
  - `RuntimeMigrationAbstractTest` for runtime tests
  - `HistoryMigrationAbstractTest` for history tests
  - `AbstractMigratorTest` for general tests
- [ ] Test class name ends with `Test`

### Test Categories
Runtime migration tests verify C8 process instances:
- [ ] Uses `camundaClient` to verify migration
- [ ] Uses `assertThatProcessInstanceCountIsEqualTo()`
- [ ] Captures `RuntimeMigrator` logs

History migration tests verify C8 history:
- [ ] Uses `searchHistoricProcessInstances()` or similar helpers
- [ ] Captures `HistoryMigrator` logs
- [ ] Completes C7 instances before migration

Persistence/infrastructure tests:
- [ ] Located in `qa.persistence` package
- [ ] Annotated with `@WhiteBox` if accessing impl classes
- [ ] Clearly documents why internal access is needed

---

## 🚨 Red Flags

**Immediately question if PR contains:**

- [ ] 🚫 New imports of `DbClient` in test classes (should cause compilation error)
- [ ] 🚫 New imports of `IdKeyMapper` in non-abstract test classes
- [ ] 🚫 Calls to `dbClient.*` methods
- [ ] 🚫 String literals in `logs.assertContains()` instead of log constants
- [ ] 🚫 Manually inserting skip records via database manipulation
- [ ] 🚫 `@WhiteBox` annotation used without clear justification
- [ ] 🚫 Architecture tests commented out or disabled
- [ ] 🚫 Tests in wrong package to bypass architecture rules

---

## 📝 Review Examples

### ✅ Good Example: Black-Box Test

```java
@Test
void shouldSkipProcessInstanceWhenDefinitionMissing() {
    // given: no C8 deployment (real-world skip scenario)
    deployer.deployCamunda7Process("process.bpmn");
    var c7Instance = runtimeService.startProcessInstanceByKey("processId");
    
    // when
    runtimeMigrator.start();
    
    // then: verify skip via observable output (logs)
    logs.assertContains(formatMessage(
        SKIPPING_PROCESS_INSTANCE_VALIDATION_ERROR,
        c7Instance.getId(),
        "No C8 deployment found"));
    
    // and: verify no instance was migrated (C8 API query)
    assertThatProcessInstanceCountIsEqualTo(0);
}
```

**Why it's good:**
- ✅ Real-World skip scenario (missing C8 deployment)
- ✅ Uses log constants with `formatMessage()`
- ✅ Verifies via observable outputs (logs + C8 API)
- ✅ Clear Given-When-Then structure
- ✅ No internal implementation access

### ❌ Bad Example: White-Box Test

```java
@Test
void shouldSkipProcessInstanceWhenDefinitionMissing() {
    // given
    deployer.deployCamunda7Process("process.bpmn");
    var c7Instance = runtimeService.startProcessInstanceByKey("processId");
    
    // when
    runtimeMigrator.start();
    
    // then: ❌ WHITE-BOX - accessing internal database state
    var skipped = dbClient.findSkippedProcessInstances();
    assertThat(skipped).hasSize(1);
    assertThat(skipped.get(0).getC7Id()).isEqualTo(c7Instance.getId());
    assertThat(skipped.get(0).getSkipReason()).contains("No C8 deployment found");
}
```

**Why it's bad:**
- ❌ Accesses internal `DbClient`
- ❌ Queries internal database state
- ❌ Couples test to database schema
- ❌ Doesn't verify observable behavior
- ❌ Will fail `ArchitectureTest`

---

## 💬 Reviewer Comment Templates

### When tests access internal state:

```
❌ This test accesses internal implementation details via DbClient/IdKeyMapper.

Please refactor to use black-box testing:
1. Verify skip via logs: `logs.assertContains(formatMessage(SKIPPING_..., ...))`
2. Verify migration success via C8 API: `camundaClient.newProcessInstanceSearchRequest()`
3. Use log constants, not string literals

See: docs/TESTING_GUIDELINES.md for examples
```

### When tests use string literals in log assertions:

```
❌ This test uses string literals in log assertions instead of log constants.

Please use log constants from *Logs classes:
- Import the constant: `import static ...HistoryMigratorLogs.SKIPPING_PROCESS_INSTANCE_...`
- Use formatMessage(): `logs.assertContains(formatMessage(CONSTANT, arg1, arg2))`

This ensures consistency and makes refactoring safer.
```

### When tests manipulate internal state:

```
❌ This test artificially creates skip state via database manipulation.

Please create a real-world skip scenario instead:
- Migrate child entities without migrating parent entities first
- Deploy C7 process without C8 deployment
- Use realistic conditions that produce the behavior

See: docs/TESTING_GUIDELINES.md section "Real-World Skip Scenarios"
```

### When architecture tests fail:

```
❌ ArchitectureTest is failing, indicating violation of black-box testing principles.

Run `mvn test -Dtest=ArchitectureTest -pl qa` locally to see specific violations.

The architecture tests enforce that tests only access public APIs and observable outputs.
See: docs/ARCHITECTURE_RULES.md for details
```

### When @WhiteBox is used without justification:

```
⚠️ This test uses `@WhiteBox` annotation to bypass black-box testing rules.

Please justify why this test needs internal access:
- Is this testing database schema or persistence layer?
- Is this testing internal component integration?
- Can this be rewritten as a black-box test?

If justified, consider moving to `qa.persistence` package.
```

---

## ✅ Approval Checklist

Before approving a PR with test changes:

- [ ] All tests follow black-box principles (or have justified `@WhiteBox`)
- [ ] Tests use log constants, not string literals
- [ ] Tests create real-world skip scenarios
- [ ] Tests verify via observable outputs (logs + C8 API)
- [ ] Test names clearly describe expected behavior
- [ ] Tests follow Given-When-Then structure
- [ ] No unnecessary `@WhiteBox` annotations
- [ ] Architecture tests pass locally
- [ ] Code is well-organized and readable

---

## 📚 Related Documentation

- **[ARCHITECTURE_RULES.md](ARCHITECTURE_RULES.md)** - Automatically enforced architectural constraints
- **[TESTING_GUIDELINES.md](TESTING_GUIDELINES.md)** - Comprehensive testing guide with examples
- **ArchitectureTest.java** - Automated enforcement via ArchUnit

---

## 🎯 Quick Reference

### What Tests SHOULD Do:
✅ Use `LogCapturer` for behavioral verification  
✅ Query C8 API to verify migrations  
✅ Create real-world skip scenarios  
✅ Use log constants with `formatMessage()`  
✅ Follow Given-When-Then structure  
✅ Start method names with "should"  
✅ Extend appropriate abstract base class  

### What Tests SHOULD NOT Do:
❌ Access `DbClient` or `IdKeyMapper`  
❌ Query internal database state  
❌ Use string literals in log assertions  
❌ Manipulate internal state artificially  
❌ Access classes in `..impl..` package (except logging constants)  
❌ Use `@WhiteBox` without clear justification  

