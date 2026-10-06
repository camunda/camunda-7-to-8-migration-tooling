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
The Step 2 source snapshot preserves the C7 files before test migration. When Step 2 used a Git
repository, keep its `.git` metadata in the snapshot so the validator can verify the recorded commit.

1. Change only `test_run_mode` from `migrate_only` to `run` in
   `.camunda-migration/validation/step2-inventory.json`. Do not run `init`.
2. Use the baseline preserved before Step 3:

   | Step 2 source | Baseline source |
   |---|---|
   | Clean Git working tree | A worktree at the recorded commit. |
   | Dirty Git working tree or non-Git source | The saved filesystem snapshot. |

3. Record `docker_info` before the first Docker-dependent suite.
4. Record each C7 baseline with kind `c7_baseline` and `--baseline-root ../c7-source-baseline/`.
   Use each exact Step 2 suite command: `mvn -pl engine-tests,engine-tests-legacy,spring-boot-app test`
   and `mvn -pl remote-engine verify`.
5. Confirm that `test-mapping.json` and its approved test and mock changes are complete.
6. Record `test_freeze` after the migrated test sources and resources are final.
7. Start Docker. Record `test_repeat` for each suite with mapped or added CPT tests, using its
   exact Step 2 command: `mvn -pl engine-tests test`, `mvn -pl spring-boot-app test`, or
   `mvn -pl remote-engine verify`. Do not use `tests` for these mapped suites.
8. Record `assertion_strength` for each migrated test class and `mock_boundary` for each migrated
   C7 test.
9. Record the computed `test_parity` and `coverage_parity` checks after the repeat checks pass.
10. Record each `process_path` check.
11. Run the validator `report` action.

## Readiness

**Verdict:** `needs review`

**Gate:** `NOT READY`

E9 and E10 remain manual retirements and need the approvals recorded in the
full-migration walkthrough. R2 remains manual because it uses a shared engine.
