# Migration Report — Migrate tests only

## User choices

| Question | Answer |
|---|---|
| Scope | Code + models |
| Target | Camunda 8.9 |
| Question 8 | Migrate tests only |

## Step 2 Summary

- process test: 13
- decision test: 7
- scenario test: 8
- remote-engine test: 2
- manual migration: 2
- manual redesign: 1
- out of scope: 12
- out of scope (Camunda 8): 0
- 29 tests are eligible for CPT migration.

## Test Inventory — target 8.9

| Test ID | File | Test kind | Signals | Models | Handling | Notes |
|---|---|---|---|---|---|---|
| `engine-tests:com.camunda.fixture.order.OrderProcessTest#approvesAndShipsOrder` | `engine-tests/src/test/java/com/camunda/fixture/order/OrderProcessTest.java` | process test | JUnit 4; inherited ProcessEngineRule; BpmnAwareTests; collaborator mock; async job; message correlation; mocks modifier; time modifier | `order.bpmn`, `shipping.bpmn`, `discount.dmn` | Migrate to CPT | E1; Inherited engine setup comes from `AbstractOrderProcessTest`. |
| `engine-tests:com.camunda.fixture.order.OrderTimerTest#escalatesAfterOneDay` | `engine-tests/src/test/java/com/camunda/fixture/order/OrderTimerTest.java` | process test | JUnit 5; ProcessEngineExtension; ClockUtil; execute(job()); isNotWaitingAt; mocks modifier; time modifier | `order.bpmn` | Migrate to CPT | E2; Boundary timer on `Task_Approve`. |
| `engine-tests:com.camunda.fixture.order.LegacyOrderTest#testStockMissing` | `engine-tests/src/test/java/com/camunda/fixture/order/LegacyOrderTest.java` | process test | JUnit 3; ProcessEngineTestCase; implicit @Deployment | `src/test/resources/com/camunda/fixture/order/LegacyOrderTest.testStockMissing.bpmn` | Migrate to CPT | E3; Expected delegate failure occurs during process start. |
| `engine-tests:com.camunda.fixture.order.OrderMockitoTest#registersWholeDelegateAndExecutionListenerMocks` | `engine-tests/src/test/java/com/camunda/fixture/order/OrderMockitoTest.java` | process test | JUnit 4; Mocks.register; JavaDelegate Mockito mock; registerExecutionListenerMock; registerCallActivityMock; mocks modifier; time modifier | `order.bpmn`, `discount.dmn`; mocked child process `shipping` | Migrate to CPT | E4; Whole delegate and listener mock boundaries. |
| `engine-tests:com.camunda.fixture.order.OrderMockitoTest#registersDelegateOutputAndVerifiesItsInvocation` | `engine-tests/src/test/java/com/camunda/fixture/order/OrderMockitoTest.java` | process test | JUnit 4; camunda-platform-7-mockito; onExecutionSetVariables; executed(times(1)); mocks modifier; time modifier | `order.bpmn`, `discount.dmn`; mocked child process `shipping` | Migrate to CPT | E4; JavaDelegate mock output and invocation count. |
| `engine-tests:com.camunda.fixture.order.OrderMockitoTest#routesDelegateBpmnError` | `engine-tests/src/test/java/com/camunda/fixture/order/OrderMockitoTest.java` | process test | JUnit 4; onExecutionThrowBpmnError; error boundary; mocks modifier; time modifier | `order.bpmn`, `discount.dmn` | Migrate to CPT | E4; `PAYMENT_FAILED` selects the notification path. |
| `engine-tests:com.camunda.fixture.order.OrderMockitoTest#throwsWhenTheSynchronousDelegateFails` | `engine-tests/src/test/java/com/camunda/fixture/order/OrderMockitoTest.java` | process test | JUnit 4; onExecutionThrowException; async job; mocks modifier; time modifier | `order.bpmn`, `discount.dmn` | Migrate to CPT | E4; Expected C8 behavior is an incident. |
| `engine-tests:com.camunda.fixture.order.OrderAutoMockTest#autoMocksDelegatesAndTracksCoverage` | `engine-tests/src/test/java/com/camunda/fixture/order/OrderAutoMockTest.java` | process test | JUnit 5; DelegateExpressions.autoMock; ProcessEngineCoverageExtension; mocks modifier; coverage modifier | `order.bpmn` | Migrate to CPT | E5; Coverage modifier and Camunda 7 baseline. |
| `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#evaluatesEveryOutputColumn` | `engine-tests/src/test/java/com/camunda/fixture/order/DiscountDecisionTest.java` | decision test | JUnit 4; DmnEngineRule; multi-output table | `discount.dmn` | Migrate | E6; Checks both output columns. |
| `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#returnsNoMatch` | `engine-tests/src/test/java/com/camunda/fixture/order/DiscountDecisionTest.java` | decision test | JUnit 4; DmnEngineRule; no matching rule | `discount.dmn` | Migrate | E6; Bronze customers have no matching rule. |
| `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#preservesNullInput` | `engine-tests/src/test/java/com/camunda/fixture/order/DiscountDecisionTest.java` | decision test | JUnit 4; DmnEngineRule; null input | `discount.dmn` | Migrate | E6; customerType is explicitly null. |
| `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#raisesUniqueHitPolicyViolation` | `engine-tests/src/test/java/com/camunda/fixture/order/DiscountDecisionTest.java` | decision test | JUnit 4; DmnEngineRule; UNIQUE hit policy | `discount.dmn` | Migrate | E6; Overlapping rows violate UNIQUE. |
| `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#evaluatesRequiredDecision` | `engine-tests/src/test/java/com/camunda/fixture/order/PromotionsDecisionTest.java` | decision test | JUnit 5; ProcessEngineExtension; DecisionService; required decision | `promotions.dmn` (programmatic deployment) | Migrate | E7; promotions requires customer-tier. |
| `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#collectsPromotionRules` | `engine-tests/src/test/java/com/camunda/fixture/order/PromotionsDecisionTest.java` | decision test | JUnit 5; ProcessEngineExtension; COLLECT hit policy | `promotions.dmn` (programmatic deployment) | Migrate | E7; Both matching promotion rules are returned. |
| `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#raisesUniqueHitPolicyViolation` | `engine-tests/src/test/java/com/camunda/fixture/order/PromotionsDecisionTest.java` | decision test | JUnit 5; ProcessEngineExtension; UNIQUE hit policy | `promotions.dmn` (programmatic deployment) | Migrate | E7; Overlapping rules violate UNIQUE. |
| `engine-tests:com.camunda.fixture.order.FulfillmentScenarioTest#shouldCompleteWorkAfterTwoDailyReminders` | `engine-tests/src/test/java/com/camunda/fixture/order/FulfillmentScenarioTest.java` | scenario test | JUnit 4; camunda-platform-scenario; timer; external task; message; mocked call activity; mocks modifier; time modifier | `fulfillment.bpmn`; mocked child process `shipping` | Migrate (lower priority) | E8; Verifies two reminders and exact completion counts. |
| `engine-tests-legacy:com.camunda.fixture.order.FulfillmentScenarioTest#shouldCompleteWorkAfterTwoDailyReminders` | `engine-tests/src/test/java/com/camunda/fixture/order/FulfillmentScenarioTest.java` | scenario test | JUnit 4; camunda-bpm-assert-scenario; timer; external task; message; mocked call activity; mocks modifier; time modifier | `fulfillment.bpmn`; mocked child process `shipping` | Migrate (lower priority) | E8; executes the shared test source with the legacy Scenario artifact. |
| `engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldStartMessageProcess` | `engine-tests/src/test/java/com/camunda/fixture/order/ScenarioMappingEdgeCasesTest.java` | scenario test | JUnit 4; camunda-platform-scenario; Scenario.run().startByMessage; message-start verification; mocks modifier | `review-edge-cases.bpmn` | Migrate (lower priority) | E8; verifies message-start completion. |
| `engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldStartMessageProcess` | `engine-tests/src/test/java/com/camunda/fixture/order/ScenarioMappingEdgeCasesTest.java` | scenario test | JUnit 4; camunda-bpm-assert-scenario; Scenario.run().startByMessage; message-start verification; mocks modifier | `review-edge-cases.bpmn` | Migrate (lower priority) | E8; executes the shared test source with the legacy Scenario artifact. |
| `engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountCompletedVisitsSeparately` | `engine-tests/src/test/java/com/camunda/fixture/order/ScenarioMappingEdgeCasesTest.java` | scenario test | JUnit 4; camunda-platform-scenario; waitsAtServiceTask; two worker visits; times(2); Mockito @Mock, @Spy, @Captor, @InjectMocks; openMocks; mocks modifier | `review-edge-cases.bpmn` | Migrate (lower priority) | E8; verifies exact completed visits and retained Mockito initialization. |
| `engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountCompletedVisitsSeparately` | `engine-tests/src/test/java/com/camunda/fixture/order/ScenarioMappingEdgeCasesTest.java` | scenario test | JUnit 4; camunda-bpm-assert-scenario; waitsAtServiceTask; two worker visits; times(2); Mockito @Mock, @Spy, @Captor, @InjectMocks; openMocks; mocks modifier | `review-edge-cases.bpmn` | Migrate (lower priority) | E8; executes the shared test source with the legacy Scenario artifact. |
| `engine-tests:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountMixedFinishedVisitsByOutcome` | `engine-tests/src/test/java/com/camunda/fixture/order/ScenarioMappingEdgeCasesTest.java` | scenario test | JUnit 4; camunda-platform-scenario; waitsAtServiceTask; BPMN error on second visit; mixed completion outcomes; mocks modifier | `review-edge-cases.bpmn` | Migrate (lower priority) | E8; verifies one completed and one terminated visit separately. |
| `engine-tests-legacy:com.camunda.fixture.order.ScenarioMappingEdgeCasesTest#shouldCountMixedFinishedVisitsByOutcome` | `engine-tests/src/test/java/com/camunda/fixture/order/ScenarioMappingEdgeCasesTest.java` | scenario test | JUnit 4; camunda-bpm-assert-scenario; waitsAtServiceTask; BPMN error on second visit; mixed completion outcomes; mocks modifier | `review-edge-cases.bpmn` | Migrate (lower priority) | E8; executes the shared test source with the legacy Scenario artifact. |
| `engine-tests:com.camunda.fixture.order.SupportCaseTest#startsSupportCase` | `engine-tests/src/test/java/com/camunda/fixture/order/SupportCaseTest.java` | manual redesign | JUnit 4; CmmnAwareTests; CMMN model | `support-case.cmmn` | Report only | E9; CMMN has no Camunda 8 equivalent. |
| `engine-tests:com.camunda.fixture.order.FluentModelTest#buildsAndStartsModel` | `engine-tests/src/test/java/com/camunda/fixture/order/FluentModelTest.java` | manual migration | JUnit 5; Bpmn.createExecutableProcess(); programmatic model deployment; no applicable modifiers | Model built in Java | Report only | E10; model built in Java, migrated by hand later. |
| `engine-tests:com.camunda.fixture.order.InheritedInventoryOneTest#inheritedInventoryTest` | `engine-tests/src/test/java/com/camunda/fixture/order/AbstractInheritedInventoryTestBase.java` | out of scope | JUnit 4; inherited @Test | None | Not part of test migration | Inherited method; report one row for this concrete class. |
| `engine-tests:com.camunda.fixture.order.InheritedInventoryTwoTest#inheritedInventoryTest` | `engine-tests/src/test/java/com/camunda/fixture/order/AbstractInheritedInventoryTestBase.java` | out of scope | JUnit 4; inherited @Test | None | Not part of test migration | Inherited method; report one row for this concrete class. |
| `engine-tests:com.camunda.fixture.order.InventoryDiscoverySpec#feature without engine` | `engine-tests/src/test/groovy/com/camunda/fixture/order/InventoryDiscoverySpec.groovy` | out of scope | Spock; Groovy feature method | None | Not part of test migration | Discovered as a Spock feature; does not execute a C7 process or decision. |
| `engine-tests:com.camunda.fixture.order.JGivenEngineBackedTest#startsProcess` | `engine-tests/src/test/java/com/camunda/fixture/order/JGivenEngineBackedTest.java` | manual migration | JGiven; ProcessEngineExtension; RuntimeService.startProcessInstanceByKey; no applicable modifiers | `jgiven-inventory.bpmn` | Report only | J1; JGiven test executes a real C7 process and requires manual migration. |
| `engine-tests:com.camunda.fixture.order.JGivenNoEngineTest#runsPlainUnitTest` | `engine-tests/src/test/java/com/camunda/fixture/order/JGivenNoEngineTest.java` | out of scope | JGiven; no engine-backed process or decision | None | Not part of test migration | J2; JGiven test runs no engine-backed BPMN process or DMN decision. |
| `engine-tests:src/test/resources/features/CandidateDiscovery.feature#Without engine execution@L10` | `engine-tests/src/test/resources/features/CandidateDiscovery.feature` | out of scope | Cucumber Scenario | None | Not part of test migration | Discovered as a Cucumber scenario; does not execute a C7 process or decision. |
| `engine-tests:src/test/resources/features/CandidateDiscovery.feature#MultipleExampleRows@L18` | `engine-tests/src/test/resources/features/CandidateDiscovery.feature` | out of scope | Cucumber Scenario Outline | None | Not part of test migration | One row for this Scenario Outline example; no C7 process or decision executes. |
| `engine-tests:src/test/resources/features/CandidateDiscovery.feature#MultipleExampleRows@L19` | `engine-tests/src/test/resources/features/CandidateDiscovery.feature` | out of scope | Cucumber Scenario Outline | None | Not part of test migration | One row for this Scenario Outline example; no C7 process or decision executes. |
| `engine-tests:com.camunda.fixture.order.CheckStockDelegateTest#setsStockFlagForAvailableItem` | `engine-tests/src/test/java/com/camunda/fixture/order/CheckStockDelegateTest.java` | out of scope | JUnit 5; Mockito mock(DelegateExecution.class) | None | Not part of test migration | E11; unit test runs no engine. |
| `engine-tests:com.camunda.fixture.order.ChargePaymentDelegateFakeTest#writesPaymentReference` | `engine-tests/src/test/java/com/camunda/fixture/order/ChargePaymentDelegateFakeTest.java` | out of scope | JUnit 4; DelegateExecutionFake; camunda-platform-7-mockito | None | Not part of test migration | E12; delegate unit test runs no engine and remains an ordinary code test. |
| `engine-tests:com.camunda.fixture.order.PriceCalculatorTest#appliesDiscount` | `engine-tests/src/test/java/com/camunda/fixture/order/PriceCalculatorTest.java` | out of scope | JUnit 5; plain Java | None | Not part of test migration | E13; plain unit test. |
| `engine-tests:com.camunda.fixture.order.MockitoAnnotationTest#initializesMockitoAnnotationsForPlainUnitTest` | `engine-tests/src/test/java/com/camunda/fixture/order/MockitoAnnotationTest.java` | out of scope | JUnit 4; Mockito @Spy, @Captor, @InjectMocks; openMocks | None | Not part of test migration | E14: plain Mockito unit test. It runs no engine. |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionProcessTest#activatesSubscription` | `spring-boot-app/src/test/java/com/camunda/fixture/subscription/SubscriptionProcessTest.java` | process test | JUnit 4; @SpringBootTest; @MockBean BillingClient; camunda-bpm-assert; Spring modifier; mocks modifier | `subscription.bpmn`, `housekeeping.bpmn` (Spring Boot auto-deployment) | Migrate to CPT | S1; the real delegate calls the mocked billing collaborator. |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionEndpointTest#startsSubscriptionFromHttp` | `spring-boot-app/src/test/java/com/camunda/fixture/subscription/SubscriptionEndpointTest.java` | process test | JUnit 4; @SpringBootTest RANDOM_PORT; TestRestTemplate; @MockBean BillingClient; Spring modifier; mocks modifier | `subscription.bpmn`, `housekeeping.bpmn` (Spring Boot auto-deployment) | Migrate to CPT | S2; endpoint starts the process instance. |
| `spring-boot-app:com.camunda.fixture.subscription.ActivateDelegateMockTest#mocksDelegateBean` | `spring-boot-app/src/test/java/com/camunda/fixture/subscription/ActivateDelegateMockTest.java` | process test | JUnit 4; @SpringBootTest; @MockBean(name="activateSubscriptionDelegate") JavaDelegate; Spring modifier; mocks modifier | `subscription.bpmn`, `housekeeping.bpmn` (Spring Boot auto-deployment) | Migrate to CPT | S3; mocks the whole delegate bean. |
| `spring-boot-app:com.camunda.fixture.subscription.HousekeepingStartupTest#startsHousekeepingOnDeployment` | `spring-boot-app/src/test/java/com/camunda/fixture/subscription/HousekeepingStartupTest.java` | process test | JUnit 4; @SpringBootTest; PostDeployEvent startup hook; Spring modifier | `subscription.bpmn`, `housekeeping.bpmn` (Spring Boot auto-deployment) | Migrate to CPT | S4; application startup begins housekeeping. |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionStandaloneTest#startsSubscriptionWithoutSpring` | `spring-boot-app/src/test/java/com/camunda/fixture/subscription/SubscriptionStandaloneTest.java` | process test | JUnit 4; AbstractProcessEngineRuleTest; @Deployment; Mocks.register; no Spring context; mocks modifier | `subscription.bpmn` | Migrate to CPT | S5; Standalone engine test inside the Spring Boot module. |
| `remote-engine:com.camunda.fixture.payment.PaymentWorkerIT#chargesPaymentThroughEngineRest` | `remote-engine/src/test/java/com/camunda/fixture/payment/PaymentWorkerIT.java` | remote-engine test | JUnit 5; Testcontainers Camunda 7.24.0; Engine REST; external task client; Awaitility; Failsafe; Spring modifier | `payment.bpmn` (REST deployment) | Migrate (lower priority) | R1; Test starts the engine container and polls history. |
| `remote-engine:com.camunda.fixture.payment.SharedEngineSmokeIT#readsConfiguredSharedEngine` | `remote-engine/src/test/java/com/camunda/fixture/payment/SharedEngineSmokeIT.java` | remote-engine test | JUnit 5; SHARED_ENGINE_URL; environment-variable condition; Engine REST; Failsafe | None; shared engine | Report only | R2; CPT deletes all runtime data between tests, so the test needs a dedicated Camunda 8 runtime. |
| `remote-engine:com.camunda.fixture.payment.ChargePaymentHandlerTest#completesExternalTask` | `remote-engine/src/test/java/com/camunda/fixture/payment/ChargePaymentHandlerTest.java` | out of scope | JUnit 5; Mockito ExternalTask and ExternalTaskService | None | Not part of test migration | R3; handler unit test runs no engine. |

