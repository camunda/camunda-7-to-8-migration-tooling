# Camunda Platform Scenario Tests

Camunda Platform Scenario stubs wait states and runs an action when the process reaches each state. CPT conditional behavior provides this pattern from Camunda 8.9. Camunda 8.8 has no equivalent conditional-behavior API. Report scenario tests as `Report only` on 8.8 with the reason `test migration needs Camunda 8.9 or later`.

## Camunda 7

```java
@Deployment(resources = "invoice.bpmn")
public class InvoiceScenarioTest {
  @Mock ProcessScenario process;

  @Test
  public void completesTheInvoice() {
    when(process.waitsAtUserTask("Approve"))
        .thenReturn(task -> task.complete());
    Scenario.run(process).startByKey("invoice").execute();
    verify(process).hasFinished("Done");
  }
}
```

## Camunda 8

```java
@CamundaProcessTest
@TestDeployment(resources = "converted-c8-invoice.bpmn")
class InvoiceScenarioTest {
  private CamundaClient client;
  private CamundaProcessTestContext processTestContext;

  @Test
  void completesTheInvoice() {
    processTestContext
        .when(() -> assertThatProcessInstance(byProcessId("invoice"))
            .hasActiveElements("Approve"))
        .as("Approve")
        .then(() -> processTestContext.completeUserTask("Approve"));

    ProcessInstanceEvent pi = client.newCreateInstanceCommand()
        .bpmnProcessId("invoice").latestVersion().send().join();

    assertThat(pi).isCompleted().hasCompletedElements("Done");
  }
}
```

| Camunda Platform Scenario | CPT 8.9 | Note |
|---|---|---|
| `@Mock ProcessScenario`, `MockitoAnnotations.openMocks(this)` | Remove | |
| `waitsAtUserTask("A").thenReturn(task -> task.complete(vars))` | `when(() -> assertThatProcessInstance(byProcessId(pid)).hasActiveElements("A")).as("A").then(() -> processTestContext.completeUserTask("A", vars))` | The action must resolve the wait state so CPT can observe it again. |
| `thenReturn(first, second)` or different actions per call | Chain `.then(first).then(second)` | The last action repeats. |
| `task.handleBpmnError(...)`, `task.handleEscalation(...)` | No direct counterpart | Report for manual migration. |
| `waitsAtServiceTask`, `waitsAtSendTask`, `waitsAtMessageIntermediateThrowEvent`, `waitsAtMessageEndEvent` with `complete(vars)` | `mockJobWorker(type).thenComplete(vars)` | Read the job type from the converted copy. |
| `waitsAtBusinessRuleTask("R")` when the converted task uses `zeebe:calledDecision` | `mockDmnDecision(decisionId, output)` | The called decision runs natively and does not create a worker job. |
| `waitsAtBusinessRuleTask("R")` when the converted task defines `zeebe:taskDefinition` | `mockJobWorker(type).thenComplete(vars)` | Read the job type from the converted copy. |
| Worker-backed wait states with `handleBpmnError(code, vars)` | `mockJobWorker(type).thenThrowBpmnError(code, vars)` | |
| `waitsAtTimerIntermediateEvent("T")` | Assert `hasActiveElements("T")`, then call `increaseTime(duration)` | Read the duration from the converted timer. |
| `action.defer(period, action)` | Increase time in steps, then run the action | Keep each step no longer than the shortest timer period on the path. Assert the expected timer effect after each step. |
| `waitsAtMessageIntermediateCatchEvent` or `waitsAtReceiveTask` with `receive(vars)` | `newCorrelateMessageCommand().messageName(name).correlationKey(key).variables(vars).send().join()` | Use the converted copy's message subscription for the name and key. Wait with `isWaitingForMessage(name, key)`. |
| `waitsAtSignalIntermediateCatchEvent` with `receive()` | `newBroadcastSignalCommand().signalName(name).send().join()` | |
| `waitsAtEventBasedGateway("G")` with an event action | The message, signal, or timer action for the selected event | |
| `waitsAtConditionalIntermediateEvent("C")` | `processTestContext.updateVariables(byKey(pik), vars)` | Conditional events require 8.9. |
| `runsCallActivity("C").thenReturn(Scenario.use(child))` | Deploy the converted child and register its behaviors with `byProcessId(childPid)` | |
| `withMockedProcess("child")`, `waitsAtMockedCallActivity("C")` | `mockChildProcess("child", vars)` | |
| `Scenario.run(process).startByKey(key, vars).execute()` | `newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(vars).send().join()` | |
| `startByMessage(name, vars)` | `newCorrelateMessageCommand().messageName(name).withoutCorrelationKey().variables(vars).send().join()` | |
| `.fromBefore("A")` | `.startBeforeElement("A")` on the create command | `fromAfter` has no direct counterpart. Start before the next element only when it is unambiguous. |
| `Scenario.instance(process)` | The `ProcessInstanceEvent` returned by the create command | |
| `verify(process).hasCompleted("E")` | `assertThat(pi).hasCompletedElements("E")` | |
| `verify(process).hasFinished("E")` | `hasCompletedElements("E")` or `hasTerminatedElements("E")` | `hasFinished` includes completed and cancelled activities. |
| `verify(process, times(n)).hasFinished("E")` | `hasCompletedElement("E", n)` | Waits for the exact count. |
| `verify(process).hasCanceled("E")` | `hasTerminatedElements("E")` | |
| `verify(process).hasStarted("E")` | Assert the reached state with `hasActiveElements`, `hasCompletedElements`, or `hasTerminatedElements` | |
| `verify(process, never()).hasStarted("E")` | `hasNotActivatedElements("E")` after a waiting assertion | This check does not wait. |

The scenario runner fails as soon as the process reaches an unstubbed wait state. CPT leaves the process waiting, so a final assertion fails after its timeout. Keep the existing stubs. Do not add behavior for a wait state that the Camunda 7 test left undefined.

The scenario runner advances the clock to each due timer in turn. CPT advances the clock once per call. Increase time in steps no longer than the shortest timer period on the path, and assert the expected effect after each step. For `defer(period, action)`, run the action after the total increase reaches `period`.

External tasks in Camunda 7 scenarios were completed by the scenario runner, so use `mockJobWorker` for those task types. Java delegates are not wait states. Keep migrated workers real unless the Camunda 7 test mocked them.

[CPT conditional behavior](https://docs.camunda.io/docs/apis-tools/testing/utilities/#conditional-behavior)
