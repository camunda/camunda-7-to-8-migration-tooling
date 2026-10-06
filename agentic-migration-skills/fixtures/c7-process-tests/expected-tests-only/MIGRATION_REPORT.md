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

## Test verification

**Status:** `not verified (Migrate tests only)`

## Test-source compilation

| Scope | Command | Result |
|---|---|---|
| Migrated test modules | `mvn -pl engine-tests test-compile`<br>`mvn -pl spring-boot-app test-compile`<br>`mvn -pl remote-engine test-compile` | passed |

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
| `tests` | C7 `engine-tests`, `engine-tests-legacy`, `spring-boot-app`, and `remote-engine`; C8 `engine-tests`, `spring-boot-app`, and `remote-engine` | blocked | declined by user (Question 8) |
| `process_path` | `order`, `shipping`, `legacyOrder`, `fulfillment`, `MessageStartReview`, `MixedFinishReview`, `subscription`, `housekeeping`, and `payment` | blocked | declined by user (Question 8) |

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

Baseline filesystem snapshot: `../c7-source-baseline/`

The C7 baseline is a sibling filesystem snapshot.

Before recording either run, change only `test_run_mode` from `migrate_only` to `run` in
`.camunda-migration/validation/step2-inventory.json`. Keep the scope, run ID, and source snapshot.
Do not run `init`, because it clears earlier validation checks.

When a reserved suite entry requires Docker, record the probe before running that suite:

```sh
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type project --target . --kind docker_info -- docker info
```

Record each C7 suite from the sibling snapshot and each C8 suite from the migrated project. The
target consolidates the legacy Scenario classes into `engine-tests`. Record each run with a separate
suite name:

```sh
# engine-tests unit suite: C7 baseline and C8 migrated
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target engine-tests --kind tests --scenario unit-c7-baseline -- mvn -f ../c7-source-baseline/pom.xml -pl engine-tests test
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target engine-tests --kind tests --scenario unit-c8-migrated -- mvn -pl engine-tests test

# engine-tests-legacy Scenario suite: C7 baseline and consolidated C8 migrated suite
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target engine-tests --kind tests --scenario legacy-scenario-c7-baseline -- mvn -f ../c7-source-baseline/pom.xml -pl engine-tests-legacy test
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target engine-tests --kind tests --scenario legacy-scenario-c8-migrated -- mvn -pl engine-tests -Dtest=FulfillmentScenarioTest,ScenarioMappingEdgeCasesTest test

# Spring Boot suite: C7 baseline and C8 migrated
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target spring-boot-app --kind tests --scenario spring-boot-c7-baseline -- mvn -f ../c7-source-baseline/pom.xml -pl spring-boot-app test
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target spring-boot-app --kind tests --scenario spring-boot-c8-migrated -- mvn -pl spring-boot-app test

# Remote-engine suite: C7 baseline and C8 migrated. These commands run the Failsafe suite.
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target remote-engine --kind tests --scenario remote-engine-c7-baseline -- mvn -f ../c7-source-baseline/pom.xml -pl remote-engine verify
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type module --target remote-engine --kind tests --scenario remote-engine-c8-migrated -- mvn -pl remote-engine verify
```

Rerun and record every deferred Step 4 process scenario from the migrated project. `shipping` is
non-standalone, so its scenario key names the covering `OrderProcessTest` test:

```sh
# order: normal path
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type process --target 'engine-tests/src/main/resources/converted-c8-order.bpmn#order' --kind process_path --scenario normal --environment local -- mvn -pl engine-tests '-Dtest=OrderProcessTest#approvesAndShipsOrder' test

# shipping: covered by the order process test
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type process --target 'engine-tests/src/main/resources/converted-c8-shipping.bpmn#shipping' --kind process_path --scenario 'engine-tests:com.camunda.fixture.order.OrderProcessTest#approvesAndShipsOrder' --environment local -- mvn -pl engine-tests '-Dtest=OrderProcessTest#approvesAndShipsOrder' test

# legacyOrder: stock-missing path
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type process --target 'engine-tests/src/main/resources/converted-c8-LegacyOrderTest.testStockMissing.bpmn#legacyOrder' --kind process_path --scenario testStockMissing --environment local -- mvn -pl engine-tests '-Dtest=LegacyOrderTest#testStockMissing' test

# fulfillment: two daily reminders
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type process --target 'engine-tests/src/main/resources/converted-c8-fulfillment.bpmn#fulfillment' --kind process_path --scenario normal --environment local -- mvn -pl engine-tests '-Dtest=FulfillmentScenarioTest#shouldCompleteWorkAfterTwoDailyReminders' test

# MessageStartReview: message-start path
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type process --target 'engine-tests/src/main/resources/converted-c8-review-edge-cases.bpmn#MessageStartReview' --kind process_path --scenario shouldStartMessageProcess --environment local -- mvn -pl engine-tests '-Dtest=ScenarioMappingEdgeCasesTest#shouldStartMessageProcess' test

# MixedFinishReview: mixed completed and terminated visits
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type process --target 'engine-tests/src/main/resources/converted-c8-review-edge-cases.bpmn#MixedFinishReview' --kind process_path --scenario shouldCountMixedFinishedVisitsByOutcome --environment local -- mvn -pl engine-tests '-Dtest=ScenarioMappingEdgeCasesTest#shouldCountMixedFinishedVisitsByOutcome' test

# subscription: normal path
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type process --target 'spring-boot-app/src/main/resources/converted-c8-subscription.bpmn#subscription' --kind process_path --scenario normal --environment local -- mvn -pl spring-boot-app '-Dtest=SubscriptionProcessTest#activatesSubscription' test

# housekeeping: startup path
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type process --target 'spring-boot-app/src/main/resources/converted-c8-housekeeping.bpmn#housekeeping' --kind process_path --scenario normal --environment local -- mvn -pl spring-boot-app '-Dtest=HousekeepingStartupTest#startsHousekeepingOnDeployment' test

# payment: remote-engine integration path
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . run --type process --target 'remote-engine/src/main/resources/converted-c8-payment.bpmn#payment' --kind process_path --scenario normal --environment local -- mvn -pl remote-engine -Dit.test=PaymentWorkerIT verify

# Regenerate the gate after all deferred checks
python3 "<skill-directory>/scripts/validate_migration_evidence.py" --project-root . report
```

## Readiness

**Verdict:** `needs review`

**Gate:** `NOT READY`

E9 and E10 remain manual retirements and need the approvals recorded in the
full-migration walkthrough. R2 remains manual because it uses a shared engine.