## Test kind counts

| Test kind | Count |
|---|---:|
| process test | 13 |
| decision test | 7 |
| scenario test | 8 |
| remote-engine test | 2 |
| manual migration | 2 |
| manual redesign | 1 |
| out of scope | 12 |
| out of scope (Camunda 8) | 0 |

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
   The validator checks this permitted mode transition separately and keeps earlier code and model checks current.
2. Use the baseline preserved before Step 3:

   | Step 2 source | Baseline source |
   |---|---|
   | Clean Git working tree and every `source_files` path tracked at the recorded commit | A worktree at the recorded commit. |
   | Dirty Git working tree, non-Git source, or any `source_files` path missing from the recorded commit | The recorded filesystem snapshot. |

3. Record `docker_info` before the first Docker-dependent suite.
4. Record each C7 baseline with kind `c7_baseline` and `--baseline-root ../c7-source-baseline/`.
   Use each exact Step 2 suite command: `mvn -pl engine-tests,engine-tests-legacy,spring-boot-app test`
   and `mvn -pl remote-engine verify`.
5. Confirm that `test-mapping.json` and its approved test and mock changes are complete.
6. Record `test_freeze` after the migrated test sources and resources are final.
7. Start Docker. Record `test_repeat` for suites with mapped or added CPT tests, using their exact
   migrated CPT commands recorded here: `mvn -pl engine-tests,spring-boot-app test` and
   `mvn -pl remote-engine verify`. These can differ from the Step 2 C7 baseline commands above.
   Do not use `tests` for mapped suites.
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
