# Engine REST Test Mapping

Use these mappings for in-scope remote-engine tests. Deploy converted copies and pass plain JSON variables.

| Camunda 7 Engine REST call | CPT 8.9 replacement | Notes |
|---|---|---|
| `POST /deployment/create` | `@TestDeployment(resources = "converted-c8-<name>.bpmn")` or the application's `@Deployment` | Deploy the converted copy. |
| `POST /process-definition/key/{key}/start` | `client.newCreateInstanceCommand().bpmnProcessId(key).latestVersion().variables(vars).send().join()` | Pass plain JSON variables. |
| `POST /message` | `client.newCorrelateMessageCommand()` or `client.newPublishMessageCommand()` | Read the name and key from the converted copy's `zeebe:subscription`. |
| `POST /signal` | `client.newBroadcastSignalCommand().signalName(name).send().join()` | Keep the converted signal name. |
| `GET /task?processInstanceId=...` then `POST /task/{id}/complete` | `processTestContext.completeUserTask(elementId, vars)` or `client.newCompleteUserTaskCommand(userTaskKey).variables(vars).send().join()` | Pass completion variables. Use the C8 user-task key when the test calls the client directly. |
| `POST /task/{id}/claim` or `/task/{id}/assignee` | `client.newAssignUserTaskCommand(userTaskKey).assignee(user).send().join()` | Preserve the assignee. |
| `POST /external-task/fetchAndLock` then `POST /external-task/{id}/complete` | `processTestContext.completeJob(type, vars)` | Use `mockJobWorker(type).thenComplete(vars)` when the test replaces the worker boundary. |
| `POST /external-task/{id}/bpmnError` | `processTestContext.throwBpmnErrorFromJob(type, code, vars)` | Preserve the BPMN error code and variables. |
| `GET /history/process-instance/{id}` with state `COMPLETED` | `assertThat(processInstance).isCompleted()` | Use the CPT process-instance assertion. |
| `GET /history/activity-instance?processInstanceId=...` when checking completed activity IDs or order only | `hasCompletedElements(...)` or `hasCompletedElementsInOrder(...)` | These assertions cover completed elements only. Handle canceled or terminated elements separately. |
| Other `/history/activity-instance` queries, including `unfinished`, `canceled`, assignee, time, or count filters | `newElementInstanceSearchRequest()` with equivalent filters and AssertJ, or manual migration | Filter by process-instance key and preserve the requested state and filters. Mark the case manual when C8 cannot express them. |
| `GET /process-instance/{id}/variables` or `GET /history/variable-instance` | `hasVariable(name, value)` or `hasVariables(map)` | Compare plain JSON values. |
| `GET /incident?processInstanceId=...` | `hasActiveIncidents()` or `hasNoActiveIncidents()` | Assert the expected incident state. |
| `POST /job/{id}/execute` for a timer job | `processTestContext.increaseTime(duration)` | Assert the timer catch event is active first. Assert the attached activity for a boundary timer. CPT does not expose a boundary timer as an active element. |
| `POST /job/{id}/execute` for a non-timer job | No time-advancement mapping | Identify the job type and why the test executes it. Use the matching CPT worker command when the test controls a worker boundary. Assert the process path for an engine-managed continuation. Do not advance time. |
