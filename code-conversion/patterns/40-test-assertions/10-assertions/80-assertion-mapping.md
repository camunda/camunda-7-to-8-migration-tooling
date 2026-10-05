# Camunda 7 Assertion Mapping

`BpmnAwareTests` and `ProcessEngineTests` are the two Camunda 7 assertion entry points. `ProcessEngineTests` extends `CmmnAwareTests`, which extends `BpmnAwareTests`. Both map to `io.camunda.process.test.api.CamundaAssert`. The CPT assertions below are available from Camunda 8.8.

Most CPT assertions wait for the expected state for up to 10 seconds by default. `hasNotActivatedElements(...)` is an exception: it evaluates immediately and does not wait. Use it only after a waiting assertion has established the process state where the absence is meaningful. Set another timeout with `CamundaAssert.setAssertionTimeout(...)` or, in Spring, the `camunda.process-test.assertion.timeout` property.

Deploy the converted model before starting an instance. See the [test deployment pattern](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/40-test-assertions/20-test-setup/20-deployment.md) for Camunda 8.8 and 8.9 setup.

## Camunda 7

```java
import static org.camunda.bpm.engine.test.assertions.bpmn.BpmnAwareTests.*;

ProcessInstance pi = runtimeService().startProcessInstanceByKey("order");
assertThat(pi).isNotEnded().isWaitingAt(findId("Approve order"));
assertThat(task()).isAssignedTo("demo");
complete(task());
assertThat(pi).hasPassed("Approved").isEnded();
assertThat(pi).variables().containsEntry("approved", true);
```

## Camunda 8

```java
import static io.camunda.process.test.api.CamundaAssert.assertThat;
import static io.camunda.process.test.api.CamundaAssert.assertThatUserTask;
import static io.camunda.process.test.api.assertions.ElementSelectors.byName;
import static io.camunda.process.test.api.assertions.UserTaskSelectors.byElementId;

ProcessInstanceEvent pi = client.newCreateInstanceCommand()
    .bpmnProcessId("order").latestVersion().send().join();
assertThat(pi).isActive().hasActiveElements(byName("Approve order"));
assertThatUserTask(byElementId("Approve")).hasAssignee("demo");
processTestContext.completeUserTask("Approve");
assertThat(pi).hasCompletedElements("Approved").isCompleted().hasVariable("approved", true);
```

## Assertion mapping

In the CPT rows, `selector` denotes a `UserTaskSelector` scoped to `pi.getProcessInstanceKey()`; use `.isCreated()` to match a currently available Camunda 7 task. CPT checks the first matching record, while Camunda 7 fails if more than one current task matches. `byProcessInstanceKey` can also match completed tasks from earlier in the process. Search by process-instance key and `UserTaskState.CREATED`, then assert `.hasSize(1)` when you need C7's current-task and uniqueness semantics.

