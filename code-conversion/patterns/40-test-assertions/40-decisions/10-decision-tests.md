# DMN Decision Test Migration

Migrate each Camunda 7 decision test to evaluate the converted DMN copy with CPT. Camunda 8 evaluates required decisions in a deployed DRD when the test evaluates the parent decision.

## Camunda 7

```java
@Rule
public DmnEngineRule dmnEngineRule = new DmnEngineRule();

@Test
public void evaluatesTheDecision() {
  DmnDecision decision = dmnEngineRule.getDmnEngine()
      .parseDecision("dish", getClass().getResourceAsStream("dish.dmn"));
  DmnDecisionTableResult result = dmnEngineRule.getDmnEngine()
      .evaluateDecisionTable(decision, Variables.putValue("season", "Spring"));

  assertThat(result.getSingleResult().getSingleEntry()).isEqualTo("Water");
}
```

## Camunda 8

```java
import io.camunda.process.test.api.CamundaAssert;
import io.camunda.process.test.api.assertions.DecisionSelectors;

@CamundaProcessTest
@TestDeployment(resources = "converted-c8-dish.dmn")
class DishDecisionTest {
  private CamundaClient client;

  @Test
  void evaluatesTheDecision() {
    Map<String, Object> variables = new HashMap<>();
    variables.put("season", "Spring");

    EvaluateDecisionResponse response = client.newEvaluateDecisionCommand()
        .decisionId("dish").variables(variables).send().join();

    CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response))
        .isEvaluated().hasOutput("Water");
  }
}
```

CPT's DMN evaluation and assertion APIs shown here are available from Camunda 8.8. `@TestDeployment` requires Camunda 8.9. On 8.8, deploy the converted DMN copy in `@BeforeEach` with `client.newDeployResourceCommand().addResourceFromClasspath(...)`, as described in the [test deployment pattern](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/40-test-assertions/20-test-setup/20-deployment.md).

| Camunda 7 | CPT | Note |
|---|---|---|
| `@Rule DmnEngineRule`, `DmnEngineConfiguration.createDefaultDmnEngineConfiguration().buildEngine()`, or a `DmnEngine` built from `DmnEngineConfiguration` | `@CamundaProcessTest` with `CamundaClient` | Camunda 7 evaluates DMN in process. CPT uses a Camunda runtime, with Testcontainers by default. |
| `parseDecision(...)`, `parseDecisions(...)`, `@Deployment(resources = "dish.dmn")` | `@TestDeployment(resources = "converted-c8-dish.dmn")` | A DRD deploys as one resource. |
| `DecisionService.evaluateDecisionByKey("dish").variables(vars).evaluate()`, `DmnEngine.evaluateDecisionTable(decision, vars)`, `evaluateDecision(decision, vars)`, or `evaluateDecisionTableByKey("dish", vars)` | `client.newEvaluateDecisionCommand().decisionId("dish").variables(vars).send().join()` | The Camunda 8 command evaluates required decisions automatically. |
| `Variables.putValue(...)` | `Map<String, Object>` | Keep values in JSON-compatible form. When a Camunda 7 value is a `Date` or typed value, check that the converted DMN reads its JSON representation as intended. Do not assume the Java type survives serialization. |
| `getSingleResult().getSingleEntry()`, `getSingleEntry()` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated().hasOutput(value)` | Use for one output column and a single-result hit policy. |
| `getSingleResult().getEntry("a")`, `getEntryMap()` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated().hasOutput(Map.of("a", value, "b", value))` | `hasOutput` compares all outputs. Parse `response.getDecisionOutput()` to check only selected fields. |
| `collectEntries("x")` with hit policy `COLLECT` | Parse `response.getDecisionOutput()` as a list of scalar values for one output column or maps keyed by output name for multiple columns. | Select values by output name and compare rows without relying on their order. Do not use `hasOutput(List)` for `COLLECT`. |
| `collectEntries("x")` with hit policy `RULE ORDER` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).isEvaluated().hasOutput(List.of(...))` | `RULE ORDER` defines result order. |
| `collectEntries("x")` with hit policy `OUTPUT ORDER` | Manual redesign | Camunda 8.9 does not support `OUTPUT ORDER`. |
| `result.isEmpty()`, `getSingleResult()` is `null` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).hasNoMatchedRules()` | |
| Matched-rule checks through `HistoricDecisionInstance` | `CamundaAssert.assertThatDecision(DecisionSelectors.byResponse(response)).hasMatchedRules(int...)` or `.hasNotMatchedRules(int...)` | `hasMatchedRules` passes when the given rule indexes are a subset of the matched rules. |
| An expected `DmnEngineException`, including one wrapped by `DecisionService` in `ProcessEngineException` | Check `response.getFailureMessage()` and `response.getFailedDecisionId()` | Camunda 8 returns a failed response instead of throwing. `isEvaluated()` fails for a failed evaluation. |

Keep null inputs. Build variables with a `HashMap` or another map that accepts null values. Do not use `Map.of` when the Camunda 7 test passed a null value.

`hasOutput(List)` compares list order. `COLLECT` does not define that order in Camunda 8, so parse the output and use an order-insensitive AssertJ assertion.

[CPT decision assertions](https://docs.camunda.io/docs/apis-tools/testing/assertions/) · [DMN hit policies](https://docs.camunda.io/docs/components/modeler/dmn/decision-table-hit-policy/)
