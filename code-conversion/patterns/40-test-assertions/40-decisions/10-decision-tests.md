# Decision Tests

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

    assertThat(response).isEvaluated().hasOutput("Water");
  }
}
```

`@TestDeployment` requires Camunda 8.9. On 8.8, deploy the converted DMN copy in `@BeforeEach` with `client.newDeployResourceCommand().addResourceFromClasspath(...)`, as described in [test deployment](../20-test-setup/20-deployment.md).

| Camunda 7 | CPT | Note |
|---|---|---|
| `@Rule DmnEngineRule`, `DmnEngineConfiguration.createDefaultDmnEngineConfiguration().buildEngine()` | `@CamundaProcessTest` with `CamundaClient` | Camunda 7 evaluates DMN in process. CPT uses a Camunda runtime, with Testcontainers by default. |
| `parseDecision(...)`, `parseDecisions(...)`, `@Deployment(resources = "dish.dmn")` | `@TestDeployment(resources = "converted-c8-dish.dmn")` | A DRD deploys as one resource. |
| `DecisionService.evaluateDecisionByKey(...)`, `DmnEngine.evaluateDecisionTable(...)`, `evaluateDecision(...)` | `client.newEvaluateDecisionCommand().decisionId("dish").variables(vars).send().join()` | |
| `Variables.putValue(...)` | `Map<String, Object>` | Keep values in JSON-compatible form and check how the converted DMN reads them. |
| `getSingleResult().getSingleEntry()`, `getSingleEntry()` | `assertThat(response).hasOutput(value)` | For one output column and a single-result hit policy. |
| `getSingleResult().getEntry("a")`, `getEntryMap()` | `assertThat(response).hasOutput(Map.of("a", value, "b", value))` | `hasOutput` compares all outputs. Parse `response.getDecisionOutput()` to check only selected fields. |
| `collectEntries("x")` with hit policy `COLLECT` | Parse `response.getDecisionOutput()` and assert with `containsExactlyInAnyOrder` | Camunda 8 returns `COLLECT` results in arbitrary order. Do not use `hasOutput(List)` for `COLLECT`. |
| `collectEntries("x")` with `RULE ORDER` or `OUTPUT ORDER` | `assertThat(response).hasOutput(List.of(...))` | These hit policies define result order. |
| `result.isEmpty()`, `getSingleResult()` is `null` | `assertThat(response).hasNoMatchedRules()` | |
| Historic decision rule checks | `hasMatchedRules(int...)`, `hasNotMatchedRules(int...)` | `hasMatchedRules` passes when the given rule indexes are a subset of the matched rules. |
| Expected `DmnEngineException`, such as a `UNIQUE` hit-policy violation | Assert `response.getFailureMessage()` is not null | Failed evaluations return failure details instead of throwing. `isEvaluated()` fails for a failed evaluation. |

Keep null inputs. Build variables with a `HashMap` or another map that accepts null values. Do not use `Map.of` when the Camunda 7 test passed a null value.

`hasOutput(List)` compares list order. `COLLECT` does not define that order in Camunda 8, so parse the output and use an order-insensitive AssertJ assertion.

[CPT decision assertions](https://docs.camunda.io/docs/apis-tools/testing/assertions/) · [DMN hit policies](https://docs.camunda.io/docs/components/modeler/dmn/decision-table-hit-policy/)