| Camunda 7 | CPT | Note |
|---|---|---|
| `assertThat(pi).isWaitingAt("A")` | `assertThat(pi).hasActiveElements("A")` | |
| `isWaitingAt(findId("Name"))` | `hasActiveElements(ElementSelectors.byName("Name"))` | |
| `isWaitingAtExactly("A")` | `hasActiveElementsExactly("A")` | |
| `isNotWaitingAt("A")` | `hasNoActiveElements("A")` | Both inspect the current state. If absence is meaningful only after a later point, assert that observation point first. See below. |
| `hasPassed("A")`, `hasPassedInOrder("A", "B")` | `hasCompletedElements("A")`, `hasCompletedElementsInOrder("A", "B")` | Camunda 7 counts finished activity instances, including cancelled ones. Use `hasTerminatedElements("A")` when a boundary event interrupted the element. |
| `hasNotPassed("A")` | `hasNotActivatedElements("A")` | This is stricter. It also fails when the element is active, so confirm that behavior is intended. |
| `isEnded()` | `isCompleted()` or `isTerminated()` | Camunda 7 `isEnded()` passes for both completed and cancelled instances. |
| `isNotEnded()`, `isActive()` | `isActive()` | |
| `isStarted()` | `isCreated()` | |
| `hasNoVariables()` | No counterpart | Search the variables and assert that the result is empty with AssertJ. |
| `hasVariables("x")` | `hasVariableNames("x")` | CPT `hasVariables(Map)` compares values. |
| `hasVariables()` | No counterpart | Search variables for the process instance and assert that the result is not empty with AssertJ. `hasVariableNames()` with no names always passes. |
| `variables().containsEntry("x", value)` | `hasVariable("x", value)` | |
| `variables().containsKey("x")` | `hasVariableNames("x")` | |
| Other `variables()` map assertions, such as `hasSize` and `isEmpty` | No counterpart | Search the variables with the client and assert with AssertJ. |
| `isWaitingFor("message")` | `isWaitingForMessage("message")` | |
| `isNotWaitingFor("message")` | `isNotWaitingForMessage("message")` | If absence matters only after the process reaches a later state, wait for that observation point first. |
| `hasProcessDefinitionKey("order")` | Assert `ProcessInstanceEvent.getBpmnProcessId()` | Use AssertJ on the returned process instance event. |
| `hasBusinessKey("key")` | `org.assertj.core.api.Assertions.assertThat(pi.getBusinessId()).isEqualTo("key")` (8.9+) | Use this only when the migration maps the Camunda 7 business key to a Camunda 8 business ID. On 8.8, assert the variable or tag selected by the [business-key pattern](https://github.com/camunda/camunda-7-to-8-migration-tooling/blob/main/code-conversion/patterns/20-client-code/10-process-engine/business-key-and-tags.md). |
| `isSuspended()` | No counterpart | Camunda 8 does not expose a suspended process-instance state. Record the test as manual if suspension behavior matters. |
| `calledProcessInstance()` | `assertThat(ProcessInstanceSelectors.byParentProcessInstanceKey(parentKey))` | |
| `calledProcessInstance(String processDefinitionKey)` | `assertThat(ProcessInstanceSelectors.byParentProcessInstanceKey(parentKey).and(ProcessInstanceSelectors.byProcessId(processDefinitionKey)))` | Combines the parent and called-process definition selectors. |
| `calledProcessInstance(ProcessInstanceQuery query)` | No counterpart | Search child process instances with the client and apply query filters that have no CPT selector equivalent. |
| `assertThat(pi).task()` | No counterpart | Search user tasks by `pi.getProcessInstanceKey()` and `UserTaskState.CREATED`, then assert `.hasSize(1)` and inspect the result. A CPT selector checks the first match, and `byProcessInstanceKey` can include completed tasks. |
| `assertThat(pi).task("A")` | `assertThatUserTask(UserTaskSelectors.byElementId("A", pi.getProcessInstanceKey())).isCreated()` | `A` is the BPMN user-task element ID. The selector includes the process-instance key. If the element can repeat, filter the search to `CREATED` tasks and assert `.hasSize(1)`. |
| `assertThat(pi).task(TaskQuery query)` | No counterpart | C7 narrows the query to `pi`. Use a user-task search scoped by `pi.getProcessInstanceKey()` and filtered to `CREATED`, with equivalent query filters; assert `.hasSize(1)` to preserve C7's uniqueness behavior. |
| `assertThat(pi).job()` | No counterpart | CPT has no chained job assertion. Use `hasActiveElements("A")` when the BPMN element state is enough; query jobs scoped by `pi.getProcessInstanceKey()` and use AssertJ to check job data or uniqueness. `JobSelectors` are for actions such as completing a job, not assertions. |
| `assertThat(pi).job("A")` | No counterpart | C7 filters by activity ID and scopes the lookup to `pi`. Assert the element state or query jobs by process-instance key and element ID. |
| `assertThat(pi).job(JobQuery query)` | No counterpart | CPT has no `JobQuery` assertion. Query jobs scoped by `pi.getProcessInstanceKey()` and apply equivalent filters; assert `.hasSize(1)` when C7's uniqueness behavior matters. |
| `BpmnAwareTests.externalTask()`, `externalTask("A")`, `externalTask(ExternalTaskQuery query)` (also overloads with `ProcessInstance`) | No counterpart | These are `BpmnAwareTests` helpers, not `ProcessInstanceAssert` methods. C7 scopes them to the last asserted or explicitly supplied process instance. Camunda 8 models external tasks as jobs; query jobs by process-instance key and the converted element ID or job type, and assert `.hasSize(1)` when preserving the C7 single-result behavior. `JobSelectors` can select jobs for actions, not assertions. |
| `assertThat(task()).isAssignedTo("u")` | `assertThatUserTask(UserTaskSelectors.byElementId("A", pi.getProcessInstanceKey())).isCreated().hasAssignee("u")` | Include the process-instance key to preserve C7's scope. |
| `assertThat(task()).hasName("Approve")` | `assertThatUserTask(selector).isCreated().hasName("Approve")` | |
| `assertThat(task()).hasCandidateGroup("approvers")` | `assertThatUserTask(selector).isCreated().hasCandidateGroup("approvers")` | Camunda 7 also requires the task to be unassigned; search for the task and assert that its assignee is null with AssertJ. |
| `assertThat(task()).hasCandidateGroupAssociated("approvers")` | `assertThatUserTask(selector).isCreated().hasCandidateGroup("approvers")` | Both check the candidate-group association whether or not the task is assigned. |
| `assertThat(task()).hasDueDate(date)` | `assertThatUserTask(selector).isCreated().hasDueDate(isoDate)` | CPT also supports `hasFollowUpDate(...)`. |
| `assertThat(task()).isNotAssigned()` | No counterpart | Search for the created user task scoped to `pi.getProcessInstanceKey()` and assert that its assignee is null with AssertJ. |
| `assertThat(task()).hasCandidateUser("u")`, `hasCandidateUserAssociated("u")` | No counterpart | Search for the created user task scoped to `pi.getProcessInstanceKey()` and assert its candidate users with AssertJ. |
| `assertThat(task()).hasId(...)`, `hasDefinitionKey(...)`, `hasFormKey(...)`, `hasDescription(...)` | No counterpart | Search for the created user task scoped to `pi.getProcessInstanceKey()` and assert the required field with AssertJ. |
| `assertThat(job()).hasId(...)`, `hasDueDate(...)`, `hasRetries(...)`, `hasExceptionMessage()`, `hasDeploymentId(...)`, `hasActivityId(...)`, `hasProcessInstanceId(...)`, `hasExecutionId(...)` | No counterpart | Search the job with the client and assert its state or fields with AssertJ. |
| `assertThat(externalTask()).hasTopicName(...)`, `hasActivityId(...)` | No counterpart | Assert the element state, or complete the job and assert the process result. |
| `assertThat(processDefinition()).hasActiveInstances(n)` | No counterpart | Search process instances for the definition and assert the count with AssertJ. |
| CMMN assertions from `CmmnAwareTests` | No counterpart | Record the test as manual migration. CPT has no CMMN assertion API. |

Camunda 7's `calledProcessInstance` overloads use `singleResult()`. CPT selector assertions use the first matching instance instead. When uniqueness is required, search with the client and assert that exactly one child matches.

The assertion helper classes for jobs and external tasks do not have direct CPT counterparts. Assert the process state or query the relevant runtime records with the client.

`isNotWaitingAt` checks only the current activity tree. An element that was entered and then left passes.

`hasNoActiveElements` and `isNotWaitingForMessage` inspect the current state. If absence matters only after an asynchronous process reaches a later point, first use a positive waiting or terminal assertion that establishes that observation point; the negative check can pass before the process reaches it.

`hasNotActivatedElements` evaluates immediately, so first assert a process state that establishes the observation point. It fails for an element that was entered before. Do not use it to replace `isNotWaitingAt`.
