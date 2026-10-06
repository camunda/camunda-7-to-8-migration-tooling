# Migration Report — Migrate tests only

## User choices

| Question | Answer |
|---|---|
| Scope | Code + models |
| Target | Camunda 8.9 |
| Question 8 | Migrate tests only |

## Test execution decision

The skill ran `docker info` before Question 8. The command failed because Docker
was stopped. The skill still offered both **Run tests** and **Migrate tests
only**. The user chose **Migrate tests only**.

The migrated test sources compile with the command below. The skill ran no C7 or C8 test command
during migration. Each test check is blocked because the user declined test execution.

## Test-source compilation

| Scope | Command | Result |
|---|---|---|
| Migrated test modules | `mvn -pl engine-tests,spring-boot-app,remote-engine -am test-compile` | passed |

## Test checks

| Check | Status | Reason |
|---|---|---|
| `test_baseline` | blocked | declined by user (Question 8) |
| `test_parity` | blocked | declined by user (Question 8) |
| `test_freeze` | blocked | declined by user (Question 8) |
| `mock_boundary` | blocked | declined by user (Question 8) |
| `process_coverage` | blocked | declined by user (Question 8) |
| `test_execution` | blocked | declined by user (Question 8) |

## Deferred validation checks

| Kind | Scope | Status | Reason |
|---|---|---|---|
| `tests` | Every declared module suite | blocked | declined by user (Question 8) |
| `process_path` | Every Step 4 process scenario | blocked | declined by user (Question 8) |

## Test parity

The inventory and mapped CPT tests are present. Runtime parity remains
unverified because the user selected **Migrate tests only**.

| Test ID | Expected CPT test | Status | Reason |
|---|---|---|---|
| `engine-tests:com.camunda.fixture.order.OrderProcessTest#approvesAndShipsOrder` | `OrderProcessTest#approvesAndShipsOrder` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.OrderTimerTest#escalatesAfterOneDay` | `OrderTimerTest#escalatesAfterOneDay` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.LegacyOrderTest#testStockMissing` | `LegacyOrderTest#testStockMissing` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.OrderMockitoTest#registersDelegateOutputAndVerifiesItsInvocation` | `OrderMockitoTest#registersDelegateOutputAndVerifiesItsInvocation` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.OrderAutoMockTest#autoMocksDelegatesAndTracksCoverage` | `OrderAutoMockTest#autoMocksDelegatesAndTracksCoverage` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#evaluatesEveryOutputColumn` | `DiscountDecisionTest#evaluatesEveryOutputColumn` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#collectsPromotionRules` | `PromotionsDecisionTest#collectsPromotionRules` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.FulfillmentScenarioTest#shouldCompleteWorkAfterTwoDailyReminders` | `FulfillmentScenarioTest#shouldCompleteWorkAfterTwoDailyReminders` | blocked | declined by user (Question 8) |
| `engine-tests-legacy:com.camunda.fixture.order.FulfillmentScenarioTest#shouldCompleteWorkAfterTwoDailyReminders` | `FulfillmentScenarioTest#shouldCompleteWorkAfterTwoDailyReminders` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldStartMessageProcess` | `ScenarioMappingEdgeCasesTest#shouldStartMessageProcess` | blocked | declined by user (Question 8) |
| `engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldStartMessageProcess` | `ScenarioMappingEdgeCasesTest#shouldStartMessageProcess` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountCompletedVisitsSeparately` | `ScenarioMappingEdgeCasesTest#shouldCountCompletedVisitsSeparately` | blocked | declined by user (Question 8) |
| `engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountCompletedVisitsSeparately` | `ScenarioMappingEdgeCasesTest#shouldCountCompletedVisitsSeparately` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountMixedFinishedVisitsByOutcome` | `ScenarioMappingEdgeCasesTest#shouldCountMixedFinishedVisitsByOutcome` | blocked | declined by user (Question 8) |
| `engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountMixedFinishedVisitsByOutcome` | `ScenarioMappingEdgeCasesTest#shouldCountMixedFinishedVisitsByOutcome` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionProcessTest#activatesSubscription` | `SubscriptionProcessTest#activatesSubscription` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionEndpointTest#startsSubscriptionFromHttp` | `SubscriptionEndpointTest#startsSubscriptionFromHttp` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.ActivateDelegateMockTest#mocksDelegateBean` | `ActivateDelegateMockTest#mocksDelegateBean` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.HousekeepingStartupTest#startsHousekeepingOnDeployment` | `HousekeepingStartupTest#startsHousekeepingOnDeployment` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionStandaloneTest#startsSubscriptionWithoutSpring` | `SubscriptionStandaloneTest#startsSubscriptionWithoutSpring` | blocked | declined by user (Question 8) |
| `remote-engine:com.camunda.fixture.payment.PaymentWorkerIT#chargesPaymentThroughEngineRest` | `PaymentWorkerIT#chargesPayment` | blocked | declined by user (Question 8) |

## Verify the test migration

Use the C7 baseline commit recorded during Step 2. Create a separate baseline worktree:

```sh
git worktree add ../c7-baseline <baseline-commit>
```

Before recording either run, change only `test_run_mode` from `migrate_only` to `run` in
`.camunda-migration/validation/step2-inventory.json`. Keep the scope, run ID, and source snapshot.
Do not run `init`, because it clears earlier validation checks.

When a reserved suite entry requires Docker, record the probe before running that suite:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind docker_info -- docker info
```

Record each C7 suite from the baseline worktree and each C8 suite from the migrated project. Use
separate evidence keys such as `unit-c7-baseline` and `unit-c8-migrated`. For example:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target engine-tests --kind tests --scenario unit-c7-baseline -- mvn -f ../c7-baseline/pom.xml -pl engine-tests test
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target engine-tests --kind tests --scenario unit-c8-migrated -- mvn -pl engine-tests test
```

Rerun and record every deferred Step 4 process scenario with its existing `process_path` key and
recorded command.

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . report
```

## Readiness

**Verdict:** `needs review`

**Gate:** `NOT READY`

E9 and E10 remain manual retirements and need the approvals recorded in the
full-migration walkthrough. R2 remains manual because it uses a shared engine.
