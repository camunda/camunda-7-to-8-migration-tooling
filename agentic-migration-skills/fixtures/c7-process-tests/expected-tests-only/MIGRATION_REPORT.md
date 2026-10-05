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

The migrated test sources compile. The skill ran no C7 or C8 test command.
Each test check is blocked because the user declined test execution.

## Test checks

| Check | Status | Reason |
|---|---|---|
| `test_baseline` | blocked | declined by user (Question 8) |
| `test_parity` | blocked | declined by user (Question 8) |
| `test_freeze` | blocked | declined by user (Question 8) |
| `mock_boundary` | blocked | declined by user (Question 8) |
| `process_coverage` | blocked | declined by user (Question 8) |
| `test_execution` | blocked | declined by user (Question 8) |

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
| `engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldStartMessageProcess` | `ScenarioMappingEdgeCasesTest#shouldStartMessageProcess` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountCompletedVisitsSeparately` | `ScenarioMappingEdgeCasesTest#shouldCountCompletedVisitsSeparately` | blocked | declined by user (Question 8) |
| `engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountMixedFinishedVisitsByOutcome` | `ScenarioMappingEdgeCasesTest#shouldCountMixedFinishedVisitsByOutcome` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionProcessTest#activatesSubscription` | `SubscriptionProcessTest#activatesSubscription` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionEndpointTest#startsSubscriptionFromHttp` | `SubscriptionEndpointTest#startsSubscriptionFromHttp` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.ActivateDelegateMockTest#mocksDelegateBean` | `ActivateDelegateMockTest#mocksDelegateBean` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.HousekeepingStartupTest#startsHousekeepingOnDeployment` | `HousekeepingStartupTest#startsHousekeepingOnDeployment` | blocked | declined by user (Question 8) |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionStandaloneTest#startsSubscriptionWithoutSpring` | `SubscriptionStandaloneTest#startsSubscriptionWithoutSpring` | blocked | declined by user (Question 8) |
| `remote-engine:com.camunda.fixture.payment.PaymentWorkerIT#chargesPaymentThroughEngineRest` | `PaymentWorkerIT#chargesPayment` | blocked | declined by user (Question 8) |

## Readiness

**Verdict:** `needs review`

**Gate:** `NOT READY`

E9 and E10 remain manual retirements and need the approvals recorded in the
full-migration walkthrough. R2 remains manual because it uses a shared engine.
