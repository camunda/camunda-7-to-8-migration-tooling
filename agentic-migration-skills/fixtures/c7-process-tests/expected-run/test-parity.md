# Test Parity — full migration, target 8.9

| Camunda 7 Test ID | CPT Test ID(s) | Verdict | Notes |
|---|---|---|---|
| `engine-tests:com.camunda.fixture.order.OrderProcessTest#approvesAndShipsOrder` | `engine-tests:com.camunda.fixture.order.OrderProcessTest#approvesAndShipsOrder` | migrated | The CPT test runs the order worker path, correlates PaymentConfirmed, calls the real shipping process, and evaluates discount. |
| `engine-tests:com.camunda.fixture.order.OrderTimerTest#escalatesAfterOneDay` | `engine-tests:com.camunda.fixture.order.OrderTimerTest#escalatesAfterOneDay` | migrated | `increaseTime(Duration.ofDays(1))` fires the approval timer. A separate C8-only test completes the escalation and verifies `End_Escalated`. |
| `engine-tests:com.camunda.fixture.order.LegacyOrderTest#testStockMissing` | `engine-tests:com.camunda.fixture.order.LegacyOrderTest#testStockMissing` | migrated | A worker failure creates an incident instead of throwing into the start call. A separate C8-only success path verifies the legacy process end. |
| `engine-tests:com.camunda.fixture.order.OrderMockitoTest#registersWholeDelegateAndExecutionListenerMocks` | `engine-tests:com.camunda.fixture.order.OrderMockitoTest#registersWholeDelegateAndExecutionListenerMocks` | migrated | The job worker, execution listener, and child-process boundaries are mocked in CPT. |
| `engine-tests:com.camunda.fixture.order.OrderMockitoTest#registersDelegateOutputAndVerifiesItsInvocation` | `engine-tests:com.camunda.fixture.order.OrderMockitoTest#registersDelegateOutputAndVerifiesItsInvocation` | migrated | The CPT worker mock returns variables and reports its invocation count. |
| `engine-tests:com.camunda.fixture.order.OrderMockitoTest#routesDelegateBpmnError` | `engine-tests:com.camunda.fixture.order.OrderMockitoTest#routesDelegateBpmnError` | migrated | The mocked worker throws `PAYMENT_FAILED` and the error boundary completes. |
| `engine-tests:com.camunda.fixture.order.OrderMockitoTest#throwsWhenTheSynchronousDelegateFails` | `engine-tests:com.camunda.fixture.order.OrderMockitoTest#reportsIncidentWhenTheWorkerFails` | migrated | An exhausted CPT job raises an incident. |
| `engine-tests:com.camunda.fixture.order.OrderAutoMockTest#autoMocksDelegatesAndTracksCoverage` | `engine-tests:com.camunda.fixture.order.OrderAutoMockTest#autoMocksDelegatesAndTracksCoverage` | migrated | CPT workers traverse the converted process. |
| `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#evaluatesEveryOutputColumn` | `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#evaluatesEveryOutputColumn` | migrated | Both DMN output columns are asserted. |
| `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#returnsNoMatch` | `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#returnsNoMatch` | migrated | CPT reports no matched rules for bronze. |
| `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#preservesNullInput` | `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#preservesNullInput` | migrated | The null input produces no matched rule. |
| `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#raisesUniqueHitPolicyViolation` | `engine-tests:com.camunda.fixture.order.DiscountDecisionTest#raisesUniqueHitPolicyViolation` | migrated | The response reports the failed UNIQUE decision. |
| `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#evaluatesRequiredDecision` | `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#evaluatesRequiredDecision` | migrated | The DRG evaluates customer-tier before promotions. |
| `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#collectsPromotionRules` | `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#collectsPromotionRules` | migrated | The COLLECT decision returns both gold promotions. |
| `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#raisesUniqueHitPolicyViolation` | `engine-tests:com.camunda.fixture.order.PromotionsDecisionTest#raisesUniqueHitPolicyViolation` | migrated | The response identifies the decision with the UNIQUE violation. |
| `engine-tests:com.camunda.fixture.order.FulfillmentScenarioTest#shouldCompleteWorkAfterTwoDailyReminders` | `engine-tests:com.camunda.fixture.order.FulfillmentScenarioTest#shouldCompleteWorkAfterTwoDailyReminders` | migrated | CPT repeats the scenario, advances time twice, completes the external task, correlates the message, and mocks the child process. |
| `engine-tests:com.camunda.fixture.order.SupportCaseTest#startsSupportCase` | — | retired | CMMN has no Camunda 8 equivalent |
| `engine-tests:com.camunda.fixture.order.FluentModelTest#buildsAndStartsModel` | — | retired | model built in Java, migrated by hand later |
| `engine-tests:com.camunda.fixture.order.CheckStockDelegateTest#setsStockFlagForAvailableItem` | — | not-in-scope | The C7 test is a unit test with no engine-backed process or decision; the C8-only `OrderStockWorkerTest` checks the migrated worker output. |
| `engine-tests:com.camunda.fixture.order.ChargePaymentDelegateFakeTest#writesPaymentReference` | — | not-in-scope | Delegate unit test; no engine-backed process or decision. |
| `engine-tests:com.camunda.fixture.order.PriceCalculatorTest#appliesDiscount` | — | not-in-scope | Plain Java unit test. |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionProcessTest#activatesSubscription` | `spring-boot-app:com.camunda.fixture.subscription.SubscriptionProcessTest#activatesSubscription` | migrated | The Spring worker calls the mocked BillingClient service. A separate C8-only test completes the welcome task. |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionEndpointTest#startsSubscriptionFromHttp` | `spring-boot-app:com.camunda.fixture.subscription.SubscriptionEndpointTest#startsSubscriptionFromHttp` | migrated | TestRestTemplate starts the process through the application endpoint. |
| `spring-boot-app:com.camunda.fixture.subscription.ActivateDelegateMockTest#mocksDelegateBean` | `spring-boot-app:com.camunda.fixture.subscription.ActivateDelegateMockTest#mocksDelegateBean` | migrated | The CPT job mock replaces the real activation worker. |
| `spring-boot-app:com.camunda.fixture.subscription.HousekeepingStartupTest#startsHousekeepingOnDeployment` | `spring-boot-app:com.camunda.fixture.subscription.HousekeepingStartupTest#startsHousekeepingOnDeployment` | migrated | The Spring startup hook starts housekeeping. A separate C8-only test completes the review task. |
| `spring-boot-app:com.camunda.fixture.subscription.SubscriptionStandaloneTest#startsSubscriptionWithoutSpring` | `spring-boot-app:com.camunda.fixture.subscription.SubscriptionStandaloneTest#startsSubscriptionWithoutSpring` | migrated | The CPT test uses no Spring application context. |
| `remote-engine:com.camunda.fixture.payment.PaymentWorkerIT#chargesPaymentThroughEngineRest` | `remote-engine:com.camunda.fixture.payment.PaymentWorkerIT#chargesPayment` | migrated | CPT replaces the C7 container and Engine REST calls with its managed runtime. |
| `remote-engine:com.camunda.fixture.payment.SharedEngineSmokeIT#readsConfiguredSharedEngine` | — | manual | shared environment; CPT deletes runtime data between tests. |
| `remote-engine:com.camunda.fixture.payment.ChargePaymentHandlerTest#completesExternalTask` | — | not-in-scope | External task handler unit test; no engine-backed process or decision. |

## Approved retirements

| Test ID | Decision | Reason |
|---|---|---|
| `engine-tests:com.camunda.fixture.order.SupportCaseTest#startsSupportCase` | retired | CMMN has no Camunda 8 equivalent |
| `engine-tests:com.camunda.fixture.order.FluentModelTest#buildsAndStartsModel` | retired | model built in Java, migrated by hand later |

R2 remains manual because the test uses a shared engine that the test does not start.
